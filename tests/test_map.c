/**
 * Raven tests - the Map widget: its parts (JSON, tiles, earcut, the style's
 * expressions and colours, dashes and offsets) against the same cases as the
 * Kotlin spike it was ported from (compose/spikes/maps, MapsTest.kt), then
 * the widget itself, headless, on tiles written here by hand.
 *
 * The parts are internal (src/map/map.h): this test links Raven statically.
 */

#include "test.h"
#include "map/map.h"
#include "render/renderer.h"

#include <stdlib.h>

/* ---------------------------------------------------------------------------
 *  A protobuf writer, just enough to build a tile by hand
 * ------------------------------------------------------------------------- */

typedef struct buf_t {
	uint8_t data[4096];
	size_t length;
} buf_t;

static void put_byte(buf_t* b, uint8_t v) {
	if (b->length < sizeof(b->data)) b->data[b->length++] = v;
}

static void put_varint(buf_t* b, uint64_t v) {
	do {
		uint8_t byte = (uint8_t)(v & 0x7F);
		v >>= 7;
		if (v) byte |= 0x80;
		put_byte(b, byte);
	} while (v);
}

static void put_bytes(buf_t* b, const void* data, size_t length) {
	size_t i;
	for (i = 0; i < length; i++) put_byte(b, ((const uint8_t*)data)[i]);
}

static void put_message(buf_t* b, int field, const buf_t* body) {
	put_varint(b, (uint64_t)((field << 3) | 2));
	put_varint(b, body->length);
	put_bytes(b, body->data, body->length);
}

static void put_string(buf_t* b, int field, const char* text) {
	put_varint(b, (uint64_t)((field << 3) | 2));
	put_varint(b, strlen(text));
	put_bytes(b, text, strlen(text));
}

static void put_field(buf_t* b, int field, uint32_t value) {
	put_varint(b, (uint64_t)(field << 3));
	put_varint(b, value);
}

static void put_packed(buf_t* b, int field, const uint32_t* values, int count) {
	buf_t inner;
	int i;
	inner.length = 0;
	for (i = 0; i < count; i++) put_varint(&inner, values[i]);
	put_message(b, field, &inner);
}

static uint32_t command(int id, int count) {
	return (uint32_t)((id & 7) | (count << 3));
}

static uint32_t zz(int n) {
	return (uint32_t)((n << 1) ^ (n >> 31));
}

/*
 * A tile of two layers: "water", a polygon tagged class=lake, a square from
 * (2, 2) to (6, 6) in an extent of 16 (an eighth of the tile up to three
 * eighths); and "road", a line tagged class=minor across the tile.
 */
static size_t water_tile(uint8_t* out, size_t cap) {
	const uint32_t square[] = { command(1, 1), zz(2), zz(2), command(2, 3), zz(4), zz(0), zz(0), zz(4), zz(-4), zz(0), command(7, 1) };
	const uint32_t line[] = { command(1, 1), zz(0), zz(8), command(2, 1), zz(16), zz(0) };
	const uint32_t tags[] = { 0, 0 };
	buf_t feature, value, layer, tile;
	tile.length = 0;

	feature.length = 0;
	put_packed(&feature, 2, tags, 2);
	put_field(&feature, 3, 3);
	put_packed(&feature, 4, square, (int)(sizeof(square) / sizeof(square[0])));
	value.length = 0;
	put_string(&value, 1, "lake");
	layer.length = 0;
	put_string(&layer, 1, "water");
	put_message(&layer, 2, &feature);
	put_string(&layer, 3, "class");
	put_message(&layer, 4, &value);
	put_field(&layer, 5, 16);
	put_field(&layer, 15, 2);
	put_message(&tile, 3, &layer);

	feature.length = 0;
	put_packed(&feature, 2, tags, 2);
	put_field(&feature, 3, 2);
	put_packed(&feature, 4, line, (int)(sizeof(line) / sizeof(line[0])));
	value.length = 0;
	put_string(&value, 1, "minor");
	layer.length = 0;
	put_string(&layer, 1, "road");
	put_message(&layer, 2, &feature);
	put_string(&layer, 3, "class");
	put_message(&layer, 4, &value);
	put_field(&layer, 5, 16);
	put_field(&layer, 15, 2);
	put_message(&tile, 3, &layer);

	if (tile.length > cap) return 0;
	memcpy(out, tile.data, tile.length);
	return tile.length;
}

static const char* g_style =
	"{\"version\": 8, \"sources\": {\"openmaptiles\": {\"type\": \"vector\"}},"
	" \"layers\": ["
	"  {\"id\": \"background\", \"type\": \"background\", \"paint\": {\"background-color\": \"#f8f4f0\"}},"
	"  {\"id\": \"water\", \"type\": \"fill\", \"source\": \"openmaptiles\", \"source-layer\": \"water\","
	"   \"filter\": [\"==\", [\"get\", \"class\"], \"lake\"], \"paint\": {\"fill-color\": \"rgb(158,189,255)\"}},"
	"  {\"id\": \"river\", \"type\": \"fill\", \"source\": \"openmaptiles\", \"source-layer\": \"water\","
	"   \"filter\": [\"==\", [\"get\", \"class\"], \"river\"], \"paint\": {\"fill-color\": \"#00f\"}},"
	"  {\"id\": \"road\", \"type\": \"line\", \"source\": \"openmaptiles\", \"source-layer\": \"road\","
	"   \"paint\": {\"line-color\": \"#fff\", \"line-width\": [\"interpolate\", [\"exponential\", 1.2], [\"zoom\"], 13.5, 0, 20, 18]}},"
	"  {\"id\": \"hidden\", \"type\": \"line\", \"source\": \"openmaptiles\", \"source-layer\": \"road\", \"layout\": {\"visibility\": \"none\"}},"
	"  {\"id\": \"other\", \"type\": \"line\", \"source\": \"elsewhere\", \"source-layer\": \"road\"}"
	" ]}";

/* ---------------------------------------------------------------------------
 *  The parts
 * ------------------------------------------------------------------------- */

static void test_json(void) {
	rvn_arena_t arena;
	rvn_map_value_t root;
	const rvn_map_value_t* a;
	const char* text = "{\"a\": [1, -2.5e1, \"x\\\"y\", true, null], \"b\": {\"c\": \"\\u00e9\"}}";
	rvn_arena_init(&arena, 4096);
	CHECK(rvn_map_json_parse(&arena, text, strlen(text), &root));
	a = rvn_map_member(&root, "a");
	CHECK(a && a->kind == RVN_MV_ARRAY && a->count == 5);
	if (a && a->count == 5) {
		CHECK(a->as.items[0].as.number == 1.0);
		CHECK(a->as.items[1].as.number == -25.0);
		CHECK_STR(a->as.items[2].as.string, "x\"y");
		CHECK(a->as.items[3].kind == RVN_MV_BOOL && a->as.items[3].boolean);
		CHECK(a->as.items[4].kind == RVN_MV_NULL);
	}
	CHECK_STR(rvn_map_member_string(rvn_map_member(&root, "b"), "c"), "\xc3\xa9");
	/* not JSON, or JSON with something after it */
	CHECK(!rvn_map_json_parse(&arena, "{\"a\": }", 7, &root));
	CHECK(!rvn_map_json_parse(&arena, "[1] 2", 5, &root));
	rvn_arena_free(&arena);
}

