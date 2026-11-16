/**
 * Raven - the Map widget's benchmark. Not a test: it prints what preparing
 * tiles and drawing a view cost, measured as the Kotlin maps spike measures
 * them (compose/spikes/maps, MapsBenchmark in MapsTest.kt), so the two can be
 * compared.
 *
 *   raven_map_benchmark <data>
 *
 * <data> is the folder compose/spikes/maps/fetch-data fills: liberty.json,
 * tiles.txt and tiles/<z>-<x>-<y>.pbf, OpenFreeMap's Liberty style and the
 * tiles of central Chicago at zooms 12 to 14. Headless: no window system, no
 * GPU.
 */

#include <Raven.h>

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define FRAMES 30
#define PREPARES 10

static const double START_LATITUDE = 41.882, START_LONGITUDE = -87.630;
/* the densest tile of the set, the Loop's: 530 KB */
static const int DENSE_Z = 14, DENSE_X = 4203, DENSE_Y = 6089;

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

static int compare(const void* a, const void* b) {
	double x = *(const double*)a, y = *(const double*)b;
	return x < y ? -1 : x > y;
}

static double median(double* values, int count) {
	qsort(values, (size_t)count, sizeof(double), compare);
	return values[count / 2];
}

static int load_tile(Rid map, const char* data, int z, int x, int y) {
	char path[1024];
	size_t size;
	char* bytes;
	int ok;
	snprintf(path, sizeof(path), "%s/tiles/%d-%d-%d.pbf", data, z, x, y);
	bytes = read_file(path, &size);
	if (!bytes) return 0;
	ok = Rmap_tile_loaded(map, z, x, y, bytes, size);
	free(bytes);
	return ok;
}

int main(int argc, char** argv) {
	const char* data = argc > 1 ? argv[1] : getenv("MAPS_DATA");
	char path[1024];
	char* style;
	char* list;
	char* line;
	RInitDesc desc;
	RMapStats stats;
	RFrameStats frame;
	Rid window, map;
	double decode[PREPARES], prepare[PREPARES], fills[FRAMES], lines[FRAMES], draws[FRAMES], paints[FRAMES];
	double latitude, longitude, world;
	int i, size, tiles = 0;

	if (!data) {
		printf("usage: raven_map_benchmark <the folder compose/spikes/maps/fetch-data fills>\n");
		return 2;
	}
	snprintf(path, sizeof(path), "%s/liberty.json", data);
	style = read_file(path, NULL);
	snprintf(path, sizeof(path), "%s/tiles.txt", data);
	list = read_file(path, NULL);
	if (!style || !list) {
		printf("no liberty.json or tiles.txt in %s\n", data);
		return 2;
	}

	memset(&desc, 0, sizeof(desc));
	desc.backend = R_BACKEND_NULL;
	desc.headless = R_TRUE;
	if (!Rinit_ex(&desc)) return 1;
	window = Rwindow_create("map benchmark", 1200, 800, R_WINDOW_DEFAULT);
	map = Rwidget_create(R_WIDGET_MAP);
	Rwindow_set_content(window, map);
	if (!Rmap_set_style_json(map, style, -1, NULL)) return 1;
	Rmap_set_tile_zoom_range(map, 12, 14);

	/* preparing the densest tile, again and again */
	for (i = 0; i < PREPARES; i++) {
		Rmap_set_style_json(map, style, -1, NULL);
		if (!load_tile(map, data, DENSE_Z, DENSE_X, DENSE_Y)) {
			printf("no tile %d/%d/%d\n", DENSE_Z, DENSE_X, DENSE_Y);
			return 1;
		}
		Rmap_get_stats(map, &stats);
		decode[i] = stats.decode_ms;
		prepare[i] = stats.prepare_ms;
	}
	printf("dense tile %d/%d/%d: decode %.1f ms, style + triangulate %.1f ms (medians of %d)\n",
		DENSE_Z, DENSE_X, DENSE_Y, median(decode, PREPARES), median(prepare, PREPARES), PREPARES);

	/* every tile */
	Rmap_set_style_json(map, style, -1, NULL);
	for (line = strtok(list, "\n"); line; line = strtok(NULL, "\n")) {
		int z, x, y;
		if (sscanf(line, "%d %d %d", &z, &x, &y) == 3 && load_tile(map, data, z, x, y)) tiles++;
	}
	Rmap_get_stats(map, &stats);
	printf("%d tiles: decode %.1f ms, style + triangulate %.1f ms in all\n", tiles, stats.decode_ms, stats.prepare_ms);

	/* the Loop at zoom 14, drawn again and again, panned a hundredth of a unit each frame: 800 by 600
	   (the Kotlin spike's test window) and 1200 by 800 */
	world = 512.0 * pow(2.0, 14.0);
	for (size = 0; size < 2; size++) {
		const int width = size == 0 ? 800 : 1200, height = size == 0 ? 600 : 800;
		Rwindow_set_size(window, width, height);
		Rmap_set_center(map, START_LATITUDE, START_LONGITUDE);
		Rmap_set_zoom(map, 14.0);
		Rdraw_frame();
		for (i = 0; i < FRAMES; i++) {
			Rmap_get_center(map, &latitude, &longitude);
			Rmap_set_center(map, latitude, longitude + 1e-2 / world * 360.0);
			Rdraw_frame();
			Rmap_get_stats(map, &stats);
			Rwindow_get_frame_stats(window, &frame);
			fills[i] = stats.fill_ms;
			lines[i] = stats.line_ms;
			draws[i] = stats.draw_ms;
			paints[i] = frame.paint_ms;
		}
		printf("zoom 14, %d x %d: %d tiles, %d triangles, fills %.2f ms, %d segments, lines %.2f ms, map %.2f ms, paint %.2f ms"
			" (medians of %d frames)\n", width, height, stats.tiles, stats.triangles, median(fills, FRAMES), stats.segments,
			median(lines, FRAMES), median(draws, FRAMES), median(paints, FRAMES), FRAMES);
	}

	Rshutdown();
	free(style);
	free(list);
	return 0;
}
