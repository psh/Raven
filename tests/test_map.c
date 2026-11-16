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

static void test_map_widget(void) {
	uint8_t bytes[1024];
	size_t length = water_tile(bytes, sizeof(bytes));
	Rid map = Rwidget_create(R_WIDGET_MAP);
	RMapStats stats;
	double latitude, longitude, lat2, lon2;
	CHECK(Rwidget_get_type(map) == R_WIDGET_MAP);
	Rwindow_set_content(g_window, map);
	Rmap_set_camera_changed_callback(map, on_camera, NULL);

	/* a tile before a style is refused */
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	CHECK(!Rmap_set_style_json(map, "{\"layers\": [", -1, NULL));
	EXPECT_ERRORS_END(2);

	CHECK(Rmap_set_style_json(map, g_style, -1, NULL));
	CHECK(Rmap_tile_loaded(map, 14, 4203, 6089, bytes, length));
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rmap_tile_loaded(map, 14, 1 << 14, 0, bytes, length));
	EXPECT_ERRORS_END(1);
	CHECK(!Rmap_tile_loaded(map, 14, 4203, 6090, bytes, length - 3));

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

	/* a tile not held is drawn from its nearest ancestor that is */
	CHECK(Rmap_tile_loaded(map, 13, 4203 / 2, 6089 / 2, bytes, length));
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

int main(void) {
	if (!test_init(0)) {
		printf("cannot initialize Raven\n");
		return 1;
	}
	g_window = Rwindow_create("map", 500, 400, R_WINDOW_DEFAULT);

	RUN(test_json);
	RUN(test_tile_decoding);
	RUN(test_earcut);
	RUN(test_expressions);
	RUN(test_colors);
	RUN(test_dashes_and_offsets);
	RUN(test_style);
	RUN(test_map_widget);

	Rdraw_frame();
	Rshutdown();
	return test_report("test_map");
}
