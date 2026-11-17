/**
 * Raven - widgets/map.c
 * Map: Mapbox Vector Tiles drawn as a MapLibre style says (map/), dragged to
 * pan and scrolled to zoom. The application hands it the style and the
 * tiles' bytes; the map decodes, filters and triangulates each tile once, on
 * a worker thread, then draws every tile's layer in two calls, its fills and
 * its lines.
 *
 * Where it looks is Web Mercator's unit square (0 to 1 across, from the
 * antimeridian and the north edge) and a fractional zoom: at zoom z the
 * world is 512 * 2^z units across, the tile size MapLibre's styles are
 * written for. The world repeats east and west: past the antimeridian the
 * same tiles are drawn again, a world over, and the centre is kept within 0
 * to 1 across as the map pans.
 *
 * The tile store: each frame asks for the tiles it needs and their ancestors
 * down to the nearest ready one, then cancels those asked for that it no
 * longer needs; past the capacity, the tiles used least recently go, except
 * those of the last two frames; a tile that failed is asked for again after
 * a while.
 *
 * A tile's bytes go through two tasks: from any thread, Rmap_tile_loaded
 * posts them to the thread of the interface, which starts their preparation
 * on a worker with the style of the moment (held by a reference); the
 * worker posts the prepared tile back. Either task finds the map by its
 * handle, so a map destroyed in between only frees what arrives.
 */

#include "common.h"

#ifdef RAVEN_WITH_MAP

#include "../map/map.h"
#include "../core/thread.h"

#include <math.h>
#include <stdint.h>
#include <string.h>

#define RVN_MAP_TILE_SIZE 512.0
/* the dashes a line-dasharray may hold */
#define RVN_MAP_MAX_DASHES 16
/* how long a tile that failed waits before it is asked for again */
#define RVN_MAP_RETRY_SECONDS 10.0
/* how long the camera rests before the labels are placed again where it stopped */
#define RVN_MAP_SETTLE_SECONDS 0.2

enum {
	RVN_MAP_TILE_PENDING,
	RVN_MAP_TILE_READY,
	RVN_MAP_TILE_FAILED
};

typedef struct rvn_map_entry_t {
	int z, x, y;
	int state;
	/** Asked for by the map, so cancelled when no longer wanted (a tile handed in unasked never is). */
	int requested;
	/** The frame that last wanted it, for eviction. */
	uint64_t last_used;
	double failed_at;
	rvn_map_prepared_t tile;
} rvn_map_entry_t;

/** A request or a cancellation waiting to be told to the application, outside painting. */
typedef struct rvn_map_message_t {
	int z, x, y;
	int cancel;
} rvn_map_message_t;

typedef struct rvn_map_t {
	rvn_widget_t base;
	rvn_map_style_t* style;
	/** Changes with the style: a tile prepared for another is dropped when it lands. */
	int style_version;
	rvn_map_entry_t* entries;
	uint32_t entry_count, entry_cap;
	int tile_min, tile_max;
	int capacity;
	uint64_t clock;
	RMapTileRequestCallback request;
	RMapTileCancelCallback cancel;
	void* source_user;
	rvn_map_message_t* messages;
	uint32_t message_count, message_cap;
	int dispatch_posted;
	int failures, requests;
	/** The camera. */
	double center_x, center_y, zoom;
	double min_zoom, max_zoom;
	int dragging;
	float drag_x, drag_y;
	char* attribution;
	RMapStats stats;
	RMapCameraCallback camera_callback;
	void* camera_user;
	/** The tiles of a frame, reused: indices into entries, the copy of the world each is drawn in, then the tiles themselves for the labels. */
	uint32_t* drawn;
	uint32_t drawn_cap;
	int* drawn_copies;
	uint32_t drawn_copies_cap;
	const rvn_map_prepared_t** drawn_tiles;
	uint32_t drawn_tiles_cap;
	/** The labels, their icons, and what they were placed for: bumped as tiles land and the style or sprite changes. */
	rvn_map_labels_t* labels;
	rvn_map_sprite_t* sprite;
	int label_version;
	/** When the camera last moved, and the timer that draws again once it has rested. */
	double moved_at;
	Rid settle_timer;
} rvn_map_t;

