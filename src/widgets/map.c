/**
 * Raven - widgets/map.c
 * Map: Mapbox Vector Tiles drawn as a MapLibre style says (map/), dragged to
 * pan and scrolled to zoom. The application hands it the style and the
 * tiles' bytes; the map decodes, filters and triangulates each tile once,
 * then draws every tile's layer in two calls, its fills and its lines.
 *
 * Where it looks is Web Mercator's unit square (0 to 1 across, from the
 * antimeridian and the north edge) and a fractional zoom: at zoom z the
 * world is 512 * 2^z units across, the tile size MapLibre's styles are
 * written for.
 */

#include "common.h"

#ifdef RAVEN_WITH_MAP

#include "../map/map.h"
#include "../core/thread.h"

#include <math.h>
#include <string.h>

#define RVN_MAP_TILE_SIZE 512.0
/* the dashes a line-dasharray may hold */
#define RVN_MAP_MAX_DASHES 16

typedef struct rvn_map_t {
	rvn_widget_t base;
	rvn_map_style_t style;
	int has_style;
	rvn_map_prepared_t* tiles;
	uint32_t tile_count, tile_cap;
	int tile_min, tile_max;
	/** The camera. */
	double center_x, center_y, zoom;
	double min_zoom, max_zoom;
	int dragging;
	float drag_x, drag_y;
	char* attribution;
	RMapStats stats;
	RMapCameraCallback camera_callback;
	void* camera_user;
	/** The tiles of a frame, reused. */
	const rvn_map_prepared_t** drawn;
	uint32_t drawn_cap;
} rvn_map_t;

static double rvn_map_clampd(double value, double low, double high) {
	return value < low ? low : value > high ? high : value;
}

static double rvn_map_world(const rvn_map_t* map) {
	return RVN_MAP_TILE_SIZE * pow(2.0, map->zoom);
}

static void rvn_map_forget_tiles(rvn_map_t* map) {
	uint32_t i;
	for (i = 0; i < map->tile_count; i++) rvn_map_prepared_free(&map->tiles[i]);
	map->tile_count = 0;
}

static void rvn_map_init(rvn_widget_t* widget) {
	rvn_map_t* map = (rvn_map_t*)widget;
	map->tile_min = 0;
	map->tile_max = 14;
	map->center_x = 0.5;
	map->center_y = 0.5;
	map->zoom = 2.0;
	map->min_zoom = 2.0;
	map->max_zoom = 18.0;
}

static void rvn_map_destroy(rvn_widget_t* widget) {
	rvn_map_t* map = (rvn_map_t*)widget;
	rvn_map_forget_tiles(map);
	rvn_free(map->tiles);
	rvn_free(map->drawn);
	if (map->has_style) rvn_map_style_free(&map->style);
	rvn_free(map->attribution);
}

static rvn_vec2_t rvn_map_measure(rvn_widget_t* widget) {
	RVN_UNUSED(widget);
	return rvn_vec2(256.0f, 256.0f);
}

static const rvn_map_prepared_t* rvn_map_find(const rvn_map_t* map, int z, int x, int y) {
	uint32_t i;
	for (i = 0; i < map->tile_count; i++) {
		const rvn_map_prepared_t* t = &map->tiles[i];
		if (t->z == z && t->x == x && t->y == y) return t;
	}
	return NULL;
}

/* The tile if it is held, else the nearest ancestor that is, down to the source's minimum zoom. */
static const rvn_map_prepared_t* rvn_map_best(const rvn_map_t* map, int z, int x, int y) {
	while (z >= map->tile_min) {
		const rvn_map_prepared_t* t = rvn_map_find(map, z, x, y);
		if (t) return t;
		if (z == 0) break;
		z--;
		x /= 2;
		y /= 2;
	}
	return NULL;
}

