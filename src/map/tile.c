/**
 * Raven - map/tile.c
 * Mapbox Vector Tiles 2.1 (github.com/mapbox/vector-tile-spec): protobuf
 * layers of features, each with tags into the layer's keys and values, and a
 * stream of MoveTo, LineTo and ClosePath commands in tile coordinates (0 to
 * the layer's extent, often 4096). Protobuf is read by hand.
 *
 * Tiles are often served or stored compressed, gzip (RFC 1952) or zlib
 * (RFC 1950), and are inflated here with stb_image's inflate, their
 * checksums verified (gzip's CRC-32 and size, zlib's Adler-32). A tile not
 * compressed starts with a layer, field 3 (0x1A), so neither header can be
 * mistaken for one.
 */

#include "map.h"
#include "../render/renderer.h"

#include <string.h>

#if defined(__ARM_FEATURE_CRC32)
#include <arm_acle.h>
#endif

typedef struct rvn_proto_t {
	const uint8_t* p;
	const uint8_t* end;
	int ok;
} rvn_proto_t;

static uint64_t rvn_proto_varint(rvn_proto_t* r) {
	uint64_t result = 0;
	int shift = 0;
	while (r->p < r->end && shift < 64) {
		uint8_t b = *r->p++;
		result |= (uint64_t)(b & 0x7F) << shift;
		if (b < 0x80) return result;
		shift += 7;
	}
	r->ok = R_FALSE;
	return 0;
}

static int rvn_proto_tag(rvn_proto_t* r, uint32_t* field, uint32_t* wire) {
	uint64_t key;
	if (r->p >= r->end) return R_FALSE;
	key = rvn_proto_varint(r);
	*field = (uint32_t)(key >> 3);
	*wire = (uint32_t)(key & 7);
	return r->ok;
}

static uint64_t rvn_proto_fixed(rvn_proto_t* r, int bytes) {
	uint64_t v = 0;
	int i;
	if (r->end - r->p < bytes) {
		r->ok = R_FALSE;
		return 0;
	}
	for (i = 0; i < bytes; i++) v |= (uint64_t)r->p[i] << (8 * i);
	r->p += bytes;
	return v;
}

static rvn_proto_t rvn_proto_message(rvn_proto_t* r) {
	rvn_proto_t inner;
	uint64_t length = rvn_proto_varint(r);
	inner.ok = r->ok;
	if (!r->ok || length > (uint64_t)(r->end - r->p)) {
		r->ok = inner.ok = R_FALSE;
		inner.p = inner.end = r->p;
		return inner;
	}
	inner.p = r->p;
	inner.end = r->p + length;
	r->p += length;
	return inner;
}

static const char* rvn_proto_string(rvn_proto_t* r, rvn_arena_t* arena, uint32_t* length) {
	rvn_proto_t s = rvn_proto_message(r);
	if (!s.ok) return NULL;
	if (length) *length = (uint32_t)(s.end - s.p);
	return rvn_arena_strndup(arena, (const char*)s.p, (size_t)(s.end - s.p));
}

/** A packed repeated varint field, as 32-bit values. */
static uint32_t* rvn_proto_packed(rvn_proto_t* r, rvn_arena_t* arena, uint32_t* count) {
	rvn_proto_t inner = rvn_proto_message(r);
	const uint8_t* p;
	uint32_t n = 0, i = 0;
	uint32_t* out;
	*count = 0;
	if (!inner.ok) return NULL;
	/* one value ends at each byte under 0x80 */
	for (p = inner.p; p < inner.end; p++) n += *p < 0x80;
	out = (uint32_t*)rvn_arena_alloc(arena, sizeof(uint32_t) * (n ? n : 1));
	if (!out) {
		r->ok = R_FALSE;
		return NULL;
	}
	while (inner.p < inner.end && i < n) out[i++] = (uint32_t)rvn_proto_varint(&inner);
	if (!inner.ok) r->ok = R_FALSE;
	*count = i;
	return out;
}