static void test_tile_decoding(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	rvn_arena_t arena;
	rvn_map_tile_data_t tile;
	rvn_map_rings_t rings;
	const rvn_map_layer_t* water;
	const float expected[] = { 2, 2, 6, 2, 6, 6, 2, 6 };
	rvn_arena_init(&arena, 4096);
	memset(&rings, 0, sizeof(rings));
	CHECK(rvn_map_tile_decode(&arena, bytes, length, &tile));
	CHECK(tile.layer_count == 2);
	water = rvn_map_tile_layer(&tile, "water");
	CHECK(water != NULL);
	if (water) {
		const rvn_map_feature_t* f = &water->features[0];
		CHECK(water->extent == 16 && water->feature_count == 1);
		CHECK(f->type == RVN_MAP_POLYGON);
		CHECK(f->tag_count == 2);
		CHECK_STR(water->keys[f->tags[0]], "class");
		CHECK_STR(water->values[f->tags[1]].as.string, "lake");
		CHECK(rvn_map_feature_rings(f, &rings));
		CHECK(rings.ring_count == 1 && rings.point_count == 4);
		CHECK(rings.point_count == 4 && memcmp(rings.xy, expected, sizeof(expected)) == 0);
		/* an exterior ring has a positive area, y down */
		CHECK(rvn_map_ring_area(rings.xy, rings.point_count) > 0);
	}
	/* a truncated tile is refused, not read past its end */
	CHECK(!rvn_map_tile_decode(&arena, bytes, length - 3, &tile));
	rvn_map_rings_free(&rings);
	rvn_arena_free(&arena);
}

/*
 * The water tile compressed by Python: gzip with a file name in its header,
 * as gzip(1) writes it, and zlib.
 */
static const uint8_t g_water_gzip[] = {
	0x1F, 0x8B, 0x08, 0x08, 0x00, 0x00, 0x00, 0x00, 0x02, 0x03, 0x31, 0x34, 0x2D, 0x34, 0x32, 0x30,
	0x33, 0x2D, 0x36, 0x30, 0x38, 0x39, 0x2E, 0x70, 0x62, 0x66, 0x00, 0x93, 0xD2, 0xE7, 0x62, 0x2D,
	0x4F, 0x2C, 0x49, 0x2D, 0x12, 0x12, 0x16, 0x62, 0x62, 0x60, 0x90, 0x60, 0x56, 0xE2, 0xE6, 0x64,
	0x61, 0x91, 0xE2, 0x60, 0x60, 0xE0, 0x60, 0x67, 0xE0, 0x97, 0x62, 0x4D, 0xCE, 0x49, 0x2C, 0x2E,
	0x56, 0x62, 0xE3, 0x62, 0xC9, 0x49, 0xCC, 0x4E, 0xD5, 0x10, 0xA8, 0x60, 0x92, 0xD2, 0xE2, 0x62,
	0x29, 0xCA, 0x4F, 0x4C, 0x11, 0xE2, 0x03, 0xAB, 0x67, 0x52, 0x62, 0xE3, 0x64, 0x10, 0xE0, 0x52,
	0x60, 0x80, 0x29, 0x65, 0xE7, 0x62, 0xCD, 0xCD, 0xCC, 0xCB, 0x2F, 0x02, 0xA9, 0x05, 0x00, 0x15,
	0x81, 0xFB, 0x97, 0x5D, 0x00, 0x00, 0x00,
};

static const uint8_t g_water_zlib[] = {
	0x78, 0xDA, 0x93, 0xD2, 0xE7, 0x62, 0x2D, 0x4F, 0x2C, 0x49, 0x2D, 0x12, 0x12, 0x16, 0x62, 0x62,
	0x60, 0x90, 0x60, 0x56, 0xE2, 0xE6, 0x64, 0x61, 0x91, 0xE2, 0x60, 0x60, 0xE0, 0x60, 0x67, 0xE0,
	0x97, 0x62, 0x4D, 0xCE, 0x49, 0x2C, 0x2E, 0x56, 0x62, 0xE3, 0x62, 0xC9, 0x49, 0xCC, 0x4E, 0xD5,
	0x10, 0xA8, 0x60, 0x92, 0xD2, 0xE2, 0x62, 0x29, 0xCA, 0x4F, 0x4C, 0x11, 0xE2, 0x03, 0xAB, 0x67,
	0x52, 0x62, 0xE3, 0x64, 0x10, 0xE0, 0x52, 0x60, 0x80, 0x29, 0x65, 0xE7, 0x62, 0xCD, 0xCD, 0xCC,
	0xCB, 0x2F, 0x02, 0xA9, 0x05, 0x00, 0xB0, 0xEE, 0x0F, 0xFD,
};

/** The tile decodes as the water tile: its two layers and the square. */
static int is_water_tile(rvn_arena_t* arena, const uint8_t* bytes, size_t length) {
	rvn_map_tile_data_t tile;
	const rvn_map_layer_t* water;
	if (!rvn_map_tile_decode(arena, bytes, length, &tile) || tile.layer_count != 2) return 0;
	water = rvn_map_tile_layer(&tile, "water");
	return water && water->feature_count == 1 && water->features[0].type == RVN_MAP_POLYGON &&
		strcmp(water->values[water->features[0].tags[1]].as.string, "lake") == 0 && rvn_map_tile_layer(&tile, "road");
}