/* The tile's lines dashed and offset for a width, kept while the width holds within a hundredth. */
static const float* rvn_map_shape(rvn_map_prepared_t* tile, uint32_t layer, int slot, const float* pattern, int pattern_count,
	float offset_widths, float width, float scale, uint32_t* count) {
	rvn_map_bucket_t* bucket = &tile->buckets[layer];
	rvn_map_shape_t* shape;
	float unit;
	if (!pattern_count && offset_widths == 0.0f) {
		*count = bucket->line_point_count;
		return bucket->lines;
	}
	/* a line width in tile units: the shapes' scale */
	unit = rvn_maxf(width, 1.0f) / scale;
	shape = &tile->shapes[layer * RVN_MAP_SHAPES + slot];
	if (shape->xy && shape->offset == offset_widths && fabsf(shape->unit - unit) <= shape->unit * 0.01f) {
		*count = shape->point_count;
		return shape->xy;
	}
	rvn_free(shape->xy);
	{
		const float* xy = bucket->lines;
		uint32_t n = bucket->line_point_count;
		float* offset = NULL;
		if (offset_widths != 0.0f) {
			offset = rvn_map_offset(xy, n, offset_widths * unit, &n);
			xy = offset;
		}
		if (pattern_count) {
			float scaled[RVN_MAP_MAX_DASHES];
			int i;
			for (i = 0; i < pattern_count; i++) scaled[i] = pattern[i] * unit;
			shape->xy = rvn_map_dash(xy, n, scaled, pattern_count, &shape->point_count);
			rvn_free(offset);
		} else {
			shape->xy = offset;
			shape->point_count = n;
		}
	}
	shape->unit = unit;
	shape->offset = offset_widths;
	*count = shape->point_count;
	return shape->xy;
}

static void rvn_map_paint_attribution(rvn_map_t* map, RPainter* painter) {
	const float size = 11.0f, pad_x = 6.0f, pad_y = 2.0f, margin = 6.0f;
	rvn_widget_t* widget = &map->base;
	float w, h, x, y;
	if (!map->attribution || !map->attribution[0]) return;
	w = rvn_widget_text_width(widget, R_INVALID_ID, size, map->attribution) + pad_x * 2.0f;
	h = rvn_widget_line_height(widget, R_INVALID_ID, size) + pad_y * 2.0f;
	x = widget->rect.w - margin - w;
	y = widget->rect.h - margin - h;
	rvn_paint_box(painter, rvn_rect(x, y, w, h), 4.0f, rvn_rgba(1.0f, 1.0f, 1.0f, 0.8f), 0.0f, 0);
	rvn_paint_text(painter, R_INVALID_ID, size, x + pad_x, y + pad_y, map->attribution, -1, rvn_rgba(0.2f, 0.2f, 0.2f, 1.0f));
}