/** Tile bytes on their way: posted to the interface, prepared on a worker, posted back. */
typedef struct rvn_map_arrival_t {
	Rid map;
	int z, x, y;
	int failed;
	uint8_t* bytes;
	size_t length;
	rvn_map_style_t* style;
	int style_version;
	int deepest;
	int ok;
	rvn_map_prepared_t tile;
} rvn_map_arrival_t;

static double rvn_map_clampd(double value, double low, double high) {
	return value < low ? low : value > high ? high : value;
}

/* a / b rounded down, and what is left, from 0 to b - 1, for b above 0: tile columns west of the antimeridian */
static int rvn_map_floor_div(int a, int b) {
	int q = a / b;
	return a % b < 0 ? q - 1 : q;
}

static int rvn_map_mod(int a, int b) {
	return a - rvn_map_floor_div(a, b) * b;
}

/* x across Web Mercator's unit square, brought within 0 to 1: the same meridian, round the world */
static double rvn_map_wrap(double x) {
	return x - floor(x);
}

static double rvn_map_world(const rvn_map_t* map) {
	return RVN_MAP_TILE_SIZE * pow(2.0, map->zoom);
}

/** The map behind a handle, or NULL if it is gone (tasks hold handles, never pointers). */
static rvn_map_t* rvn_map_from(Rid id) {
	rvn_widget_t* widget = rvn_widget_get(id);
	return widget && widget->cls && widget->cls->type == R_WIDGET_MAP ? (rvn_map_t*)widget : NULL;
}

/* ---------------------------------------------------------------------------
 *  The tile store
 * ------------------------------------------------------------------------- */

static int rvn_map_entry_index(const rvn_map_t* map, int z, int x, int y) {
	uint32_t i;
	for (i = 0; i < map->entry_count; i++) {
		const rvn_map_entry_t* e = &map->entries[i];
		if (e->z == z && e->x == x && e->y == y) return (int)i;
	}
	return -1;
}

static rvn_map_entry_t* rvn_map_entry_add(rvn_map_t* map, int z, int x, int y) {
	rvn_map_entry_t* e;
	RVN_ARRAY_RESERVE(map->entries, map->entry_cap, map->entry_count + 1);
	e = &map->entries[map->entry_count++];
	memset(e, 0, sizeof(*e));
	e->z = z;
	e->x = x;
	e->y = y;
	e->state = RVN_MAP_TILE_PENDING;
	e->last_used = map->clock;
	return e;
}

static void rvn_map_entry_remove(rvn_map_t* map, uint32_t index) {
	if (map->entries[index].state == RVN_MAP_TILE_READY) rvn_map_prepared_free(&map->entries[index].tile);
	map->entries[index] = map->entries[--map->entry_count];
}

static void rvn_map_dispatch(void* user_data);

/* Queues a request or a cancellation, told to the application by a task: never from inside painting. */
static void rvn_map_queue(rvn_map_t* map, int z, int x, int y, int cancel) {
	rvn_map_message_t* m;
	if (cancel && !map->cancel) return;
	RVN_ARRAY_RESERVE(map->messages, map->message_cap, map->message_count + 1);
	m = &map->messages[map->message_count++];
	m->z = z;
	m->x = x;
	m->y = y;
	m->cancel = cancel;
	if (!map->dispatch_posted) {
		map->dispatch_posted = R_TRUE;
		Rtask_post(rvn_map_dispatch, (void*)(uintptr_t)map->base.id);
	}
}

static void rvn_map_dispatch(void* user_data) {
	Rid id = (Rid)(uintptr_t)user_data;
	rvn_map_t* map = rvn_map_from(id);
	rvn_map_message_t* messages;
	RMapTileRequestCallback request;
	RMapTileCancelCallback cancel;
	void* user;
	uint32_t count, i;
	if (!map) return;
	/* the callbacks may ask for more, or hand tiles in: they queue anew */
	messages = map->messages;
	count = map->message_count;
	map->messages = NULL;
	map->message_count = map->message_cap = 0;
	map->dispatch_posted = R_FALSE;
	request = map->request;
	cancel = map->cancel;
	user = map->source_user;
	for (i = 0; i < count; i++) {
		if (messages[i].cancel && cancel) cancel(id, messages[i].z, messages[i].x, messages[i].y, user);
		else if (!messages[i].cancel && request) request(id, messages[i].z, messages[i].x, messages[i].y, user);
		/* a callback may have destroyed the map */
		if (rvn_map_from(id) != map) break;
	}
	rvn_free(messages);
}