static void test_compressed_tiles(void) {
	uint8_t bytes[1024], broken[sizeof(g_water_gzip)];
	size_t length = water_tile(bytes, sizeof(bytes));
	rvn_arena_t arena;
	rvn_map_tile_data_t tile;
	rvn_arena_init(&arena, 4096);
	CHECK(is_water_tile(&arena, g_water_gzip, sizeof(g_water_gzip)));
	CHECK(is_water_tile(&arena, g_water_zlib, sizeof(g_water_zlib)));
	CHECK(is_water_tile(&arena, bytes, length));
	/* cut short, the stream doesn't inflate, or the trailer is lost */
	CHECK(!rvn_map_tile_decode(&arena, g_water_gzip, sizeof(g_water_gzip) - 12, &tile));
	CHECK(!rvn_map_tile_decode(&arena, g_water_gzip, sizeof(g_water_gzip) - 3, &tile));
	CHECK(!rvn_map_tile_decode(&arena, g_water_zlib, sizeof(g_water_zlib) / 2, &tile));
	CHECK(!rvn_map_tile_decode(&arena, g_water_gzip, 12, &tile));
	/* a size in the trailer that isn't the tile's */
	memcpy(broken, g_water_gzip, sizeof(broken));
	broken[sizeof(broken) - 4]++;
	CHECK(!rvn_map_tile_decode(&arena, broken, sizeof(broken), &tile));
	/* a checksum that isn't the tile's: gzip's CRC-32, zlib's Adler-32 */
	memcpy(broken, g_water_gzip, sizeof(broken));
	broken[sizeof(broken) - 8] ^= 1;
	CHECK(!rvn_map_tile_decode(&arena, broken, sizeof(broken), &tile));
	memcpy(broken, g_water_zlib, sizeof(g_water_zlib));
	broken[sizeof(g_water_zlib) - 1] ^= 1;
	CHECK(!rvn_map_tile_decode(&arena, broken, sizeof(g_water_zlib), &tile));
	/*
	 * One bit flipped in the stream turns "lake" into "like": it inflates, to
	 * a tile that decodes as well as the first. Only the checksum catches it.
	 */
	memcpy(broken, g_water_zlib, sizeof(g_water_zlib));
	broken[sizeof(g_water_zlib) - 46] ^= 0x80;
	{
		size_t size = 0;
		uint8_t* inflated = rvn_inflate(broken, sizeof(g_water_zlib), R_TRUE, 0, &size);
		const rvn_map_layer_t* water;
		CHECK(inflated && rvn_map_tile_decode(&arena, inflated, size, &tile));
		water = inflated ? rvn_map_tile_layer(&tile, "water") : NULL;
		CHECK(water && strcmp(water->values[0].as.string, "like") == 0);
		rvn_free(inflated);
	}
	CHECK(!rvn_map_tile_decode(&arena, broken, sizeof(g_water_zlib), &tile));
	/* a method that isn't DEFLATE */
	memcpy(broken, g_water_gzip, sizeof(broken));
	broken[2] = 7;
	CHECK(!rvn_map_tile_decode(&arena, broken, sizeof(broken), &tile));
	/* the file name never ends */
	memset(broken + 10, 'a', sizeof(broken) - 10);
	broken[2] = 8;
	CHECK(!rvn_map_tile_decode(&arena, broken, sizeof(broken), &tile));
	rvn_arena_free(&arena);
}

static double triangles_area(const float* xy, const uint32_t* t, uint32_t count) {
	double sum = 0.0;
	uint32_t i;
	for (i = 0; i + 2 < count; i += 3) {
		const float* a = xy + t[i] * 2;
		const float* b = xy + t[i + 1] * 2;
		const float* c = xy + t[i + 2] * 2;
		sum += fabs((double)(b[0] - a[0]) * (c[1] - a[1]) - (double)(c[0] - a[0]) * (b[1] - a[1])) / 2.0;
	}
	return sum;
}

static void test_earcut(void) {
	/* a 10 by 10 square with a 4 by 4 hole: 84 square units, in triangles that cover it once */
	const float square[] = { 0, 0, 10, 0, 10, 10, 0, 10, 3, 3, 3, 7, 7, 7, 7, 3 };
	const uint32_t hole[] = { 4 };
	/* a concave L: 3 by 3 less a 2 by 2 corner */
	const float l[] = { 0, 0, 3, 0, 3, 1, 1, 1, 1, 3, 0, 3 };
	rvn_map_earcut_t e;
	memset(&e, 0, sizeof(e));
	rvn_map_earcut(&e, square, 8, hole, 1);
	CHECK(fabs(triangles_area(square, e.indices, e.index_count) - 84.0) < 1e-6);
	rvn_map_earcut(&e, l, 6, NULL, 0);
	CHECK(fabs(triangles_area(l, e.indices, e.index_count) - 5.0) < 1e-6);
	CHECK(e.index_count == 12);
	rvn_map_earcut_free(&e);
}

static int parse(rvn_arena_t* arena, const char* json, rvn_map_value_t* out) {
	return rvn_map_json_parse(arena, json, strlen(json), out);
}

static void hex(const float c[4], char out[10]) {
	snprintf(out, 10, "#%02x%02x%02x%02x", (int)lroundf(c[0] * 255), (int)lroundf(c[1] * 255), (int)lroundf(c[2] * 255), (int)lroundf(c[3] * 255));
}

static void test_expressions(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	rvn_arena_t arena;
	rvn_map_tile_data_t tile;
	rvn_map_context_t c;
	rvn_map_value_t e, v;
	float color[4];
	char text[10];
	rvn_arena_init(&arena, 4096);
	CHECK(rvn_map_tile_decode(&arena, bytes, length, &tile));
	memset(&c, 0, sizeof(c));
	c.zoom = 14.0;
	c.layer = rvn_map_tile_layer(&tile, "road");
	c.feature = c.layer ? &c.layer->features[0] : NULL;
	c.scratch = &arena;
	if (!c.feature) {
		CHECK(c.feature != NULL);
		rvn_arena_free(&arena);
		return;
	}

	CHECK(parse(&arena, "[\"all\", [\"match\", [\"geometry-type\"], [\"LineString\", \"MultiLineString\"], true, false],"
		" [\"match\", [\"get\", \"class\"], [\"minor\", \"service\"], true, false]]", &e));
	CHECK(rvn_map_accepts(&e, &c));
	CHECK(parse(&arena, "[\"!\", [\"has\", \"brunnel\"]]", &e) && rvn_map_accepts(&e, &c));
	CHECK(parse(&arena, "[\"in\", [\"get\", \"class\"], [\"literal\", [\"major\", \"trunk\"]]]", &e) && !rvn_map_accepts(&e, &c));
	CHECK(parse(&arena, "[\"==\", [\"to-number\", \"2\"], 2]", &e) && rvn_map_accepts(&e, &c));
	CHECK(parse(&arena, "[\"case\", [\">=\", [\"zoom\"], 15], \"near\", \"far\"]", &e));
	v = rvn_map_evaluate(&e, &c);
	CHECK(v.kind == RVN_MV_STRING && strcmp(v.as.string, "far") == 0);
	CHECK(parse(&arena, "[\"step\", [\"zoom\"], 1, 12, 2, 14, 3, 16, 4]", &e));
	v = rvn_map_evaluate(&e, &c);
	CHECK(v.kind == RVN_MV_NUMBER && v.as.number == 3.0);
	CHECK(parse(&arena, "[\"upcase\", [\"concat\", [\"get\", \"class\"], \" \", 66]]", &e));
	v = rvn_map_evaluate(&e, &c);
	CHECK(v.kind == RVN_MV_STRING && strcmp(v.as.string, "MINOR 66") == 0);

	CHECK(parse(&arena, "[\"interpolate\", [\"exponential\", 1.2], [\"zoom\"], 13.5, 0, 14, 2.5, 20, 18]", &e));
	v = rvn_map_evaluate(&e, &c);
	CHECK(v.kind == RVN_MV_NUMBER && fabs(v.as.number - 2.5) < 1e-9);
	c.zoom = 13.75;
	v = rvn_map_evaluate(&e, &c);
	CHECK(v.kind == RVN_MV_NUMBER && fabs(v.as.number - 1.25) < 0.1);
	CHECK(parse(&arena, "[\"interpolate\", [\"linear\"], [\"zoom\"], 9, \"#000000\", 13, \"#ffffff\"]", &e));
	c.zoom = 11.0;
	v = rvn_map_evaluate(&e, &c);
	CHECK(rvn_map_parse_color(&v, color));
	hex(color, text);
	CHECK_STR(text, "#808080ff");
	rvn_arena_free(&arena);
}