static void rvn_map_paint(rvn_widget_t* widget, RPainter* painter) {
	rvn_map_t* map = (rvn_map_t*)widget;
	const float width = widget->rect.w, height = widget->rect.h;
	const double zoom = map->zoom, world = rvn_map_world(map);
	double start = rvn_clock_seconds(), fill_time = 0.0, line_time = 0.0;
	double left, right, top, bottom;
	uint32_t drawn = 0, l, i;
	int tile_zoom, n, tx, ty, tx0, tx1, ty0, ty1, triangles = 0, segments = 0;
	const float* bg = map->style.background;

	rvn_paint_push_clip(painter, rvn_rect(0.0f, 0.0f, width, height));
	Rdraw_rect(painter, 0.0f, 0.0f, width, height, 0.0f, bg[0], bg[1], bg[2], map->has_style ? bg[3] : 1.0f);
	if (!map->has_style) {
		rvn_map_paint_attribution(map, painter);
		rvn_paint_pop_clip(painter);
		return;
	}

	/* the tiles at the zoom drawn from that cover the view, or the nearest ancestors held */
	tile_zoom = (int)floor(zoom);
	if (tile_zoom < map->tile_min) tile_zoom = map->tile_min;
	if (tile_zoom > map->tile_max) tile_zoom = map->tile_max;
	n = 1 << tile_zoom;
	left = map->center_x - width / 2.0 / world;
	right = map->center_x + width / 2.0 / world;
	top = map->center_y - height / 2.0 / world;
	bottom = map->center_y + height / 2.0 / world;
	tx0 = (int)floor(left * n);
	tx1 = (int)floor(right * n);
	ty0 = (int)floor(top * n);
	ty1 = (int)floor(bottom * n);
	for (tx = tx0; tx <= tx1; tx++) {
		for (ty = ty0; ty <= ty1; ty++) {
			const rvn_map_prepared_t* tile;
			uint32_t k;
			if (tx < 0 || tx >= n || ty < 0 || ty >= n) continue;
			tile = rvn_map_best(map, tile_zoom, tx, ty);
			if (!tile) continue;
			for (k = 0; k < drawn && map->drawn[k] != tile; k++) {}
			if (k < drawn) continue;
			RVN_ARRAY_RESERVE(map->drawn, map->drawn_cap, drawn + 1);
			map->drawn[drawn++] = tile;
		}
	}

	for (l = 0; l < map->style.layer_count; l++) {
		const rvn_map_style_layer_t* layer = &map->style.layers[l];
		float color[4], outline[4], line_width = 0.0f, pattern[RVN_MAP_MAX_DASHES], gap = 0.0f;
		int fill = layer->is_fill, has_outline = R_FALSE, pattern_count = 0, cap = R_LINE_CAP_BUTT;
		if (zoom < layer->min_zoom || zoom >= layer->max_zoom) continue;
		if (!rvn_map_layer_color(layer, zoom, color) || color[3] <= 0.0f) continue;
		if (fill) {
			has_outline = layer->antialias && rvn_map_layer_outline_color(layer, zoom, outline);
		} else {
			line_width = (float)rvn_map_layer_number(layer, "line-width", zoom, 1.0);
			if (!rvn_map_layer_dashes(layer, zoom, pattern, &pattern_count, RVN_MAP_MAX_DASHES)) pattern_count = 0;
			gap = (float)rvn_map_layer_number(layer, "line-gap-width", zoom, 0.0);
			cap = rvn_map_layer_line_cap(layer, zoom);
		}
		for (i = 0; i < drawn; i++) {
			rvn_map_prepared_t* tile = (rvn_map_prepared_t*)map->drawn[i];
			const rvn_map_bucket_t* bucket = &tile->buckets[l];
			double tiles_across = (double)(1 << tile->z);
			float scale, ox, oy;
			double part;
			if (!bucket->triangle_count && !bucket->line_point_count) continue;
			scale = (float)(world / tiles_across);
			ox = (float)((tile->x / tiles_across - map->center_x) * world + width / 2.0);
			oy = (float)((tile->y / tiles_across - map->center_y) * world + height / 2.0);
			/* tiles carry a buffer past their edges: clipped to the tile, a translucent fill isn't drawn twice there */
			Rdraw_push_clip_rect(painter, ox, oy, scale, scale);
			part = rvn_clock_seconds();
			if (fill) {
				Rdraw_triangles(painter, bucket->fills, (int)bucket->triangle_count, ox, oy, scale, color[0], color[1], color[2], color[3]);
				/* one device pixel (Raven's thinnest line) over the mesh's aliased edges */
				if (has_outline && bucket->line_point_count) {
					Rdraw_polyline(painter, bucket->lines, (int)bucket->line_point_count, ox, oy, scale, 0.0f, R_LINE_CAP_ROUND,
						outline[0], outline[1], outline[2], outline[3]);
				}
				triangles += (int)bucket->triangle_count;
				fill_time += rvn_clock_seconds() - part;
			} else if (bucket->line_point_count) {
				const float* xy;
				uint32_t count;
				if (gap > 0.0f) {
					/* each side's middle is half the gap and half its own width from the line's */
					float side = gap / 2.0f / rvn_maxf(line_width, 1.0f) + 0.5f;
					xy = rvn_map_shape(tile, l, 1, pattern, pattern_count, side, line_width, scale, &count);
					Rdraw_polyline(painter, xy, (int)count, ox, oy, scale, line_width, (RLineCap)cap, color[0], color[1], color[2], color[3]);
					xy = rvn_map_shape(tile, l, 2, pattern, pattern_count, -side, line_width, scale, &count);
					Rdraw_polyline(painter, xy, (int)count, ox, oy, scale, line_width, (RLineCap)cap, color[0], color[1], color[2], color[3]);
				} else {
					xy = rvn_map_shape(tile, l, 0, pattern, pattern_count, 0.0f, line_width, scale, &count);
					Rdraw_polyline(painter, xy, (int)count, ox, oy, scale, line_width, (RLineCap)cap, color[0], color[1], color[2], color[3]);
				}
				segments += (int)bucket->segment_count;
				line_time += rvn_clock_seconds() - part;
			}
			Rdraw_pop_clip_rect(painter);
		}
	}
	rvn_map_paint_attribution(map, painter);
	rvn_paint_pop_clip(painter);

	map->stats.tiles = (int)drawn;
	map->stats.triangles = triangles;
	map->stats.segments = segments;
	map->stats.draw_ms = (rvn_clock_seconds() - start) * 1000.0;
	map->stats.fill_ms = fill_time * 1000.0;
	map->stats.line_ms = line_time * 1000.0;
}