/* Forgets every tile, cancelling those asked for and not answered. */
static void rvn_map_forget_tiles(rvn_map_t* map) {
	uint32_t i;
	for (i = 0; i < map->entry_count; i++) {
		rvn_map_entry_t* e = &map->entries[i];
		if (e->state == RVN_MAP_TILE_PENDING && e->requested) rvn_map_queue(map, e->z, e->x, e->y, R_TRUE);
		if (e->state == RVN_MAP_TILE_READY) rvn_map_prepared_free(&e->tile);
	}
	map->entry_count = 0;
}

/* Past the capacity, the ready tiles used least recently go, but never those of the last two frames. */
static void rvn_map_evict(rvn_map_t* map) {
	int ready = 0;
	uint32_t i;
	for (i = 0; i < map->entry_count; i++) ready += map->entries[i].state == RVN_MAP_TILE_READY;
	while (ready > map->capacity) {
		int oldest = -1;
		for (i = 0; i < map->entry_count; i++) {
			const rvn_map_entry_t* e = &map->entries[i];
			if (e->state != RVN_MAP_TILE_READY || e->last_used + 1 >= map->clock) continue;
			if (oldest < 0 || e->last_used < map->entries[oldest].last_used) oldest = (int)i;
		}
		if (oldest < 0) break;
		rvn_map_entry_remove(map, (uint32_t)oldest);
		ready--;
	}
}

/* The tile if it is ready; otherwise asks for it (once, and again a while after it failed) and returns -1. */
static int rvn_map_ready(rvn_map_t* map, int z, int x, int y) {
	int index = rvn_map_entry_index(map, z, x, y);
	rvn_map_entry_t* e = index >= 0 ? &map->entries[index] : NULL;
	if (e) {
		e->last_used = map->clock;
		if (e->state == RVN_MAP_TILE_READY) return index;
		if (e->state == RVN_MAP_TILE_PENDING) return -1;
		if (rvn_clock_seconds() - e->failed_at < RVN_MAP_RETRY_SECONDS) return -1;
	}
	if (!map->request || z < map->tile_min || z > map->tile_max) return -1;
	if (!e) e = rvn_map_entry_add(map, z, x, y);
	e->state = RVN_MAP_TILE_PENDING;
	e->requested = R_TRUE;
	map->requests++;
	rvn_map_queue(map, z, x, y, R_FALSE);
	return -1;
}

/* The tile if it is ready, else the nearest ancestor that is, down to the source's minimum zoom. */
static int rvn_map_best(rvn_map_t* map, int z, int x, int y) {
	while (z >= map->tile_min) {
		int index = rvn_map_ready(map, z, x, y);
		if (index >= 0) return index;
		if (z == 0) break;
		z--;
		x /= 2;
		y /= 2;
	}
	return -1;
}

/* Whether a tile is one of the wanted ones (tiles of tile_zoom from x0, y0 to x1, y1, the columns
   counted round the world: x0 and x1 may be past its edges) or an ancestor of one. */
static int rvn_map_kept(int z, int x, int y, int tile_zoom, int x0, int y0, int x1, int y1) {
	int shift, lo, hi, across;
	if (z > tile_zoom) return R_FALSE;
	shift = tile_zoom - z;
	if (y < (y0 >> shift) || y > (y1 >> shift)) return R_FALSE;
	lo = rvn_map_floor_div(x0, 1 << shift);
	hi = rvn_map_floor_div(x1, 1 << shift);
	across = 1 << z;
	return hi - lo + 1 >= across || rvn_map_mod(x - lo, across) <= hi - lo;
}

