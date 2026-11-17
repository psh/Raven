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
 * The tiles come from a tile source: the map asks for each tile as the view
 * needs it, and the example reads it from the folder and hands it back (a
 * tile not in the folder is no tile). Drag to move the map, scroll to zoom.
 * What each frame costs is shown over it, twice a second.
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

/* The source: a tile asked for is read from the folder, or is no tile there. */
static void request_tile(Rid map, int z, int x, int y, void* user_data) {
	const char* folder = (const char*)user_data;
	char path[1024];
	size_t size = 0;
	char* bytes;
	snprintf(path, sizeof(path), "%s/tiles/%d-%d-%d.pbf", folder, z, x, y);
	bytes = read_file(path, &size);
	Rmap_tile_loaded(map, z, x, y, bytes, bytes ? size : 0);
	free(bytes);
}

/* The zooms the folder's tiles.txt lists. */
static void zoom_range(const char* folder, int* min_zoom, int* max_zoom) {
	char path[1024];
	char* list;
	char* line;
	*min_zoom = 30;
	*max_zoom = 0;
	snprintf(path, sizeof(path), "%s/tiles.txt", folder);
	list = read_file(path, NULL);
	for (line = list ? strtok(list, "\n") : NULL; line; line = strtok(NULL, "\n")) {
		int z, x, y;
		if (sscanf(line, "%d %d %d", &z, &x, &y) != 3) continue;
		if (z < *min_zoom) *min_zoom = z;
		if (z > *max_zoom) *max_zoom = z;
	}
	free(list);
	if (*min_zoom > *max_zoom) *min_zoom = *max_zoom = 14;
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
		"%d tiles prepared (%.1f MB): decode %.1f ms, style + triangulate %.1f ms\n"
		"%d asked for, %d loading, %d failed\n"
		"%d labels placed of %d, %d icons: %.1f ms, placing %.1f ms%s",
		Rmap_get_zoom(app.map), stats.tiles, stats.triangles, stats.segments, stats.draw_ms, stats.fill_ms, stats.line_ms,
		frame.paint_ms, frame.render_ms, frame.instance_count, frame.draw_call_count,
		stats.prepared_tiles, (double)stats.memory_bytes / (1024.0 * 1024.0), stats.decode_ms, stats.prepare_ms,
		stats.requested_tiles, stats.pending_tiles, stats.failed_tiles,
		stats.labels_placed, stats.label_candidates, stats.icons, stats.labels_ms, stats.place_ms, stats.labels_reused ? " (reused)" : "");
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
	int min_zoom, max_zoom;

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
	/* the icons, from the style's sprite sheet at twice the pixels */
	{
		size_t index_size, png_size;
		char* index;
		char* png;
		snprintf(path, sizeof(path), "%s/sprite@2x.json", folder);
		index = read_file(path, &index_size);
		snprintf(path, sizeof(path), "%s/sprite@2x.png", folder);
		png = read_file(path, &png_size);
		if (index && png) Rmap_set_sprite(app.map, index, (int)index_size, png, png_size);
		free(index);
		free(png);
	}
	Rmap_set_attribution(app.map, "\xc2\xa9 OpenFreeMap \xc2\xa9 OpenMapTiles Data from OpenStreetMap");
	zoom_range(folder, &min_zoom, &max_zoom);
	Rmap_set_tile_source(app.map, min_zoom, max_zoom, request_tile, NULL, (void*)folder);
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