/* ---------------------------------------------------------------------------
 *  Moving the camera
 * ------------------------------------------------------------------------- */

static void rvn_map_camera_changed(rvn_widget_t* widget) {
	rvn_map_t* map = (rvn_map_t*)widget;
	rvn_widget_invalidate_paint(widget);
	if (map->camera_callback) map->camera_callback(widget->id, map->camera_user);
}

static void rvn_map_pan_by(rvn_map_t* map, float dx, float dy) {
	double world = rvn_map_world(map);
	map->center_x -= dx / world;
	map->center_y -= dy / world;
}

/* Zooms by delta keeping the point under (x, y) where it is. */
static int rvn_map_zoom_at(rvn_map_t* map, double delta, float x, float y) {
	const float width = map->base.rect.w, height = map->base.rect.h;
	double before = rvn_map_world(map), wx, wy, zoom;
	zoom = map->zoom + delta;
	if (zoom < map->min_zoom) zoom = map->min_zoom;
	if (zoom > map->max_zoom) zoom = map->max_zoom;
	if (zoom == map->zoom) return R_FALSE;
	wx = map->center_x + (x - width / 2.0) / before;
	wy = map->center_y + (y - height / 2.0) / before;
	map->zoom = zoom;
	map->center_x = wx - (x - width / 2.0) / rvn_map_world(map);
	map->center_y = wy - (y - height / 2.0) / rvn_map_world(map);
	return R_TRUE;
}

static int rvn_map_event(rvn_widget_t* widget, const REvent* event) {
	rvn_map_t* map = (rvn_map_t*)widget;
	float x = event->local_x, y = event->local_y;
	switch (event->type) {
		case R_EVENT_MOUSE_DOWN:
			if (event->button != R_MOUSE_LEFT || map->dragging) return R_FALSE;
			map->dragging = R_TRUE;
			map->drag_x = x;
			map->drag_y = y;
			widget->cursor = R_CURSOR_MOVE;
			rvn_widget_capture_mouse(widget);
			return R_TRUE;
		case R_EVENT_MOUSE_MOVE:
			if (!map->dragging) return R_FALSE;
			rvn_map_pan_by(map, x - map->drag_x, y - map->drag_y);
			map->drag_x = x;
			map->drag_y = y;
			rvn_map_camera_changed(widget);
			return R_TRUE;
		case R_EVENT_MOUSE_UP:
			if (!map->dragging || event->button != R_MOUSE_LEFT) return R_FALSE;
			map->dragging = R_FALSE;
			widget->cursor = (RCursor)0;
			rvn_widget_release_mouse(widget);
			return R_TRUE;
		case R_EVENT_MOUSE_WHEEL:
			if (event->wheel_precise && !(event->modifiers & R_MOD_CTRL)) {
				/* a trackpad: two fingers move the map (pinch zooms) */
				rvn_map_pan_by(map, event->wheel_x * 48.0f, event->wheel_y * 48.0f);
			} else if (!rvn_map_zoom_at(map, (event->wheel_y != 0.0f ? event->wheel_y : event->wheel_x) * 0.25, x, y)) {
				return R_TRUE;
			}
			rvn_map_camera_changed(widget);
			return R_TRUE;
		default:
			return R_FALSE;
	}
}

static int rvn_map_pan(rvn_widget_t* widget, float dx, float dy, int coasting) {
	RVN_UNUSED(coasting);
	rvn_map_pan_by((rvn_map_t*)widget, dx, dy);
	rvn_map_camera_changed(widget);
	return R_TRUE;
}

static int rvn_map_zoom(rvn_widget_t* widget, float factor, float x, float y) {
	if (factor <= 0.0f || !rvn_map_zoom_at((rvn_map_t*)widget, log2((double)factor), x, y)) return R_FALSE;
	rvn_map_camera_changed(widget);
	return R_TRUE;
}

const rvn_widget_class_t rvn_class_map = {
	.type = R_WIDGET_MAP,
	.name = "Map",
	.size = sizeof(rvn_map_t),
	/* (the paint keeps the lines it cuts, and the tiles change on the thread of the interface) */
	.flags = RVN_CF_NO_CHILDREN | RVN_CF_UI_PAINT,
	.init = rvn_map_init,
	.destroy = rvn_map_destroy,
	.measure = rvn_map_measure,
	.paint = rvn_map_paint,
	.event = rvn_map_event,
	.pan = rvn_map_pan,
	.zoom = rvn_map_zoom
};