static void test_colors(void) {
	static const char* const cases[][2] = {
		{ "\"#f8f4f0\"", "#f8f4f0ff" },
		{ "\"#fde\"", "#ffddeeff" },
		{ "\"rgb(158,189,255)\"", "#9ebdffff" },
		{ "\"rgba(95, 208, 100, 0.5)\"", "#5fd06480" },
		{ "\"hsl(35,8%,85%)\"", "#dcd9d6ff" }
	};
	rvn_arena_t arena;
	size_t i;
	rvn_arena_init(&arena, 1024);
	for (i = 0; i < sizeof(cases) / sizeof(cases[0]); i++) {
		rvn_map_value_t v;
		float color[4] = { 0, 0, 0, 0 };
		char text[10];
		CHECK(parse(&arena, cases[i][0], &v) && rvn_map_parse_color(&v, color));
		hex(color, text);
		CHECK_STR(text, cases[i][1]);
	}
	rvn_arena_free(&arena);
}

/* The pieces of packed lines, as "x,y x,y|x,y x,y" with whole coordinates rounded to thousandths. */
static void pieces(const float* xy, uint32_t count, char* out, size_t cap) {
	size_t n = 0;
	uint32_t i;
	int start = 1;
	out[0] = '\0';
	for (i = 0; i < count && n + 32 < cap; i++) {
		if (isnan(xy[i * 2])) {
			if (!start) n += (size_t)snprintf(out + n, cap - n, "|");
			start = 1;
			continue;
		}
		n += (size_t)snprintf(out + n, cap - n, "%s%g,%g", start ? "" : " ", roundf(xy[i * 2] * 1000.0f) / 1000.0f, roundf(xy[i * 2 + 1] * 1000.0f) / 1000.0f);
		start = 0;
	}
}

static void test_dashes_and_offsets(void) {
	/* a line 10 long, then a second one 4 long: 3 on, 2 off, starting again on each line */
	const float lines[] = { 0, 0, 10, 0, NAN, NAN, 0, 5, 0, 9, NAN, NAN };
	const float pattern[] = { 3, 2 };
	const float turn[] = { 0, 0, 2, 0, 2, 9, NAN, NAN };
	const float long_dash[] = { 3, 100 };
	const float straight[] = { 0, 0, 10, 0, NAN, NAN };
	const float corner[] = { 0, 0, 10, 0, 10, 10, NAN, NAN };
	char text[256];
	uint32_t count;
	float* out;

	out = rvn_map_dash(lines, 6, pattern, 2, &count);
	pieces(out, count, text, sizeof(text));
	CHECK_STR(text, "0,0 3,0|5,0 8,0|0,5 0,8|");
	rvn_free(out);
	/* across a turn, a dash keeps the turn's point */
	out = rvn_map_dash(turn, 4, long_dash, 2, &count);
	pieces(out, count, text, sizeof(text));
	CHECK_STR(text, "0,0 2,0 2,1|");
	rvn_free(out);

	/* along x, its left (y down) is up */
	out = rvn_map_offset(straight, 3, 2.0f, &count);
	pieces(out, count, text, sizeof(text));
	CHECK_STR(text, "0,-2 10,-2|");
	rvn_free(out);
	/* right then down: the corner's mitre keeps it 2 from both legs */
	out = rvn_map_offset(corner, 4, 2.0f, &count);
	pieces(out, count, text, sizeof(text));
	CHECK_STR(text, "0,-2 12,-2 12,10|");
	rvn_free(out);
}

static void test_style(void) {
	rvn_map_style_t style;
	char text[10];
	float color[4];
	int i, found_water = 0, found_hidden = 0, found_other = 0;
	CHECK(rvn_map_style_parse(&style, g_style, strlen(g_style), NULL));
	hex(style.background, text);
	CHECK_STR(text, "#f8f4f0ff");
	/* the fills and lines of the first vector source, visible: not "hidden" nor "other" */
	CHECK(style.layer_count == 3);
	for (i = 0; i < (int)style.layer_count; i++) {
		found_water |= strcmp(style.layers[i].id, "water") == 0;
		found_hidden |= strcmp(style.layers[i].id, "hidden") == 0;
		found_other |= strcmp(style.layers[i].id, "other") == 0;
	}
	CHECK(found_water && !found_hidden && !found_other);
	CHECK(style.layers[0].is_fill && style.layers[0].antialias);
	CHECK(rvn_map_layer_color(&style.layers[0], 14.0, color));
	hex(color, text);
	CHECK_STR(text, "#9ebdffff");
	CHECK(fabs(rvn_map_layer_number(&style.layers[2], "line-width", 14.0, 1.0) - 0.757) < 0.001);
	CHECK(rvn_map_layer_line_cap(&style.layers[2], 14.0) == R_LINE_CAP_BUTT);
	rvn_map_style_free(&style);
	CHECK(!rvn_map_style_parse(&style, "{\"layers\": [", 12, NULL));
}

/* A feature of hand-made properties: keys and values side by side, all tagged. */
typedef struct props_t {
	const char* keys[8];
	rvn_map_value_t values[8];
	uint32_t tags[16];
	rvn_map_layer_t layer;
	rvn_map_feature_t feature;
} props_t;

static void props_init(props_t* p) {
	memset(p, 0, sizeof(*p));
	p->layer.keys = p->keys;
	p->layer.values = p->values;
	p->feature.tags = p->tags;
	p->feature.type = RVN_MAP_POINT;
}

static void props_add(props_t* p, const char* key, const char* text, double number) {
	uint32_t i = p->layer.key_count;
	p->keys[i] = key;
	memset(&p->values[i], 0, sizeof(p->values[i]));
	if (text) {
		p->values[i].kind = RVN_MV_STRING;
		p->values[i].as.string = text;
		p->values[i].count = (uint32_t)strlen(text);
	} else {
		p->values[i].kind = RVN_MV_NUMBER;
		p->values[i].as.number = number;
	}
	p->tags[i * 2] = i;
	p->tags[i * 2 + 1] = i;
	p->layer.key_count = p->layer.value_count = i + 1;
	p->feature.tag_count = (i + 1) * 2;
}