/* Cancels the tiles asked for and not answered that the frame no longer wants. */
static void rvn_map_retain(rvn_map_t* map, int tile_zoom, int x0, int y0, int x1, int y1) {
	uint32_t i = 0;
	while (i < map->entry_count) {
		rvn_map_entry_t* e = &map->entries[i];
		if (e->state == RVN_MAP_TILE_PENDING && e->requested && !rvn_map_kept(e->z, e->x, e->y, tile_zoom, x0, y0, x1, y1)) {
			rvn_map_queue(map, e->z, e->x, e->y, R_TRUE);
			rvn_map_entry_remove(map, i);
			continue;
		}
		i++;
	}
}

static void rvn_map_prepared_task(void* user_data);

/* On a worker: decodes and prepares the tile, then posts it back. */
static void rvn_map_prepare_job(void* user_data) {
	rvn_map_arrival_t* a = (rvn_map_arrival_t*)user_data;
	a->ok = rvn_map_prepare(&a->tile, a->style, a->z, a->x, a->y, a->bytes, a->length, a->deepest);
	rvn_free(a->bytes);
	a->bytes = NULL;
	Rtask_post(rvn_map_prepared_task, a);
}

static void rvn_map_arrival_free(rvn_map_arrival_t* a) {
	if (a->ok) rvn_map_prepared_free(&a->tile);
	rvn_map_style_release(a->style);
	rvn_free(a->bytes);
	rvn_free(a);
}

/* On the interface: a prepared tile lands, if its map, its style and its entry are still there. */
static void rvn_map_prepared_task(void* user_data) {
	rvn_map_arrival_t* a = (rvn_map_arrival_t*)user_data;
	rvn_map_t* map = rvn_map_from(a->map);
	int index;
	rvn_map_entry_t* e;
	if (!map || a->style_version != map->style_version || (index = rvn_map_entry_index(map, a->z, a->x, a->y)) < 0) {
		/* the map is gone, its style changed, or the tile was cancelled meanwhile */
		rvn_map_arrival_free(a);
		return;
	}
	e = &map->entries[index];
	if (!a->ok) {
		e->state = RVN_MAP_TILE_FAILED;
		e->failed_at = rvn_clock_seconds();
		map->failures++;
	} else {
		if (e->state == RVN_MAP_TILE_READY) rvn_map_prepared_free(&e->tile);
		e->tile = a->tile;
		e->state = RVN_MAP_TILE_READY;
		e->last_used = map->clock;
		a->ok = R_FALSE;
		map->label_version++;
		rvn_map_evict(map);
	}
	rvn_map_arrival_free(a);
	rvn_widget_invalidate_paint(&map->base);
}

/* On the interface: bytes handed in (or a failure) start their preparation with the style of the moment. */
static void rvn_map_arrival_task(void* user_data) {
	rvn_map_arrival_t* a = (rvn_map_arrival_t*)user_data;
	rvn_map_t* map = rvn_map_from(a->map);
	int index;
	rvn_map_entry_t* e;
	if (!map || !map->style) {
		rvn_map_arrival_free(a);
		return;
	}
	index = rvn_map_entry_index(map, a->z, a->x, a->y);
	if (a->failed) {
		if (index >= 0 && map->entries[index].state == RVN_MAP_TILE_PENDING) {
			map->entries[index].state = RVN_MAP_TILE_FAILED;
			map->entries[index].failed_at = rvn_clock_seconds();
			map->failures++;
		}
		rvn_map_arrival_free(a);
		return;
	}
	/* handed in unasked: kept as it lands (a ready tile handed in again is replaced when it does) */
	e = index >= 0 ? &map->entries[index] : rvn_map_entry_add(map, a->z, a->x, a->y);
	if (e->state == RVN_MAP_TILE_FAILED) e->state = RVN_MAP_TILE_PENDING;
	a->style = map->style;
	rvn_map_style_retain(a->style);
	a->style_version = map->style_version;
	a->deepest = a->z >= map->tile_max;
	Rtask_run_async(rvn_map_prepare_job, a);
}