static void rvn_proto_skip(rvn_proto_t* r, uint32_t wire) {
	switch (wire) {
		case 0: rvn_proto_varint(r); break;
		case 1: rvn_proto_fixed(r, 8); break;
		case 2: rvn_proto_message(r); break;
		case 5: rvn_proto_fixed(r, 4); break;
		default: r->ok = R_FALSE; break;
	}
}

static int64_t rvn_zigzag64(uint64_t n) {
	return (int64_t)(n >> 1) ^ -(int64_t)(n & 1);
}

static int32_t rvn_zigzag32(uint32_t n) {
	return (int32_t)(n >> 1) ^ -(int32_t)(n & 1);
}

static void rvn_tile_value(rvn_proto_t r, rvn_arena_t* arena, rvn_map_value_t* value) {
	uint32_t field, wire;
	memset(value, 0, sizeof(*value));
	while (rvn_proto_tag(&r, &field, &wire)) {
		switch (field) {
			case 1:
				value->kind = RVN_MV_STRING;
				value->as.string = rvn_proto_string(&r, arena, &value->count);
				if (!value->as.string) value->kind = RVN_MV_NULL;
				break;
			case 2: {
				uint32_t bits = (uint32_t)rvn_proto_fixed(&r, 4);
				float f;
				memcpy(&f, &bits, 4);
				value->kind = RVN_MV_NUMBER;
				value->as.number = (double)f;
				break;
			}
			case 3: {
				uint64_t bits = rvn_proto_fixed(&r, 8);
				memcpy(&value->as.number, &bits, 8);
				value->kind = RVN_MV_NUMBER;
				break;
			}
			case 4:
				value->kind = RVN_MV_NUMBER;
				value->as.number = (double)(int64_t)rvn_proto_varint(&r);
				break;
			case 5:
				value->kind = RVN_MV_NUMBER;
				value->as.number = (double)rvn_proto_varint(&r);
				break;
			case 6:
				value->kind = RVN_MV_NUMBER;
				value->as.number = (double)rvn_zigzag64(rvn_proto_varint(&r));
				break;
			case 7:
				value->kind = RVN_MV_BOOL;
				value->boolean = rvn_proto_varint(&r) != 0;
				break;
			default:
				rvn_proto_skip(&r, wire);
				break;
		}
		if (!r.ok) break;
	}
}

static int rvn_tile_feature(rvn_proto_t r, rvn_arena_t* arena, rvn_map_feature_t* feature) {
	uint32_t field, wire;
	memset(feature, 0, sizeof(*feature));
	while (rvn_proto_tag(&r, &field, &wire)) {
		switch (field) {
			case 2: feature->tags = rvn_proto_packed(&r, arena, &feature->tag_count); break;
			case 3: {
				uint64_t type = rvn_proto_varint(&r);
				feature->type = type <= RVN_MAP_POLYGON ? (rvn_map_geometry_t)type : RVN_MAP_UNKNOWN;
				break;
			}
			case 4: feature->commands = rvn_proto_packed(&r, arena, &feature->command_count); break;
			default: rvn_proto_skip(&r, wire); break;
		}
		if (!r.ok) return R_FALSE;
	}
	return r.ok;
}