/* ---------------------------------------------------------------------------
 *  Public API
 * ------------------------------------------------------------------------- */

int Rmap_set_style_json(Rid id, const char* json, int length, const char* source) {
	rvn_map_t* map;
	rvn_map_style_t style;
	RVN_WIDGET(widget, id, R_WIDGET_MAP, R_FALSE);
	map = (rvn_map_t*)widget;
	if (!json) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_set_style_json: no JSON");
		return R_FALSE;
	}
	if (!rvn_map_style_parse(&style, json, length < 0 ? strlen(json) : (size_t)length, source)) {
		rvn_error(R_ERROR_INVALID_FILE_FORMAT, R_SEVERITY_ERROR, "Rmap_set_style_json: the style is not valid JSON");
		return R_FALSE;
	}
	rvn_map_forget_tiles(map);
	if (map->has_style) rvn_map_style_free(&map->style);
	map->style = style;
	map->has_style = R_TRUE;
	rvn_widget_invalidate_paint(widget);
	return R_TRUE;
}

void Rmap_set_tile_zoom_range(Rid id, int min_zoom, int max_zoom) {
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (min_zoom < 0 || max_zoom < min_zoom || max_zoom > 30) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_set_tile_zoom_range: invalid range %d to %d", min_zoom, max_zoom);
		return;
	}
	((rvn_map_t*)widget)->tile_min = min_zoom;
	((rvn_map_t*)widget)->tile_max = max_zoom;
	rvn_widget_invalidate_paint(widget);
}

int Rmap_tile_loaded(Rid id, int z, int x, int y, const void* bytes, size_t length) {
	rvn_map_t* map;
	rvn_map_prepared_t tile;
	uint32_t i;
	RVN_WIDGET(widget, id, R_WIDGET_MAP, R_FALSE);
	map = (rvn_map_t*)widget;
	if (z < 0 || z > 30 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z) || (!bytes && length)) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_tile_loaded: invalid tile %d/%d/%d", z, x, y);
		return R_FALSE;
	}
	if (!map->has_style) {
		rvn_error(R_ERROR_INVALID_OPERATION, R_SEVERITY_ERROR, "Rmap_tile_loaded: the map has no style");
		return R_FALSE;
	}
	if (!rvn_map_prepare(&tile, &map->style, z, x, y, (const uint8_t*)bytes, length, z >= map->tile_max)) return R_FALSE;
	for (i = 0; i < map->tile_count; i++) {
		if (map->tiles[i].z == z && map->tiles[i].x == x && map->tiles[i].y == y) {
			rvn_map_prepared_free(&map->tiles[i]);
			map->tiles[i] = tile;
			rvn_widget_invalidate_paint(widget);
			return R_TRUE;
		}
	}
	RVN_ARRAY_RESERVE(map->tiles, map->tile_cap, map->tile_count + 1);
	map->tiles[map->tile_count++] = tile;
	rvn_widget_invalidate_paint(widget);
	return R_TRUE;
}

void Rmap_set_center(Rid id, double latitude, double longitude) {
	rvn_map_t* map;
	const double pi = 3.14159265358979323846;
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	map = (rvn_map_t*)widget;
	latitude = rvn_map_clampd(latitude, -85.0511287798, 85.0511287798);
	map->center_x = (longitude + 180.0) / 360.0;
	map->center_y = (1.0 - asinh(tan(latitude * pi / 180.0)) / pi) / 2.0;
	rvn_widget_invalidate_paint(widget);
}

void Rmap_get_center(Rid id, double* latitude, double* longitude) {
	rvn_map_t* map;
	const double pi = 3.14159265358979323846;
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	map = (rvn_map_t*)widget;
	if (longitude) *longitude = map->center_x * 360.0 - 180.0;
	if (latitude) *latitude = atan(sinh(pi * (1.0 - 2.0 * map->center_y))) * 180.0 / pi;
}

void Rmap_set_zoom(Rid id, double zoom) {
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	((rvn_map_t*)widget)->zoom = rvn_map_clampd(zoom, 0.0, 30.0);
	rvn_widget_invalidate_paint(widget);
}