/* ---------------------------------------------------------------------------
 *  The widget
 * ------------------------------------------------------------------------- */

static void rvn_map_init(rvn_widget_t* widget) {
	rvn_map_t* map = (rvn_map_t*)widget;
	map->tile_min = 0;
	map->tile_max = 14;
	map->capacity = 160;
	map->center_x = 0.5;
	map->center_y = 0.5;
	map->zoom = 2.0;
	map->min_zoom = 2.0;
	map->max_zoom = 18.0;
	map->moved_at = -1.0;
	map->labels = rvn_map_labels_create();
}

static void rvn_map_destroy(rvn_widget_t* widget) {
	rvn_map_t* map = (rvn_map_t*)widget;
	uint32_t i;
	for (i = 0; i < map->entry_count; i++) {
		if (map->entries[i].state == RVN_MAP_TILE_READY) rvn_map_prepared_free(&map->entries[i].tile);
	}
	rvn_free(map->entries);
	rvn_free(map->messages);
	rvn_free(map->drawn);
	rvn_free(map->drawn_copies);
	rvn_free(map->drawn_tiles);
	rvn_map_labels_free(map->labels);
	rvn_map_sprite_free(map->sprite);
	if (map->settle_timer && Rtimer_is_valid(map->settle_timer)) Rtimer_destroy(map->settle_timer);
	rvn_map_style_release(map->style);
	rvn_free(map->attribution);
}

