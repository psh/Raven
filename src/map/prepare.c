/**
 * Raven - map/prepare.c
 * A tile decoded, filtered by the style at its zoom and triangulated: one
 * bucket per style layer, in the tile's unit square.
 */

#include "map.h"
#include "../core/thread.h"

#include <math.h>
#include <string.h>

typedef struct rvn_floats_t {
	float* data;
	uint32_t count, cap;
} rvn_floats_t;

static float* rvn_floats_push(rvn_floats_t* f, uint32_t n) {
	float* at;
	RVN_ARRAY_RESERVE(f->data, f->cap, f->count + n);
	at = f->data + f->count;
	f->count += n;
	return at;
}

/* A ring scaled, its first point repeated at the end if closed, then a NaN pair; skipped under two points. */
static uint32_t rvn_add_line(rvn_floats_t* lines, const float* xy, uint32_t count, float scale, int closed) {
	uint32_t total = count + (closed ? 1u : 0u), i;
	float* at;
	if (count == 0 || total < 2) return 0;
	at = rvn_floats_push(lines, (total + 1) * 2);
	for (i = 0; i < count * 2; i++) at[i] = xy[i] * scale;
	if (closed) {
		at[count * 2] = xy[0] * scale;
		at[count * 2 + 1] = xy[1] * scale;
	}
	at[total * 2] = NAN;
	at[total * 2 + 1] = NAN;
	return total - 1;
}

/* The triangles of a polygon's rings, each exterior (positive area) with the holes that follow it. */
static void rvn_triangulate(rvn_floats_t* fills, const rvn_map_rings_t* rings, float scale, rvn_map_earcut_t* earcut,
	rvn_floats_t* coords, uint32_t** holes, uint32_t* hole_cap) {
	uint32_t i = 0;
	while (i < rings->ring_count) {
		const float* ring = rings->xy + rings->starts[i] * 2;
		uint32_t points = rings->starts[i + 1] - rings->starts[i], hole_count = 0, k;
		float* out;
		if (rvn_map_ring_area(ring, points) <= 0) {
			i++;
			continue;
		}
		coords->count = 0;
		memcpy(rvn_floats_push(coords, points * 2), ring, sizeof(float) * points * 2);
		i++;
		while (i < rings->ring_count) {
			const float* hole = rings->xy + rings->starts[i] * 2;
			uint32_t hole_points = rings->starts[i + 1] - rings->starts[i];
			if (rvn_map_ring_area(hole, hole_points) >= 0) break;
			RVN_ARRAY_RESERVE(*holes, *hole_cap, hole_count + 1);
			(*holes)[hole_count++] = coords->count / 2;
			memcpy(rvn_floats_push(coords, hole_points * 2), hole, sizeof(float) * hole_points * 2);
			i++;
		}
		rvn_map_earcut(earcut, coords->data, coords->count / 2, *holes, hole_count);
		out = rvn_floats_push(fills, earcut->index_count * 2);
		for (k = 0; k < earcut->index_count; k++) {
			uint32_t v = earcut->indices[k];
			out[k * 2] = coords->data[v * 2] * scale;
			out[k * 2 + 1] = coords->data[v * 2 + 1] * scale;
		}
	}
}