/* The text a layout gives a feature, or "(null)". */
static const char* label_of(rvn_arena_t* arena, const char* layout, const props_t* p) {
	rvn_map_style_layer_t layer;
	rvn_map_value_t value;
	rvn_map_context_t c;
	const char* text;
	memset(&layer, 0, sizeof(layer));
	if (!parse(arena, layout, &value)) return "(bad layout)";
	layer.layout = (const rvn_map_value_t*)rvn_arena_alloc(arena, sizeof(value));
	memcpy((void*)layer.layout, &value, sizeof(value));
	memset(&c, 0, sizeof(c));
	c.zoom = 14.0;
	c.layer = &p->layer;
	c.feature = &p->feature;
	c.scratch = arena;
	text = rvn_map_label_text(&layer, &c);
	return text ? text : "(null)";
}

static void test_label_text(void) {
	static const char* field = "\"text-field\": [\"case\", [\"has\", \"name:nonlatin\"], [\"concat\", [\"get\", \"name:latin\"], \"\\n\", [\"get\", \"name:nonlatin\"]],"
		" [\"coalesce\", [\"get\", \"name_en\"], [\"get\", \"name\"]]]";
	char layout[512], upper[512];
	rvn_arena_t arena;
	props_t p;
	rvn_arena_init(&arena, 4096);
	snprintf(layout, sizeof(layout), "{%s}", field);
	snprintf(upper, sizeof(upper), "{%s, \"text-transform\": \"uppercase\"}", field);
	props_init(&p);
	props_add(&p, "name", "Chicago", 0.0);
	CHECK_STR(label_of(&arena, layout, &p), "Chicago");
	props_init(&p);
	props_add(&p, "name", "Chicago (city)", 0.0);
	props_add(&p, "name_en", "Chicago", 0.0);
	CHECK_STR(label_of(&arena, layout, &p), "Chicago");
	props_init(&p);
	props_add(&p, "name:latin", "Tokyo", 0.0);
	props_add(&p, "name:nonlatin", "\xe6\x9d\xb1\xe4\xba\xac", 0.0);
	CHECK_STR(label_of(&arena, layout, &p), "Tokyo\n\xe6\x9d\xb1\xe4\xba\xac");
	props_init(&p);
	props_add(&p, "name", "The Loop", 0.0);
	CHECK_STR(label_of(&arena, upper, &p), "THE LOOP");
	/* a legacy field's tokens, a number written whole */
	props_init(&p);
	props_add(&p, "ref", NULL, 66.0);
	CHECK_STR(label_of(&arena, "{\"text-field\": \"Route {ref}\"}", &p), "Route 66");
	/* no name, no label */
	props_init(&p);
	CHECK_STR(label_of(&arena, layout, &p), "(null)");
	/* case beyond ASCII, where Raven knows it */
	CHECK_STR(rvn_map_change_case(&arena, "caf\xc3\xa9 \xc5\x82\xc3\xb3" "d\xc5\xba \xd0\xbc\xd0\xbe\xd1\x81\xd0\xba\xd0\xb2\xd0\xb0 \xce\xb1\xce\xb8\xce\xae\xce\xbd\xce\xb1", 1),
		"CAF\xc3\x89 \xc5\x81\xc3\x93" "D\xc5\xb9 \xd0\x9c\xd0\x9e\xd0\xa1\xd0\x9a\xd0\x92\xd0\x90 \xce\x91\xce\x98\xce\xae\xce\x9d\xce\x91");
	CHECK_STR(rvn_map_change_case(&arena, "\xc3\x89" "COLE \xc5\x81\xc3\x93" "D\xc5\xb9", 0), "\xc3\xa9" "cole \xc5\x82\xc3\xb3" "d\xc5\xba");
	rvn_arena_free(&arena);
}

/* ---------------------------------------------------------------------------
 *  The widget
 * ------------------------------------------------------------------------- */

static Rid g_window;
static int g_camera_calls;

static void on_camera(Rid map, void* user_data) {
	(void)map;
	(void)user_data;
	g_camera_calls++;
}

/* The latitude and longitude of the middle of tile z/x/y. */
static void tile_center(int z, int x, int y, double* latitude, double* longitude) {
	const double pi = 3.14159265358979323846;
	double n = (double)(1 << z);
	*longitude = (x + 0.5) / n * 360.0 - 180.0;
	*latitude = atan(sinh(pi * (1.0 - 2.0 * (y + 0.5) / n))) * 180.0 / pi;
}

/* Draws frames until no tile handed in or asked for is still being prepared (at most two seconds). */
static void wait_for_tiles(Rid map) {
	RMapStats stats;
	int i;
	for (i = 0; i < 400; i++) {
		Rdraw_frame();
		Rmap_get_stats(map, &stats);
		if (stats.pending_tiles == 0) return;
		Rwait_frame(0.005);
	}
}

static void test_map_widget(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	Rid map = Rwidget_create(R_WIDGET_MAP);
	RMapStats stats;
	double latitude, longitude, lat2, lon2;
	CHECK(Rwidget_get_type(map) == R_WIDGET_MAP);
	Rwindow_set_content(g_window, map);
	Rmap_set_camera_changed_callback(map, on_camera, NULL);

	/* a tile before a style is dropped as it lands; a style that isn't JSON is refused */
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	wait_for_tiles(map);
	Rmap_get_stats(map, &stats);
	CHECK(stats.prepared_tiles == 0);
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rmap_set_style_json(map, "{\"layers\": [", -1, NULL));
	EXPECT_ERRORS_END(1);

	CHECK(Rmap_set_style_json(map, g_style, -1, NULL));
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rmap_tile_loaded(map, 14, 1 << 14, 0, bytes, length));
	EXPECT_ERRORS_END(1);
	/* a tile that can't be decoded counts as failed when it lands */
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6090, bytes, length - 3));
	wait_for_tiles(map);
	Rmap_get_stats(map, &stats);
	CHECK(stats.failed_tiles == 1);
	CHECK(stats.prepared_tiles == 1);

	tile_center(14, 4203, 6089, &latitude, &longitude);
	Rmap_set_center(map, latitude, longitude);
	Rmap_set_zoom(map, 14.5);
	Rmap_get_center(map, &lat2, &lon2);
	CHECK(fabs(lat2 - latitude) < 1e-9 && fabs(lon2 - longitude) < 1e-9);
	CHECK(Rmap_get_zoom(map) == 14.5);
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	/* the lake (two triangles) and the road (one segment; as in the spike, fills' outlines aren't counted) */
	CHECK(stats.tiles == 1);
	CHECK(stats.triangles == 2);
	CHECK(stats.segments == 1);
	CHECK(stats.prepared_tiles == 1);

	/* a tile not held is drawn from its nearest ancestor that is (handed in gzipped, as servers send them) */
	CHECK(Rmap_tile_loaded(map, 13, 4203 / 2, 6089 / 2, g_water_gzip, sizeof(g_water_gzip)));
	wait_for_tiles(map);
	tile_center(14, 4202, 6089, &latitude, &longitude);
	Rmap_set_center(map, latitude, longitude);
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	CHECK(stats.tiles >= 1);
	CHECK(stats.prepared_tiles == 2);

	/* dragged, the map follows the pointer; scrolled, it zooms around it */
	g_camera_calls = 0;
	Rmap_get_center(map, &latitude, &longitude);
	Rwindow_inject_mouse_move(g_window, 250.0f, 200.0f);
	Rwindow_inject_mouse_button(g_window, R_MOUSE_LEFT, R_TRUE);
	Rwindow_inject_mouse_move(g_window, 350.0f, 200.0f);
	Rwindow_inject_mouse_button(g_window, R_MOUSE_LEFT, R_FALSE);
	Rmap_get_center(map, &lat2, &lon2);
	CHECK(lon2 < longitude && fabs(lat2 - latitude) < 1e-9);
	/* 100 units at zoom 14.5: 100 / (512 * 2^14.5) of the world's 360 degrees */
	CHECK(fabs((longitude - lon2) - 100.0 / (512.0 * pow(2.0, 14.5)) * 360.0) < 1e-9);
	CHECK(g_camera_calls == 1);
	Rwindow_inject_mouse_wheel(g_window, 0.0f, 2.0f);
	CHECK(fabs(Rmap_get_zoom(map) - 15.0) < 1e-9);
	CHECK(g_camera_calls == 2);
	/* the zoom stops at its range */
	Rmap_set_zoom_range(map, 2.0, 15.0);
	Rwindow_inject_mouse_wheel(g_window, 0.0f, 2.0f);
	CHECK(fabs(Rmap_get_zoom(map) - 15.0) < 1e-9);

	/* a new style forgets the tiles prepared for the old one */
	CHECK(Rmap_set_style_json(map, g_style, -1, "openmaptiles"));
	Rmap_get_stats(map, &stats);
	CHECK(stats.prepared_tiles == 0);
	Rmap_set_attribution(map, "\xc2\xa9 test");
	Rdraw_frame();
	Rwindow_set_content(g_window, R_INVALID_ID);
}