static rvn_vec2_t rvn_map_measure(rvn_widget_t* widget) {
	RVN_UNUSED(widget);
	return rvn_vec2(256.0f, 256.0f);
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
	const rvn_map_style_t* style = map->style;
	const float width = widget->rect.w, height = widget->rect.h;
	const double zoom = map->zoom, world = rvn_map_world(map);
	double start = rvn_clock_seconds(), fill_time = 0.0, line_time = 0.0;
	double left, right, top, bottom;
	uint32_t drawn = 0, l, i;
	int tile_zoom, n, tx, ty, tx0, tx1, ty0, ty1, triangles = 0, segments = 0;

	rvn_paint_push_clip(painter, rvn_rect(0.0f, 0.0f, width, height));
	if (!style) {
		Rdraw_rect(painter, 0.0f, 0.0f, width, height, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f);
		rvn_map_paint_attribution(map, painter);
		rvn_paint_pop_clip(painter);
		return;
	}
	Rdraw_rect(painter, 0.0f, 0.0f, width, height, 0.0f, style->background[0], style->background[1], style->background[2], style->background[3]);

	/* the tiles at the zoom drawn from that cover the view; below the source's lowest zoom, none (as
	   MapLibre has it: its tiles would be a crowd of specks, millions of them far out) */
	tile_zoom = (int)floor(zoom);
	if (tile_zoom < map->tile_min) {
		rvn_map_paint_attribution(map, painter);
		rvn_paint_pop_clip(painter);
		map->clock++;
		rvn_map_retain(map, map->tile_min, 1, 1, 0, 0);
		memset(&map->stats, 0, sizeof(map->stats));
		return;
	}
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
	/* columns past the antimeridian are the world's next copy, east or west: they stay */
	if (ty0 < 0) ty0 = 0;
	if (ty1 >= n) ty1 = n - 1;

	/* a new frame of use: tiles asked for that it no longer wants stop, then each wanted tile, or the
	   nearest ancestor ready (the tiles missing on the way are asked for) */
	map->clock++;
	rvn_map_retain(map, tile_zoom, tx0, ty0, tx1, ty1);
	for (tx = tx0; tx <= tx1; tx++) {
		for (ty = ty0; ty <= ty1; ty++) {
			int copy = rvn_map_floor_div(tx, n);
			int index = rvn_map_best(map, tile_zoom, tx - copy * n, ty);
			uint32_t k;
			if (index < 0) continue;
			for (k = 0; k < drawn && (map->drawn[k] != (uint32_t)index || map->drawn_copies[k] != copy); k++) {}
			if (k < drawn) continue;
			RVN_ARRAY_RESERVE(map->drawn, map->drawn_cap, drawn + 1);
			RVN_ARRAY_RESERVE(map->drawn_copies, map->drawn_copies_cap, drawn + 1);
			map->drawn_copies[drawn] = copy;
			map->drawn[drawn++] = (uint32_t)index;
		}
	}

	for (l = 0; l < style->layer_count; l++) {
		const rvn_map_style_layer_t* layer = &style->layers[l];
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
			rvn_map_prepared_t* tile = &map->entries[map->drawn[i]].tile;
			const rvn_map_bucket_t* bucket;
			double tiles_across = (double)(1 << tile->z);
			float scale, ox, oy;
			double part;
			if (l >= tile->bucket_count) continue;
			bucket = &tile->buckets[l];
			if (!bucket->triangle_count && !bucket->line_point_count) continue;
			scale = (float)(world / tiles_across);
			ox = (float)((tile->x / tiles_across + map->drawn_copies[i] - map->center_x) * world + width / 2.0);
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
	if (style->symbol_count) {
		/* the labels over everything, placed again once the camera has rested somewhere new */
		rvn_map_view_t view;
		rvn_map_label_stats_t label_stats;
		int settled = map->moved_at < 0.0 || rvn_clock_seconds() - map->moved_at >= RVN_MAP_SETTLE_SECONDS;
		RVN_ARRAY_RESERVE(map->drawn_tiles, map->drawn_tiles_cap, drawn + 1);
		for (i = 0; i < drawn; i++) map->drawn_tiles[i] = &map->entries[map->drawn[i]].tile;
		view.center_x = map->center_x;
		view.center_y = map->center_y;
		view.zoom = zoom;
		view.width = width;
		view.height = height;
		memset(&label_stats, 0, sizeof(label_stats));
		label_stats.placed = map->stats.labels_placed;
		label_stats.candidates = map->stats.label_candidates;
		label_stats.icons = map->stats.icons;
		label_stats.place_ms = map->stats.place_ms;
		rvn_map_labels_draw(map->labels, painter, style, map->drawn_tiles, map->drawn_copies, drawn, &view, map->label_version, settled, map->sprite, &label_stats);
		map->stats.labels_placed = label_stats.placed;
		map->stats.label_candidates = label_stats.candidates;
		map->stats.icons = label_stats.icons;
		map->stats.labels_ms = label_stats.ms;
		map->stats.place_ms = label_stats.place_ms;
		map->stats.labels_reused = label_stats.reused;
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

static void rvn_map_settled(Rid timer, void* user_data) {
	rvn_map_t* map = rvn_map_from((Rid)(uintptr_t)user_data);
	if (!map || map->settle_timer != timer) return;
	map->settle_timer = 0;
	rvn_widget_invalidate_paint(&map->base);
}

/* The camera moved: the labels move with it, and are placed again once it has rested a moment. */
static void rvn_map_moved(rvn_map_t* map) {
	map->moved_at = rvn_clock_seconds();
	if (map->settle_timer && Rtimer_is_valid(map->settle_timer)) Rtimer_destroy(map->settle_timer);
	map->settle_timer = Rtimer_create(RVN_MAP_SETTLE_SECONDS, R_FALSE, rvn_map_settled, (void*)(uintptr_t)map->base.id);
	rvn_widget_invalidate_paint(&map->base);
}

static void rvn_map_camera_changed(rvn_widget_t* widget) {
	rvn_map_t* map = (rvn_map_t*)widget;
	rvn_map_moved(map);
	if (map->camera_callback) map->camera_callback(widget->id, map->camera_user);
}

static void rvn_map_pan_by(rvn_map_t* map, float dx, float dy) {
	double world = rvn_map_world(map);
	map->center_x = rvn_map_wrap(map->center_x - dx / world);
	map->center_y -= dy / world;
}

/* Zooms by delta keeping the point under (x, y) where it is. */
static int rvn_map_zoom_at(rvn_map_t* map, double delta, float x, float y) {
	const float width = map->base.rect.w, height = map->base.rect.h;
	double before = rvn_map_world(map), wx, wy, zoom;
	zoom = rvn_map_clampd(map->zoom + delta, map->min_zoom, map->max_zoom);
	if (zoom == map->zoom) return R_FALSE;
	wx = map->center_x + (x - width / 2.0) / before;
	wy = map->center_y + (y - height / 2.0) / before;
	map->zoom = zoom;
	map->center_x = rvn_map_wrap(wx - (x - width / 2.0) / rvn_map_world(map));
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
	/* (the paint asks for tiles and cuts lines: on the thread of the interface) */
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
	rvn_map_style_t* style;
	RVN_WIDGET(widget, id, R_WIDGET_MAP, R_FALSE);
	map = (rvn_map_t*)widget;
	if (!json) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_set_style_json: no JSON");
		return R_FALSE;
	}
	style = rvn_map_style_create(json, length < 0 ? strlen(json) : (size_t)length, source);
	if (!style) {
		rvn_error(R_ERROR_INVALID_FILE_FORMAT, R_SEVERITY_ERROR, "Rmap_set_style_json: the style is not valid JSON");
		return R_FALSE;
	}
	rvn_map_forget_tiles(map);
	rvn_map_style_release(map->style);
	map->style = style;
	map->style_version++;
	map->label_version++;
	rvn_widget_invalidate_paint(widget);
	return R_TRUE;
}

int Rmap_set_sprite(Rid id, const char* index_json, int index_length, const void* png, size_t png_length) {
	rvn_map_t* map;
	rvn_map_sprite_t* sprite = NULL;
	RVN_WIDGET(widget, id, R_WIDGET_MAP, R_FALSE);
	map = (rvn_map_t*)widget;
	if (index_json) {
		if (!png || !png_length) {
			rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_set_sprite: no PNG");
			return R_FALSE;
		}
		sprite = rvn_map_sprite_create(index_json, index_length < 0 ? strlen(index_json) : (size_t)index_length, png, png_length);
		if (!sprite) {
			rvn_error(R_ERROR_INVALID_FILE_FORMAT, R_SEVERITY_ERROR, "Rmap_set_sprite: the index or the image can't be read");
			return R_FALSE;
		}
	}
	rvn_map_sprite_free(map->sprite);
	map->sprite = sprite;
	map->label_version++;
	rvn_widget_invalidate_paint(widget);
	return R_TRUE;
}

static int rvn_map_check_range(const char* function, int min_zoom, int max_zoom) {
	if (min_zoom < 0 || max_zoom < min_zoom || max_zoom > 30) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "%s: invalid range %d to %d", function, min_zoom, max_zoom);
		return R_FALSE;
	}
	return R_TRUE;
}