int rvn_map_prepare(rvn_map_prepared_t* tile, const rvn_map_style_t* style, int z, int x, int y,
	const uint8_t* bytes, size_t length, int deepest) {
	rvn_arena_t arena;
	rvn_map_tile_data_t data;
	rvn_map_rings_t rings;
	rvn_map_earcut_t earcut;
	rvn_floats_t fills = { NULL, 0, 0 }, lines = { NULL, 0, 0 }, coords = { NULL, 0, 0 };
	uint32_t* holes = NULL;
	uint32_t hole_cap = 0, l;
	double start = rvn_clock_seconds(), decoded;
	double zoom = (double)z;

	memset(tile, 0, sizeof(*tile));
	tile->z = z;
	tile->x = x;
	tile->y = y;
	tile->bytes = length;
	rvn_arena_init(&arena, 256 * 1024);
	if (!rvn_map_tile_decode(&arena, bytes, length, &data)) {
		rvn_arena_free(&arena);
		return R_FALSE;
	}
	decoded = rvn_clock_seconds();
	memset(&rings, 0, sizeof(rings));
	memset(&earcut, 0, sizeof(earcut));
	tile->bucket_count = style->layer_count;
	tile->buckets = (rvn_map_bucket_t*)rvn_calloc(sizeof(rvn_map_bucket_t) * (style->layer_count ? style->layer_count : 1));
	tile->shapes = (rvn_map_shape_t*)rvn_calloc(sizeof(rvn_map_shape_t) * RVN_MAP_SHAPES * (style->layer_count ? style->layer_count : 1));

	for (l = 0; l < style->layer_count; l++) {
		const rvn_map_style_layer_t* style_layer = &style->layers[l];
		const rvn_map_layer_t* layer = rvn_map_tile_layer(&data, style_layer->source_layer);
		rvn_map_bucket_t* bucket = &tile->buckets[l];
		rvn_map_context_t context;
		float scale;
		uint32_t f, segments = 0;
		if (!layer) continue;
		if ((zoom + 1 <= style_layer->min_zoom && !deepest) || zoom >= style_layer->max_zoom) continue;
		scale = 1.0f / (float)layer->extent;
		fills.count = 0;
		lines.count = 0;
		memset(&context, 0, sizeof(context));
		context.zoom = zoom;
		context.layer = layer;
		context.scratch = &arena;
		for (f = 0; f < layer->feature_count; f++) {
			const rvn_map_feature_t* feature = &layer->features[f];
			uint32_t r;
			if (feature->type == RVN_MAP_POINT || feature->type == RVN_MAP_UNKNOWN) continue;
			context.feature = feature;
			if (!rvn_map_accepts(style_layer->filter, &context)) continue;
			if (!rvn_map_feature_rings(feature, &rings)) continue;
			if (style_layer->is_fill) {
				if (feature->type != RVN_MAP_POLYGON) continue;
				rvn_triangulate(&fills, &rings, scale, &earcut, &coords, &holes, &hole_cap);
				/* the rings, closed, for the outline that smooths the mesh's aliased edges */
				if (style_layer->antialias) {
					for (r = 0; r < rings.ring_count; r++) {
						segments += rvn_add_line(&lines, rings.xy + rings.starts[r] * 2, rings.starts[r + 1] - rings.starts[r], scale, R_TRUE);
					}
				}
			} else {
				for (r = 0; r < rings.ring_count; r++) {
					segments += rvn_add_line(&lines, rings.xy + rings.starts[r] * 2, rings.starts[r + 1] - rings.starts[r], scale,
						feature->type == RVN_MAP_POLYGON);
				}
			}
		}
		if (fills.count || lines.count) {
			if (fills.count) {
				bucket->fills = (float*)rvn_alloc(sizeof(float) * fills.count);
				memcpy(bucket->fills, fills.data, sizeof(float) * fills.count);
				bucket->triangle_count = fills.count / 6;
			}
			if (lines.count) {
				bucket->lines = (float*)rvn_alloc(sizeof(float) * lines.count);
				memcpy(bucket->lines, lines.data, sizeof(float) * lines.count);
				bucket->line_point_count = lines.count / 2;
			}
			bucket->segment_count = segments;
			tile->triangle_count += bucket->triangle_count;
			tile->segment_count += segments;
		}
	}
	rvn_free(fills.data);
	rvn_free(lines.data);
	rvn_free(coords.data);
	rvn_free(holes);
	rvn_map_rings_free(&rings);
	rvn_map_earcut_free(&earcut);
	rvn_arena_free(&arena);
	tile->decode_ms = (decoded - start) * 1000.0;
	tile->prepare_ms = (rvn_clock_seconds() - decoded) * 1000.0;
	return R_TRUE;
}

void rvn_map_prepared_free(rvn_map_prepared_t* tile) {
	uint32_t i;
	for (i = 0; i < tile->bucket_count; i++) {
		rvn_free(tile->buckets[i].fills);
		rvn_free(tile->buckets[i].lines);
	}
	if (tile->shapes) {
		for (i = 0; i < tile->bucket_count * RVN_MAP_SHAPES; i++) rvn_free(tile->shapes[i].xy);
	}
	rvn_free(tile->buckets);
	rvn_free(tile->shapes);
	memset(tile, 0, sizeof(*tile));
}