/* ---------------------------------------------------------------------------
 *  The tile store, as the spike's TileStoreTest has it
 * ------------------------------------------------------------------------- */

#define MAX_ASKED 64

static struct {
	int requests, cancels;
	int requested[MAX_ASKED][3];
	int cancelled[MAX_ASKED][3];
} g_source;

static void on_request(Rid map, int z, int x, int y, void* user_data) {
	(void)map;
	(void)user_data;
	if (g_source.requests < MAX_ASKED) {
		g_source.requested[g_source.requests][0] = z;
		g_source.requested[g_source.requests][1] = x;
		g_source.requested[g_source.requests][2] = y;
	}
	g_source.requests++;
}

static void on_cancel(Rid map, int z, int x, int y, void* user_data) {
	(void)map;
	(void)user_data;
	if (g_source.cancels < MAX_ASKED) {
		g_source.cancelled[g_source.cancels][0] = z;
		g_source.cancelled[g_source.cancels][1] = x;
		g_source.cancelled[g_source.cancels][2] = y;
	}
	g_source.cancels++;
}

static int was_asked(int list[][3], int count, int z, int x, int y) {
	int i;
	for (i = 0; i < count && i < MAX_ASKED; i++) {
		if (list[i][0] == z && list[i][1] == x && list[i][2] == y) return 1;
	}
	return 0;
}

/* A map of the test style looking at the middle of tile 14/x/y: a 500 by 400 view at zoom 14 wants it alone. */
static Rid source_map(int min_zoom, int x, int y) {
	Rid map = Rwidget_create(R_WIDGET_MAP);
	double latitude, longitude;
	memset(&g_source, 0, sizeof(g_source));
	Rwindow_set_content(g_window, map);
	CHECK(Rmap_set_style_json(map, g_style, -1, NULL));
	Rmap_set_tile_source(map, min_zoom, 14, on_request, on_cancel, NULL);
	tile_center(14, x, y, &latitude, &longitude);
	Rmap_set_center(map, latitude, longitude);
	Rmap_set_zoom(map, 14.0);
	return map;
}

static void look_at(Rid map, int x, int y) {
	double latitude, longitude;
	tile_center(14, x, y, &latitude, &longitude);
	Rmap_set_center(map, latitude, longitude);
}

static void test_tile_asked_for_once(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	Rid map = source_map(12, 4203, 6089);
	RMapStats stats;
	/* the tile and its ancestors down to the source's lowest zoom, asked for once, outside painting */
	Rdraw_frame();
	Rdraw_frame();
	Rwidget_invalidate(map);
	Rdraw_frame();
	CHECK(g_source.requests == 3);
	CHECK(was_asked(g_source.requested, g_source.requests, 14, 4203, 6089));
	CHECK(was_asked(g_source.requested, g_source.requests, 13, 2101, 3044));
	CHECK(was_asked(g_source.requested, g_source.requests, 12, 1050, 1522));
	Rmap_get_stats(map, &stats);
	CHECK(stats.pending_tiles == 3 && stats.requested_tiles == 3 && stats.tiles == 0);
	/* the ancestor lands first, and stands in */
	CHECK(Rmap_tile_loaded(map, 12, 1050, 1522, bytes, length));
	wait_for_tiles(map);
	Rmap_get_stats(map, &stats);
	CHECK(stats.prepared_tiles == 1 && stats.tiles == 1);
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	CHECK(Rmap_tile_loaded(map, 13, 2101, 3044, NULL, 0));
	wait_for_tiles(map);
	Rmap_get_stats(map, &stats);
	CHECK(stats.prepared_tiles == 3 && stats.pending_tiles == 0);
	CHECK(stats.tiles == 1 && stats.triangles == 2);
	CHECK(g_source.requests == 3 && g_source.cancels == 0);
	CHECK(stats.memory_bytes > 0);
	Rwindow_set_content(g_window, R_INVALID_ID);
}

static void test_tile_no_longer_wanted(void) {
	Rid map = source_map(14, 4203, 6089);
	RMapStats stats;
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.requests == 1);
	/* panned away: the tile still loading is cancelled, and the new one asked for */
	look_at(map, 4300, 6089);
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.cancels == 1 && was_asked(g_source.cancelled, g_source.cancels, 14, 4203, 6089));
	CHECK(g_source.requests == 2 && was_asked(g_source.requested, g_source.requests, 14, 4300, 6089));
	Rmap_get_stats(map, &stats);
	CHECK(stats.pending_tiles == 1);
	/* a cancelled tile is no failure, and is asked for again from the start */
	CHECK(stats.failed_tiles == 0);
	look_at(map, 4203, 6089);
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.requests == 3);
	/* zoomed out past the source's lowest zoom: nothing is drawn or asked for, and what loads stops */
	Rmap_set_zoom(map, 3.0);
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.requests == 3 && g_source.cancels == 3);
	Rmap_get_stats(map, &stats);
	CHECK(stats.pending_tiles == 0 && stats.tiles == 0);
	Rwindow_set_content(g_window, R_INVALID_ID);
}