void Rmap_set_tile_zoom_range(Rid id, int min_zoom, int max_zoom) {
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (!rvn_map_check_range(__func__, min_zoom, max_zoom)) return;
	((rvn_map_t*)widget)->tile_min = min_zoom;
	((rvn_map_t*)widget)->tile_max = max_zoom;
	rvn_widget_invalidate_paint(widget);
}

void Rmap_set_tile_source(Rid id, int min_zoom, int max_zoom, RMapTileRequestCallback request, RMapTileCancelCallback cancel, void* user_data) {
	rvn_map_t* map;
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (!rvn_map_check_range(__func__, min_zoom, max_zoom)) return;
	map = (rvn_map_t*)widget;
	map->tile_min = min_zoom;
	map->tile_max = max_zoom;
	map->request = request;
	map->cancel = cancel;
	map->source_user = user_data;
	rvn_widget_invalidate_paint(widget);
}

/* Any thread: no widget is touched here, only the handle travels. */
static int rvn_map_post_arrival(const char* function, Rid id, int z, int x, int y, const void* bytes, size_t length, int failed) {
	rvn_map_arrival_t* a;
	if (z < 0 || z > 30 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z) || (!bytes && length)) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "%s: invalid tile %d/%d/%d", function, z, x, y);
		return R_FALSE;
	}
	a = (rvn_map_arrival_t*)rvn_calloc(sizeof(rvn_map_arrival_t));
	a->map = id;
	a->z = z;
	a->x = x;
	a->y = y;
	a->failed = failed;
	if (length) {
		a->bytes = (uint8_t*)rvn_alloc(length);
		memcpy(a->bytes, bytes, length);
		a->length = length;
	}
	Rtask_post(rvn_map_arrival_task, a);
	return R_TRUE;
}