static int rvn_tile_layer(rvn_proto_t r, rvn_arena_t* arena, rvn_map_layer_t* layer) {
	/* first pass: count, so that each array is allocated once */
	rvn_proto_t scan = r;
	uint32_t field, wire, keys = 0, values = 0, features = 0;
	memset(layer, 0, sizeof(*layer));
	layer->extent = 4096;
	layer->name = "";
	while (rvn_proto_tag(&scan, &field, &wire)) {
		if (field == 2) features++;
		else if (field == 3) keys++;
		else if (field == 4) values++;
		rvn_proto_skip(&scan, wire);
		if (!scan.ok) return R_FALSE;
	}
	layer->keys = (const char**)rvn_arena_alloc(arena, sizeof(char*) * (keys ? keys : 1));
	layer->values = (rvn_map_value_t*)rvn_arena_alloc(arena, sizeof(rvn_map_value_t) * (values ? values : 1));
	layer->features = (rvn_map_feature_t*)rvn_arena_alloc(arena, sizeof(rvn_map_feature_t) * (features ? features : 1));
	if (!layer->keys || !layer->values || !layer->features) return R_FALSE;
	while (rvn_proto_tag(&r, &field, &wire)) {
		switch (field) {
			case 1: {
				const char* name = rvn_proto_string(&r, arena, NULL);
				if (name) layer->name = name;
				break;
			}
			case 2: {
				rvn_proto_t inner = rvn_proto_message(&r);
				if (inner.ok && rvn_tile_feature(inner, arena, &layer->features[layer->feature_count])) layer->feature_count++;
				break;
			}
			case 3: {
				const char* key = rvn_proto_string(&r, arena, NULL);
				layer->keys[layer->key_count++] = key ? key : "";
				break;
			}
			case 4: {
				rvn_proto_t inner = rvn_proto_message(&r);
				rvn_tile_value(inner, arena, &layer->values[layer->value_count++]);
				break;
			}
			case 5: layer->extent = (uint32_t)rvn_proto_varint(&r); break;
			default: rvn_proto_skip(&r, wire); break;
		}
		if (!r.ok) return R_FALSE;
	}
	if (layer->extent == 0) layer->extent = 4096;
	/* tags pointing past the keys or values are dropped: they would read out of bounds */
	{
		uint32_t i, j;
		for (i = 0; i < layer->feature_count; i++) {
			rvn_map_feature_t* f = &layer->features[i];
			for (j = 0; j + 1 < f->tag_count; j += 2) {
				if (f->tags[j] >= layer->key_count || f->tags[j + 1] >= layer->value_count) {
					f->tag_count = j;
					break;
				}
			}
			f->tag_count &= ~1u;
		}
	}
	return R_TRUE;
}

static int rvn_tile_decode(rvn_arena_t* arena, const uint8_t* bytes, size_t length, rvn_map_tile_data_t* out) {
	rvn_proto_t r, scan;
	uint32_t field, wire, layers = 0;
	r.p = bytes;
	r.end = bytes + length;
	r.ok = R_TRUE;
	scan = r;
	while (rvn_proto_tag(&scan, &field, &wire)) {
		if (field == 3 && wire == 2) layers++;
		rvn_proto_skip(&scan, wire);
		if (!scan.ok) return R_FALSE;
	}
	out->layers = (rvn_map_layer_t*)rvn_arena_alloc(arena, sizeof(rvn_map_layer_t) * (layers ? layers : 1));
	if (!out->layers) return R_FALSE;
	while (rvn_proto_tag(&r, &field, &wire)) {
		if (field == 3 && wire == 2) {
			rvn_proto_t inner = rvn_proto_message(&r);
			if (!inner.ok || !rvn_tile_layer(inner, arena, &out->layers[out->layer_count])) return R_FALSE;
			out->layer_count++;
		} else {
			rvn_proto_skip(&r, wire);
		}
		if (!r.ok) return R_FALSE;
	}
	return R_TRUE;
}