static void test_tiles_used_least_recently_go(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	Rid map = source_map(14, 100, 200);
	RMapStats stats;
	int i, requests;
	Rmap_set_tile_capacity(map, 2);
	for (i = 0; i < 4; i++) {
		/* frames wanting this tile alone, until it is ready */
		look_at(map, 100 + i * 4, 200);
		Rdraw_frame();
		Rdraw_frame();
		CHECK(Rmap_tile_loaded(map, 14, 100 + i * 4, 200, bytes, length));
		wait_for_tiles(map);
		Rwidget_invalidate(map);
		Rdraw_frame();
	}
	Rmap_get_stats(map, &stats);
	CHECK(stats.prepared_tiles == 2);
	/* the last two stayed: looking at them asks for nothing; the first two went */
	requests = g_source.requests;
	look_at(map, 112, 200);
	Rdraw_frame();
	look_at(map, 108, 200);
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.requests == requests);
	look_at(map, 100, 200);
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.requests == requests + 1);
	Rwindow_set_content(g_window, R_INVALID_ID);
}

static void test_tile_failed_waits(void) {
	Rid map = source_map(14, 4203, 6089);
	RMapStats stats;
	Rdraw_frame();
	Rdraw_frame();
	CHECK(g_source.requests == 1);
	Rmap_tile_failed(map, 14, 4203, 6089);
	wait_for_tiles(map);
	Rwidget_invalidate(map);
	Rdraw_frame();
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	CHECK(stats.failed_tiles == 1 && stats.pending_tiles == 0);
	/* not asked for again at once */
	CHECK(g_source.requests == 1);
	Rwindow_set_content(g_window, R_INVALID_ID);
}

static void test_tile_for_an_old_style(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	Rid map = source_map(14, 4203, 6089);
	RMapStats stats;
	/* handed in, then the style changes before it lands: it is dropped, and the map destroyed
	   with a tile on its way frees it as it lands */
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	Rdraw_frame();
	CHECK(Rmap_set_style_json(map, g_style, -1, NULL));
	wait_for_tiles(map);
	Rmap_get_stats(map, &stats);
	CHECK(stats.prepared_tiles == 0);
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	Rwindow_set_content(g_window, R_INVALID_ID);
	Rdraw_frame();
}

/* ---------------------------------------------------------------------------
 *  Labels
 * ------------------------------------------------------------------------- */

/*
 * A tile of two layers, extent 4096: "place", two points close together at
 * the middle (Chicago, then Evanston, which collides with it), and
 * "road_name", a line across the tile a quarter down, named Main Street.
 */
static size_t label_tile(uint8_t* out, size_t cap) {
	const uint32_t chicago[] = { command(1, 1), zz(2048), zz(2048) };
	const uint32_t evanston[] = { command(1, 1), zz(2056), zz(2052) };
	const uint32_t road[] = { command(1, 1), zz(0), zz(1024), command(2, 1), zz(4096), zz(0) };
	const uint32_t tag0[] = { 0, 0 }, tag1[] = { 0, 1 };
	buf_t feature, value, layer, tile;
	tile.length = 0;

	layer.length = 0;
	put_string(&layer, 1, "place");
	feature.length = 0;
	put_packed(&feature, 2, tag0, 2);
	put_field(&feature, 3, 1);
	put_packed(&feature, 4, chicago, 3);
	put_message(&layer, 2, &feature);
	feature.length = 0;
	put_packed(&feature, 2, tag1, 2);
	put_field(&feature, 3, 1);
	put_packed(&feature, 4, evanston, 3);
	put_message(&layer, 2, &feature);
	put_string(&layer, 3, "name");
	value.length = 0;
	put_string(&value, 1, "Chicago");
	put_message(&layer, 4, &value);
	value.length = 0;
	put_string(&value, 1, "Evanston");
	put_message(&layer, 4, &value);
	put_field(&layer, 5, 4096);
	put_message(&tile, 3, &layer);

	layer.length = 0;
	put_string(&layer, 1, "road_name");
	feature.length = 0;
	put_packed(&feature, 2, tag0, 2);
	put_field(&feature, 3, 2);
	put_packed(&feature, 4, road, 6);
	put_message(&layer, 2, &feature);
	put_string(&layer, 3, "name");
	value.length = 0;
	put_string(&value, 1, "Main Street");
	put_message(&layer, 4, &value);
	put_field(&layer, 5, 4096);
	put_message(&tile, 3, &layer);

	if (tile.length > cap) return 0;
	memcpy(out, tile.data, tile.length);
	return tile.length;
}

static const char* g_label_style =
	"{\"version\": 8, \"sources\": {\"openmaptiles\": {\"type\": \"vector\"}},"
	" \"layers\": ["
	"  {\"id\": \"background\", \"type\": \"background\", \"paint\": {\"background-color\": \"#ffffff\"}},"
	"  {\"id\": \"road-name\", \"type\": \"symbol\", \"source\": \"openmaptiles\", \"source-layer\": \"road_name\","
	"   \"layout\": {\"text-field\": \"{name}\", \"symbol-placement\": \"line\", \"text-size\": 12},"
	"   \"paint\": {\"text-color\": \"#333\", \"text-halo-color\": \"#fff\", \"text-halo-width\": 1}},"
	"  {\"id\": \"place\", \"type\": \"symbol\", \"source\": \"openmaptiles\", \"source-layer\": \"place\","
	"   \"layout\": {\"text-field\": [\"get\", \"name\"], \"text-size\": 14, \"icon-image\": \"dot\", \"text-anchor\": \"top\","
	"    \"text-offset\": [0, 0.6], \"text-font\": [\"Noto Sans Bold\"]}}"
	" ]}";

static uint32_t crc32_of(const uint8_t* data, size_t length, uint32_t crc) {
	size_t i;
	int k;
	crc = ~crc;
	for (i = 0; i < length; i++) {
		crc ^= data[i];
		for (k = 0; k < 8; k++) crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
	}
	return ~crc;
}

static void put_be32(buf_t* b, uint32_t v) {
	put_byte(b, (uint8_t)(v >> 24));
	put_byte(b, (uint8_t)(v >> 16));
	put_byte(b, (uint8_t)(v >> 8));
	put_byte(b, (uint8_t)v);
}

static void put_chunk(buf_t* b, const char* type, const uint8_t* data, size_t length) {
	size_t start;
	put_be32(b, (uint32_t)length);
	start = b->length;
	put_bytes(b, type, 4);
	put_bytes(b, data, length);
	put_be32(b, crc32_of(b->data + start, length + 4, 0));
}