int Rmap_tile_loaded(Rid id, int z, int x, int y, const void* bytes, size_t length) {
	return rvn_map_post_arrival(__func__, id, z, x, y, bytes, length, R_FALSE);
}

void Rmap_tile_failed(Rid id, int z, int x, int y) {
	rvn_map_post_arrival(__func__, id, z, x, y, NULL, 0, R_TRUE);
}

void Rmap_set_tile_capacity(Rid id, int capacity) {
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	if (capacity < 1) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rmap_set_tile_capacity: %d", capacity);
		return;
	}
	((rvn_map_t*)widget)->capacity = capacity;
	rvn_map_evict((rvn_map_t*)widget);
}

void Rmap_set_center(Rid id, double latitude, double longitude) {
	rvn_map_t* map;
	const double pi = 3.14159265358979323846;
	RVN_WIDGET_VOID(widget, id, R_WIDGET_MAP);
	map = (rvn_map_t*)widget;
	latitude = rvn_map_clampd(latitude, -85.0511287798, 85.0511287798);
	map->center_x = rvn_map_wrap((longitude + 180.0) / 360.0);
	map->center_y = (1.0 - asinh(tan(latitude * pi / 180.0)) / pi) / 2.0;
	rvn_map_moved(map);
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
	rvn_map_moved((rvn_map_t*)widget);
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
	stats->prepared_tiles = stats->pending_tiles = 0;
	stats->decode_ms = stats->prepare_ms = 0.0;
	stats->memory_bytes = 0;
	for (i = 0; i < map->entry_count; i++) {
		const rvn_map_entry_t* e = &map->entries[i];
		if (e->state == RVN_MAP_TILE_PENDING) stats->pending_tiles++;
		if (e->state != RVN_MAP_TILE_READY) continue;
		stats->prepared_tiles++;
		stats->decode_ms += e->tile.decode_ms;
		stats->prepare_ms += e->tile.prepare_ms;
		stats->memory_bytes += rvn_map_prepared_memory(&e->tile);
	}
	stats->failed_tiles = map->failures;
	stats->requested_tiles = map->requests;
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
int Rmap_set_sprite(Rid id, const char* index_json, int index_length, const void* png, size_t png_length) {
	RVN_UNUSED(id); RVN_UNUSED(index_json); RVN_UNUSED(index_length); RVN_UNUSED(png); RVN_UNUSED(png_length);
	rvn_map_missing(__func__);
	return R_FALSE;
}
void Rmap_set_tile_zoom_range(Rid id, int min_zoom, int max_zoom) {
	RVN_UNUSED(id); RVN_UNUSED(min_zoom); RVN_UNUSED(max_zoom);
	rvn_map_missing(__func__);
}
void Rmap_set_tile_source(Rid id, int min_zoom, int max_zoom, RMapTileRequestCallback request, RMapTileCancelCallback cancel, void* user_data) {
	RVN_UNUSED(id); RVN_UNUSED(min_zoom); RVN_UNUSED(max_zoom); RVN_UNUSED(request); RVN_UNUSED(cancel); RVN_UNUSED(user_data);
	rvn_map_missing(__func__);
}
int Rmap_tile_loaded(Rid id, int z, int x, int y, const void* bytes, size_t length) {
	RVN_UNUSED(id); RVN_UNUSED(z); RVN_UNUSED(x); RVN_UNUSED(y); RVN_UNUSED(bytes); RVN_UNUSED(length);
	rvn_map_missing(__func__);
	return R_FALSE;
}
void Rmap_tile_failed(Rid id, int z, int x, int y) {
	RVN_UNUSED(id); RVN_UNUSED(z); RVN_UNUSED(x); RVN_UNUSED(y);
	rvn_map_missing(__func__);
}
void Rmap_set_tile_capacity(Rid id, int capacity) {
	RVN_UNUSED(id); RVN_UNUSED(capacity);
	rvn_map_missing(__func__);
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
