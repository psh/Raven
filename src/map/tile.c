/**
 * Raven - map/tile.c
 * Mapbox Vector Tiles 2.1 (github.com/mapbox/vector-tile-spec): protobuf
 * layers of features, each with tags into the layer's keys and values, and a
 * stream of MoveTo, LineTo and ClosePath commands in tile coordinates (0 to
 * the layer's extent, often 4096). Protobuf is read by hand.
 */

#include "map.h"

#include <string.h>

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

int rvn_map_tile_decode(rvn_arena_t* arena, const uint8_t* bytes, size_t length, rvn_map_tile_data_t* out) {
	rvn_proto_t r, scan;
	uint32_t field, wire, layers = 0;
	memset(out, 0, sizeof(*out));
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