/* A 4 by 4 PNG, opaque red, written by hand: one stored deflate block. */
static size_t red_png(uint8_t* out, size_t cap) {
	static const uint8_t signature[] = { 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
	uint8_t header[13] = { 0, 0, 0, 4, 0, 0, 0, 4, 8, 6, 0, 0, 0 };
	uint8_t raw[4 * (1 + 16)], zlib[128];
	uint32_t a = 1, b = 0;
	size_t i, n = 0;
	buf_t png;
	for (i = 0; i < sizeof(raw); i++) raw[i] = (i % 17 == 0) ? 0 : ((i % 17 - 1) % 4 == 0 || (i % 17 - 1) % 4 == 3) ? 255 : 0;
	zlib[n++] = 0x78;
	zlib[n++] = 0x01;
	zlib[n++] = 1;
	zlib[n++] = (uint8_t)sizeof(raw);
	zlib[n++] = 0;
	zlib[n++] = (uint8_t)~sizeof(raw);
	zlib[n++] = 0xFF;
	memcpy(zlib + n, raw, sizeof(raw));
	n += sizeof(raw);
	for (i = 0; i < sizeof(raw); i++) {
		a = (a + raw[i]) % 65521;
		b = (b + a) % 65521;
	}
	zlib[n++] = (uint8_t)(b >> 8);
	zlib[n++] = (uint8_t)b;
	zlib[n++] = (uint8_t)(a >> 8);
	zlib[n++] = (uint8_t)a;
	png.length = 0;
	put_bytes(&png, signature, sizeof(signature));
	put_chunk(&png, "IHDR", header, sizeof(header));
	put_chunk(&png, "IDAT", zlib, n);
	put_chunk(&png, "IEND", NULL, 0);
	if (png.length > cap) return 0;
	memcpy(out, png.data, png.length);
	return png.length;
}

static void test_labels(void) {
	uint8_t bytes[1024], png[512];
	size_t length = label_tile(bytes, sizeof(bytes));
	const char* index = "{\"dot\": {\"x\": 0, \"y\": 0, \"width\": 4, \"height\": 4, \"pixelRatio\": 2}}";
	Rid map = Rwidget_create(R_WIDGET_MAP);
	RMapStats stats;
	double latitude, longitude;
	Rwindow_set_content(g_window, map);
	CHECK(Rmap_set_style_json(map, g_label_style, -1, NULL));
	Rmap_set_tile_zoom_range(map, 14, 14);
	tile_center(14, 4203, 6089, &latitude, &longitude);
	Rmap_set_center(map, latitude, longitude);
	Rmap_set_zoom(map, 14.0);
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	wait_for_tiles(map);
	Rwidget_invalidate(map);
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	/* Chicago, then Evanston kept off it by collision; Main Street along its line */
	CHECK(stats.label_candidates == 3);
	CHECK(stats.labels_placed == 2);
	CHECK(stats.icons == 0);
	/* placed as the tile landed: drawn again, the placement is kept */
	CHECK(stats.labels_reused);

	/* a pan of a few units moves the placement rather than placing again... */
	Rmap_set_center(map, latitude, longitude + 4.0 / (512.0 * 16384.0) * 360.0);
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	CHECK(stats.labels_reused);
	/* ...until the camera has rested there a moment */
	{
		double start = Rget_time();
		while (Rget_time() - start < 0.3) Rwait_frame(0.05);
	}
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	CHECK(!stats.labels_reused);
	CHECK(stats.labels_placed == 2);
	/* a new sprite places again, with the icon */
#if !defined(RAVEN_TEST_NO_IMAGE_DECODER)
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rmap_set_sprite(map, "{", -1, png, red_png(png, sizeof(png))));
	EXPECT_ERRORS_END(1);
	CHECK(Rmap_set_sprite(map, index, -1, png, red_png(png, sizeof(png))));
	Rdraw_frame();
	Rmap_get_stats(map, &stats);
	CHECK(!stats.labels_reused);
	CHECK(stats.labels_placed == 2 && stats.icons == 1);
	CHECK(Rmap_set_sprite(map, NULL, 0, NULL, 0));
#endif
	Rwindow_set_content(g_window, R_INVALID_ID);
}

/* Against the maps spike's downloaded tiles, when MAPS_DATA names them: what the Loop's tile prepares. */
static void test_downloaded_tile(void) {
	const char* data = getenv("MAPS_DATA");
	char path[1024];
	char* json;
	char* bytes;
	size_t json_size, size;
	rvn_map_style_t* style;
	rvn_map_prepared_t tile;
	uint32_t i;
	int poi = 0, minor = 0, arrows = 0, major = 0, points = 0;
	if (!data) {
		printf("  (MAPS_DATA unset: skipped)\n");
		return;
	}
	snprintf(path, sizeof(path), "%s/liberty.json", data);
	json = rvn_read_file(path, &json_size);
	snprintf(path, sizeof(path), "%s/tiles/14-4203-6089.pbf", data);
	bytes = rvn_read_file(path, &size);
	CHECK(json && bytes);
	if (!json || !bytes) {
		rvn_free(json);
		rvn_free(bytes);
		return;
	}
	style = rvn_map_style_create(json, json_size, NULL);
	CHECK(style != NULL);
	CHECK(style && rvn_map_prepare(&tile, style, 14, 4203, 6089, (const uint8_t*)bytes, size, R_TRUE));
	for (i = 0; style && i < style->symbol_count; i++) {
		const rvn_map_symbol_bucket_t* b = &tile.symbols[i];
		const char* id = style->symbols[i].id;
		uint32_t k;
		points += b->label_count > 0;
		/* the deepest tiles draw every zoom past theirs: points of interest and minor street names from 15 */
		if (strcmp(id, "poi_r1") == 0) poi = b->label_count > 0;
		if (strcmp(id, "highway-name-minor") == 0) minor = b->line_count > 0;
		if (strcmp(id, "highway-name-major") == 0) major = b->line_count > 0;
		/* one-way arrows: icons alone along their roads */
		if (strcmp(id, "road_one_way_arrow") == 0) {
			for (k = 0; k < b->line_count; k++) arrows |= b->lines[k].icon && strcmp(b->lines[k].icon, "arrow") == 0 && !b->lines[k].text;
		}
	}
	CHECK(poi && minor && major && arrows && points);
	if (style) rvn_map_prepared_free(&tile);
	rvn_map_style_release(style);
	rvn_free(json);
	rvn_free(bytes);
}

int main(void) {
	if (!test_init(0)) {
		printf("cannot initialize Raven\n");
		return 1;
	}
	g_window = Rwindow_create("map", 500, 400, R_WINDOW_DEFAULT);

	RUN(test_json);
	RUN(test_tile_decoding);
	RUN(test_compressed_tiles);
	RUN(test_earcut);
	RUN(test_expressions);
	RUN(test_colors);
	RUN(test_dashes_and_offsets);
	RUN(test_style);
	RUN(test_label_text);
	RUN(test_map_widget);
	RUN(test_tile_asked_for_once);
	RUN(test_tile_no_longer_wanted);
	RUN(test_tiles_used_least_recently_go);
	RUN(test_tile_failed_waits);
	RUN(test_tile_for_an_old_style);
	RUN(test_labels);
	RUN(test_downloaded_tile);

	Rdraw_frame();
	Rshutdown();
	return test_report("test_map");
}