double Rmap_get_zoom(Rid id) {
	RVN_WIDGET(widget, id, R_WIDGET_MAP, 0.0);
	return ((rvn_map_t*)widget)->zoom;
}

void Rmap_set_zoom_range(Rid id, double min_zoom, double max_zoom) {
	rvn_map_t* map;
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (!(min_zoom >= 0.0) || !(max_zoom >= min_zoom) || max_zoom > 30.0) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_set_zoom_range: invalid range");
		return;
	}
	map = (rvn_map_t*)widget;
	map->min_zoom = min_zoom;
	map->max_zoom = max_zoom;
}

void Rmap_set_attribution(Rid id, const char* text) {
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (rvn_str_set(&((rvn_map_t*)widget)->attribution, text)) rvn_widget_invalidate_paint(widget);
}

void Rmap_get_stats(Rid id, RMapStats* stats) {
	rvn_map_t* map;
	uint32_t i;
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (!stats) return;
	map = (rvn_map_t*)widget;
	*stats = map->stats;
	stats->prepared_tiles = (int)map->tile_count;
	stats->decode_ms = stats->prepare_ms = 0.0;
	for (i = 0; i < map->tile_count; i++) {
		stats->decode_ms += map->tiles[i].decode_ms;
		stats->prepare_ms += map->tiles[i].prepare_ms;
	}
}

void Rmap_set_camera_changed_callback(Rid id, RMapCameraCallback callback, void* user_data) {
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	((rvn_map_t*)widget)->camera_callback = callback;
	((rvn_map_t*)widget)->camera_user = user_data;
}

#else /* RAVEN_WITH_MAP */

/* A library built without the map: the functions are there, and say so. */
static void rvn_map_missing(const char* function) {
	rvn_error(R_ERROR_INVALID_OPERATION, R_SEVERITY_ERROR, "%s: Raven was built without RAVEN_WITH_MAP", function);
}

int Rmap_set_style_json(Rid id, const char* json, int length, const char* source) {
	RVN_UNUSED(id); RVN_UNUSED(json); RVN_UNUSED(length); RVN_UNUSED(source);
	rvn_map_missing(__func__);
	return R_FALSE;
}
void Rmap_set_tile_zoom_range(Rid id, int min_zoom, int max_zoom) {
	RVN_UNUSED(id); RVN_UNUSED(min_zoom); RVN_UNUSED(max_zoom);
	rvn_map_missing(__func__);
}
int Rmap_tile_loaded(Rid id, int z, int x, int y, const void* bytes, size_t length) {
	RVN_UNUSED(id); RVN_UNUSED(z); RVN_UNUSED(x); RVN_UNUSED(y); RVN_UNUSED(bytes); RVN_UNUSED(length);
	rvn_map_missing(__func__);
	return R_FALSE;
}
void Rmap_set_center(Rid id, double latitude, double longitude) {
	RVN_UNUSED(id); RVN_UNUSED(latitude); RVN_UNUSED(longitude);
	rvn_map_missing(__func__);
}
void Rmap_get_center(Rid id, double* latitude, double* longitude) {
	RVN_UNUSED(id); RVN_UNUSED(latitude); RVN_UNUSED(longitude);
	rvn_map_missing(__func__);
}
void Rmap_set_zoom(Rid id, double zoom) {
	RVN_UNUSED(id); RVN_UNUSED(zoom);
	rvn_map_missing(__func__);
}
double Rmap_get_zoom(Rid id) {
	RVN_UNUSED(id);
	rvn_map_missing(__func__);
	return 0.0;
}
void Rmap_set_zoom_range(Rid id, double min_zoom, double max_zoom) {
	RVN_UNUSED(id); RVN_UNUSED(min_zoom); RVN_UNUSED(max_zoom);
	rvn_map_missing(__func__);
}
void Rmap_set_attribution(Rid id, const char* text) {
	RVN_UNUSED(id); RVN_UNUSED(text);
	rvn_map_missing(__func__);
}
void Rmap_get_stats(Rid id, RMapStats* stats) {
	RVN_UNUSED(id); RVN_UNUSED(stats);
	rvn_map_missing(__func__);
}
void Rmap_set_camera_changed_callback(Rid id, RMapCameraCallback callback, void* user_data) {
	RVN_UNUSED(id); RVN_UNUSED(callback); RVN_UNUSED(user_data);
	rvn_map_missing(__func__);
}

#endif /* RAVEN_WITH_MAP */