static uint32_t rvn_le32(const uint8_t* p) {
	return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static uint32_t rvn_be32(const uint8_t* p) {
	return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | (uint32_t)p[3];
}

/** The DEFLATE stream of a gzip member after its header, and its size inflated; R_FALSE if the header is malformed. */
static int rvn_gzip_body(const uint8_t* bytes, size_t length, size_t* start, size_t* inflated) {
	size_t at = 10;
	uint8_t flags;
	if (length < 18 || bytes[2] != 8) return R_FALSE; /* the header, the trailer, and DEFLATE */
	flags = bytes[3];
	if (flags & 4) { /* FEXTRA */
		if (at + 2 > length) return R_FALSE;
		at += 2 + (size_t)(bytes[at] | (bytes[at + 1] << 8));
	}
	if (flags & 8) { /* FNAME */
		while (at < length && bytes[at]) at++;
		at++;
	}
	if (flags & 16) { /* FCOMMENT */
		while (at < length && bytes[at]) at++;
		at++;
	}
	if (flags & 2) at += 2; /* FHCRC */
	if (at + 8 > length) return R_FALSE;
	*start = at;
	*inflated = rvn_le32(bytes + length - 4);
	return R_TRUE;
}

#if defined(__ARM_FEATURE_CRC32)

/** CRC-32 (ISO-HDLC, gzip's) with ARMv8's CRC32 instructions, eight bytes at a time. */
static uint32_t rvn_crc32(const uint8_t* p, size_t length) {
	uint32_t crc = 0xFFFFFFFFu;
	for (; length >= 8; p += 8, length -= 8) {
		uint64_t word;
		memcpy(&word, p, 8);
		crc = __crc32d(crc, word);
	}
	for (; length; p++, length--) crc = __crc32b(crc, *p);
	return crc ^ 0xFFFFFFFFu;
}

#else

/** CRC-32 (ISO-HDLC, gzip's), a byte at a time. */
static uint32_t rvn_crc32(const uint8_t* p, size_t length) {
	static const uint32_t table[256] = {
		0x00000000, 0x77073096, 0xEE0E612C, 0x990951BA, 0x076DC419, 0x706AF48F, 0xE963A535, 0x9E6495A3,
		0x0EDB8832, 0x79DCB8A4, 0xE0D5E91E, 0x97D2D988, 0x09B64C2B, 0x7EB17CBD, 0xE7B82D07, 0x90BF1D91,
		0x1DB71064, 0x6AB020F2, 0xF3B97148, 0x84BE41DE, 0x1ADAD47D, 0x6DDDE4EB, 0xF4D4B551, 0x83D385C7,
		0x136C9856, 0x646BA8C0, 0xFD62F97A, 0x8A65C9EC, 0x14015C4F, 0x63066CD9, 0xFA0F3D63, 0x8D080DF5,
		0x3B6E20C8, 0x4C69105E, 0xD56041E4, 0xA2677172, 0x3C03E4D1, 0x4B04D447, 0xD20D85FD, 0xA50AB56B,
		0x35B5A8FA, 0x42B2986C, 0xDBBBC9D6, 0xACBCF940, 0x32D86CE3, 0x45DF5C75, 0xDCD60DCF, 0xABD13D59,
		0x26D930AC, 0x51DE003A, 0xC8D75180, 0xBFD06116, 0x21B4F4B5, 0x56B3C423, 0xCFBA9599, 0xB8BDA50F,
		0x2802B89E, 0x5F058808, 0xC60CD9B2, 0xB10BE924, 0x2F6F7C87, 0x58684C11, 0xC1611DAB, 0xB6662D3D,
		0x76DC4190, 0x01DB7106, 0x98D220BC, 0xEFD5102A, 0x71B18589, 0x06B6B51F, 0x9FBFE4A5, 0xE8B8D433,
		0x7807C9A2, 0x0F00F934, 0x9609A88E, 0xE10E9818, 0x7F6A0DBB, 0x086D3D2D, 0x91646C97, 0xE6635C01,
		0x6B6B51F4, 0x1C6C6162, 0x856530D8, 0xF262004E, 0x6C0695ED, 0x1B01A57B, 0x8208F4C1, 0xF50FC457,
		0x65B0D9C6, 0x12B7E950, 0x8BBEB8EA, 0xFCB9887C, 0x62DD1DDF, 0x15DA2D49, 0x8CD37CF3, 0xFBD44C65,
		0x4DB26158, 0x3AB551CE, 0xA3BC0074, 0xD4BB30E2, 0x4ADFA541, 0x3DD895D7, 0xA4D1C46D, 0xD3D6F4FB,
		0x4369E96A, 0x346ED9FC, 0xAD678846, 0xDA60B8D0, 0x44042D73, 0x33031DE5, 0xAA0A4C5F, 0xDD0D7CC9,
		0x5005713C, 0x270241AA, 0xBE0B1010, 0xC90C2086, 0x5768B525, 0x206F85B3, 0xB966D409, 0xCE61E49F,
		0x5EDEF90E, 0x29D9C998, 0xB0D09822, 0xC7D7A8B4, 0x59B33D17, 0x2EB40D81, 0xB7BD5C3B, 0xC0BA6CAD,
		0xEDB88320, 0x9ABFB3B6, 0x03B6E20C, 0x74B1D29A, 0xEAD54739, 0x9DD277AF, 0x04DB2615, 0x73DC1683,
		0xE3630B12, 0x94643B84, 0x0D6D6A3E, 0x7A6A5AA8, 0xE40ECF0B, 0x9309FF9D, 0x0A00AE27, 0x7D079EB1,
		0xF00F9344, 0x8708A3D2, 0x1E01F268, 0x6906C2FE, 0xF762575D, 0x806567CB, 0x196C3671, 0x6E6B06E7,
		0xFED41B76, 0x89D32BE0, 0x10DA7A5A, 0x67DD4ACC, 0xF9B9DF6F, 0x8EBEEFF9, 0x17B7BE43, 0x60B08ED5,
		0xD6D6A3E8, 0xA1D1937E, 0x38D8C2C4, 0x4FDFF252, 0xD1BB67F1, 0xA6BC5767, 0x3FB506DD, 0x48B2364B,
		0xD80D2BDA, 0xAF0A1B4C, 0x36034AF6, 0x41047A60, 0xDF60EFC3, 0xA867DF55, 0x316E8EEF, 0x4669BE79,
		0xCB61B38C, 0xBC66831A, 0x256FD2A0, 0x5268E236, 0xCC0C7795, 0xBB0B4703, 0x220216B9, 0x5505262F,
		0xC5BA3BBE, 0xB2BD0B28, 0x2BB45A92, 0x5CB36A04, 0xC2D7FFA7, 0xB5D0CF31, 0x2CD99E8B, 0x5BDEAE1D,
		0x9B64C2B0, 0xEC63F226, 0x756AA39C, 0x026D930A, 0x9C0906A9, 0xEB0E363F, 0x72076785, 0x05005713,
		0x95BF4A82, 0xE2B87A14, 0x7BB12BAE, 0x0CB61B38, 0x92D28E9B, 0xE5D5BE0D, 0x7CDCEFB7, 0x0BDBDF21,
		0x86D3D2D4, 0xF1D4E242, 0x68DDB3F8, 0x1FDA836E, 0x81BE16CD, 0xF6B9265B, 0x6FB077E1, 0x18B74777,
		0x88085AE6, 0xFF0F6A70, 0x66063BCA, 0x11010B5C, 0x8F659EFF, 0xF862AE69, 0x616BFFD3, 0x166CCF45,
		0xA00AE278, 0xD70DD2EE, 0x4E048354, 0x3903B3C2, 0xA7672661, 0xD06016F7, 0x4969474D, 0x3E6E77DB,
		0xAED16A4A, 0xD9D65ADC, 0x40DF0B66, 0x37D83BF0, 0xA9BCAE53, 0xDEBB9EC5, 0x47B2CF7F, 0x30B5FFE9,
		0xBDBDF21C, 0xCABAC28A, 0x53B39330, 0x24B4A3A6, 0xBAD03605, 0xCDD70693, 0x54DE5729, 0x23D967BF,
		0xB3667A2E, 0xC4614AB8, 0x5D681B02, 0x2A6F2B94, 0xB40BBE37, 0xC30C8EA1, 0x5A05DF1B, 0x2D02EF8D
	};
	uint32_t crc = 0xFFFFFFFFu;
	size_t i;
	for (i = 0; i < length; i++) crc = (crc >> 8) ^ table[(crc ^ p[i]) & 0xFF];
	return crc ^ 0xFFFFFFFFu;
}

#endif

/** Adler-32 (zlib's), taking the modulo every 5552 bytes, the most that can't overflow. */
static uint32_t rvn_adler32(const uint8_t* p, size_t length) {
	uint32_t a = 1, b = 0;
	while (length) {
		size_t n = length < 5552 ? length : 5552;
		length -= n;
		while (n--) {
			a += *p++;
			b += a;
		}
		a %= 65521;
		b %= 65521;
	}
	return (b << 16) | a;
}

int rvn_map_tile_decode(rvn_arena_t* arena, const uint8_t* bytes, size_t length, rvn_map_tile_data_t* out) {
	uint8_t* inflated = NULL;
	size_t size = 0;
	int ok;
	memset(out, 0, sizeof(*out));
	if (length >= 2 && bytes[0] == 0x1F && bytes[1] == 0x8B) {
		size_t start, expected;
		if (!rvn_gzip_body(bytes, length, &start, &expected)) return R_FALSE;
		inflated = rvn_inflate(bytes + start, length - start - 8, R_FALSE, expected, &size);
		/* the trailer: the CRC-32 of the tile, then its size modulo 2^32 */
		if (inflated && ((uint32_t)size != (uint32_t)expected || rvn_crc32(inflated, size) != rvn_le32(bytes + length - 8))) {
			rvn_free(inflated);
			inflated = NULL;
		}
		if (!inflated) return R_FALSE;
	} else if (length >= 2 && (bytes[0] & 0x0F) == 8 && ((bytes[0] << 8) | bytes[1]) % 31 == 0) {
		inflated = rvn_inflate(bytes, length, R_TRUE, length * 4, &size);
		/* the stream ends with the Adler-32 of the tile, which stb_image's inflate doesn't check */
		if (inflated && (length < 6 || rvn_adler32(inflated, size) != rvn_be32(bytes + length - 4))) {
			rvn_free(inflated);
			inflated = NULL;
		}
		if (!inflated) return R_FALSE;
	}
	if (!inflated) return rvn_tile_decode(arena, bytes, length, out);
	ok = rvn_tile_decode(arena, inflated, size, out);
	rvn_free(inflated);
	return ok;
}

const rvn_map_layer_t* rvn_map_tile_layer(const rvn_map_tile_data_t* tile, const char* name) {
	uint32_t i;
	if (!name) return NULL;
	for (i = 0; i < tile->layer_count; i++) {
		if (strcmp(tile->layers[i].name, name) == 0) return &tile->layers[i];
	}
	return NULL;
}

static void rvn_rings_start(rvn_map_rings_t* rings) {
	RVN_ARRAY_RESERVE(rings->starts, rings->start_cap, rings->ring_count + 2);
	rings->starts[rings->ring_count++] = rings->point_count;
}

int rvn_map_feature_rings(const rvn_map_feature_t* feature, rvn_map_rings_t* rings) {
	int32_t x = 0, y = 0;
	uint32_t i = 0;
	rings->point_count = 0;
	rings->ring_count = 0;
	while (i < feature->command_count) {
		uint32_t command = feature->commands[i] & 7, count = feature->commands[i] >> 3, k;
		i++;
		if (command == 1 || command == 2) {
			if (count > (feature->command_count - i) / 2) return R_FALSE;
			RVN_ARRAY_RESERVE(rings->xy, rings->point_cap, (rings->point_count + count) * 2);
			for (k = 0; k < count; k++) {
				/* the first point starts a ring, and so does a MoveTo after the ring has points */
				if (rings->ring_count == 0 || (command == 1 && rings->starts[rings->ring_count - 1] < rings->point_count)) {
					rvn_rings_start(rings);
				}
				x += rvn_zigzag32(feature->commands[i]);
				y += rvn_zigzag32(feature->commands[i + 1]);
				i += 2;
				rings->xy[rings->point_count * 2] = (float)x;
				rings->xy[rings->point_count * 2 + 1] = (float)y;
				rings->point_count++;
			}
		} else if (command != 7) {
			return R_FALSE;
		}
	}
	/* the end of the last ring */
	RVN_ARRAY_RESERVE(rings->starts, rings->start_cap, rings->ring_count + 1);
	rings->starts[rings->ring_count] = rings->point_count;
	return R_TRUE;
}

void rvn_map_rings_free(rvn_map_rings_t* rings) {
	rvn_free(rings->xy);
	rvn_free(rings->starts);
	memset(rings, 0, sizeof(*rings));
}

double rvn_map_ring_area(const float* xy, uint32_t point_count) {
	double sum = 0.0;
	uint32_t i, j;
	if (point_count == 0) return 0.0;
	j = point_count - 1;
	for (i = 0; i < point_count; i++) {
		sum += (double)xy[j * 2] * xy[i * 2 + 1] - (double)xy[i * 2] * xy[j * 2 + 1];
		j = i;
	}
	return sum;
}
