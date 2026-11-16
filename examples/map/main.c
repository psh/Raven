/**
 * Raven example - map
 * The Map widget over the tiles of compose-raven's maps spike: OpenFreeMap's
 * Liberty style and the vector tiles of central Chicago at zooms 12 to 14,
 * which compose/spikes/maps/fetch-data downloads (they are kept out of git).
 *
 *   raven_map [data folder]
 *
 * The folder defaults to MAPS_DATA, else the spike's own data/ folder.
 * MAPS_LAT, MAPS_LON and MAPS_ZOOM say where the map opens.
 *
 * Drag to move the map, scroll to zoom. What each frame costs is shown over
 * it, twice a second.
 */

#include "../common/example.h"

#ifndef RAVEN_EXAMPLE_DIR
#define RAVEN_EXAMPLE_DIR "."
#endif

static struct {
	Rid window, map, stats;
} app;

static char* read_file(const char* path, size_t* size) {
	FILE* f = fopen(path, "rb");
	char* data;
	long length;
	if (!f) return NULL;
	fseek(f, 0, SEEK_END);
	length = ftell(f);
	fseek(f, 0, SEEK_SET);
	data = (char*)malloc((size_t)length + 1);
	if (data && fread(data, 1, (size_t)length, f) != (size_t)length) {
		free(data);
		data = NULL;
	}
	fclose(f);
	if (data) data[length] = '\0';
	if (size) *size = (size_t)length;
	return data;
}

/* Hands the map every tile listed in tiles.txt. Returns how many it took. */
static int load_tiles(const char* folder) {
	char path[1024];
	char* list;
	char* line;
	int count = 0, min_zoom = 30, max_zoom = 0;
	snprintf(path, sizeof(path), "%s/tiles.txt", folder);
	list = read_file(path, NULL);
	if (!list) return 0;
	/* the zoom range first: the deepest tiles are prepared for the zooms past theirs */
	for (line = list; *line; line++) {
		int z, x, y;
		if ((line == list || line[-1] == '\n') && sscanf(line, "%d %d %d", &z, &x, &y) == 3) {
			if (z < min_zoom) min_zoom = z;
			if (z > max_zoom) max_zoom = z;
		}
	}
	if (min_zoom <= max_zoom) Rmap_set_tile_zoom_range(app.map, min_zoom, max_zoom);
	for (line = strtok(list, "\n"); line; line = strtok(NULL, "\n")) {
		int z, x, y;
		size_t size;
		char* bytes;
		if (sscanf(line, "%d %d %d", &z, &x, &y) != 3) continue;
		snprintf(path, sizeof(path), "%s/tiles/%d-%d-%d.pbf", folder, z, x, y);
		bytes = read_file(path, &size);
		if (bytes && Rmap_tile_loaded(app.map, z, x, y, bytes, size)) count++;
		free(bytes);
	}
	free(list);
	return count;
}

static void show_stats(Rid timer, void* user_data) {
	RMapStats stats;
	RFrameStats frame;
	char text[512];
	(void)timer;
	(void)user_data;
	Rmap_get_stats(app.map, &stats);
	Rwindow_get_frame_stats(app.window, &frame);
	snprintf(text, sizeof(text),
		"zoom %.2f, %d tiles\n"
		"%d triangles, %d line segments\n"
		"C draw calls: %.1f ms (fills %.1f, lines %.1f)\n"
		"Raven: paint %.1f ms, render %.1f ms, %u primitives, %u GPU draws\n"
		"%d tiles prepared: decode %.1f ms, style + triangulate %.1f ms",
		Rmap_get_zoom(app.map), stats.tiles, stats.triangles, stats.segments, stats.draw_ms, stats.fill_ms, stats.line_ms,
		frame.paint_ms, frame.render_ms, frame.instance_count, frame.draw_call_count,
		stats.prepared_tiles, stats.decode_ms, stats.prepare_ms);
	Rlabel_set_text(app.stats, text);
}

static double env_number(const char* name, double fallback) {
	const char* value = getenv(name);
	return value ? atof(value) : fallback;
}

int main(int argc, char** argv) {
	const char* folder = argc > 1 ? argv[1] : getenv("MAPS_DATA");
	char path[1024];
	char* style;
	Rid overlay, panel;
	int tiles;

	if (!folder) folder = RAVEN_EXAMPLE_DIR "/../../compose/spikes/maps/data";
	snprintf(path, sizeof(path), "%s/liberty.json", folder);
	style = read_file(path, NULL);
	if (!style) {
		fprintf(stderr, "raven_map: no liberty.json in %s (run compose/spikes/maps/fetch-data)\n", folder);
		return 1;
	}
	if (!example_init("map")) return 1;
	app.window = Rwindow_create("Map", 1200, 800, R_WINDOW_DEFAULT);

	overlay = Rwidget_create(R_WIDGET_OVERLAY);
	Rwindow_set_content(app.window, overlay);
	app.map = Rwidget_add_child(overlay, Rwidget_create(R_WIDGET_MAP));
	if (!Rmap_set_style_json(app.map, style, -1, NULL)) return 1;
	free(style);
	Rmap_set_attribution(app.map, "\xc2\xa9 OpenFreeMap \xc2\xa9 OpenMapTiles Data from OpenStreetMap");
	tiles = load_tiles(folder);
	printf("%d tiles\n", tiles);
	Rmap_set_center(app.map, env_number("MAPS_LAT", 41.882), env_number("MAPS_LON", -87.630));
	Rmap_set_zoom(app.map, env_number("MAPS_ZOOM", 14.0));

	/* what the frame costs, top left */
	panel = Rwidget_add_child(overlay, Rwidget_create(R_WIDGET_BORDER));
	Rslot_set_halign(panel, R_HALIGN_LEFT);
	Rslot_set_valign(panel, R_VALIGN_TOP);
	Rslot_set_padding(panel, 12.0f, 12.0f, 12.0f, 12.0f);
	Rborder_set_background(panel, 0.13f, 0.13f, 0.13f, 0.87f);
	Rborder_set_corner_radius(panel, 6.0f, 6.0f, 6.0f, 6.0f);
	Rborder_set_padding(panel, 8.0f, 8.0f, 8.0f, 8.0f);
	app.stats = Rwidget_add_child(panel, Rlabel_create(""));
	Rwidget_set_color(app.stats, R_COLOR_TEXT, 1.0f, 1.0f, 1.0f, 1.0f);
	Rlabel_set_text_size(app.stats, 12.0f);
	show_stats(R_INVALID_ID, NULL);
	Rtimer_create(0.5, R_TRUE, show_stats, NULL);

	example_run(app.window, NULL, NULL);
	return 0;
}
