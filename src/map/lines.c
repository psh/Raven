/**
 * Raven - map/lines.c
 * What a line layer's paint does to its lines' shapes, in part:
 * line-dasharray, dashes and gaps in line widths, the pattern starting again
 * at each line's start; line-gap-width, the line drawn twice, either side of a
 * gap that wide. Both depend on the line's width on screen, so the widget
 * cuts them for the zoom drawn, and keeps them while it holds.
 */

#include "map.h"

#include <math.h>

typedef struct rvn_points_t {
	float* xy;
	uint32_t count, cap;
} rvn_points_t;

static void rvn_points_add(rvn_points_t* p, float x, float y) {
	RVN_ARRAY_RESERVE(p->xy, p->cap, (p->count + 1) * 2);
	p->xy[p->count * 2] = x;
	p->xy[p->count * 2 + 1] = y;
	p->count++;
}

/* the end of the line starting at point i: the NaN after it, or the end */
static uint32_t rvn_line_end(const float* xy, uint32_t i, uint32_t count) {
	while (i < count && !isnan(xy[i * 2])) i++;
	return i;
}

float* rvn_map_dash(const float* xy, uint32_t point_count, const float* pattern, int pattern_count, uint32_t* out_count) {
	rvn_points_t out = { NULL, 0, 0 };
	/* where the dash being drawn starts in out, and how long it is so far: an empty one is dropped */
	int64_t dash_start = -1;
	float dash_length = 0.0f;
	uint32_t i = 0;
#define BEGIN(px, py) do { dash_start = out.count; dash_length = 0.0f; rvn_points_add(&out, (px), (py)); } while (0)
#define FINISH() do { \
		if (dash_start >= 0) { \
			if (dash_length > 0.0f) rvn_points_add(&out, NAN, NAN); else out.count = (uint32_t)dash_start; \
			dash_start = -1; \
		} \
	} while (0)
	while (i < point_count) {
		uint32_t end = rvn_line_end(xy, i, point_count), j;
		int phase = 0;
		float left = pattern[0], px = xy[i * 2], py = xy[i * 2 + 1];
		if (left > 0.0f) BEGIN(px, py);
		for (j = i + 1; j < end; j++) {
			float x = xy[j * 2], y = xy[j * 2 + 1];
			float length = hypotf(x - px, y - py), sx = px, sy = py;
			while (length > 0.0f) {
				float step = fminf(left, length), t = step / length;
				sx += (x - sx) * t;
				sy += (y - sy) * t;
				length -= step;
				left -= step;
				if (dash_start >= 0) dash_length += step;
				if (left <= 0.0f) {
					/* the end of this dash or gap, here: the next starts */
					if (phase % 2 == 0 && dash_start >= 0) {
						rvn_points_add(&out, sx, sy);
						FINISH();
					}
					phase = (phase + 1) % pattern_count;
					left = pattern[phase];
					if (phase % 2 == 0 && left > 0.0f) BEGIN(sx, sy);
				}
			}
			if (dash_start >= 0) rvn_points_add(&out, x, y);
			px = x;
			py = y;
		}
		FINISH();
		i = end + 1;
	}
#undef BEGIN
#undef FINISH
	*out_count = out.count;
	return out.xy;
}

/* the unit normal to the left of a segment, y down; R_FALSE for an empty one */
static int rvn_normal(float x0, float y0, float x1, float y1, float* nx, float* ny) {
	float dx = x1 - x0, dy = y1 - y0, length = hypotf(dx, dy);
	if (length <= 0.0f) return R_FALSE;
	*nx = dy / length;
	*ny = -dx / length;
	return R_TRUE;
}

float* rvn_map_offset(const float* xy, uint32_t point_count, float distance, uint32_t* out_count) {
	rvn_points_t out = { NULL, 0, 0 };
	uint32_t i = 0;
	while (i < point_count) {
		uint32_t end = rvn_line_end(xy, i, point_count), k;
		for (k = i; k < end; k++) {
			float x = xy[k * 2], y = xy[k * 2 + 1], bx = 0.0f, by = 0.0f, ax = 0.0f, ay = 0.0f, nx = 0.0f, ny = 0.0f;
			int before = k > i && rvn_normal(xy[k * 2 - 2], xy[k * 2 - 1], x, y, &bx, &by);
			int after = k + 1 < end && rvn_normal(x, y, xy[k * 2 + 2], xy[k * 2 + 3], &ax, &ay);
			if (before && after) {
				/* the mitre: the bisector n1 + n2 over 1 + n1 . n2 keeps the point a unit from both
				   segments; its length, sqrt(2 / (1 + n1 . n2)), is capped at 4 */
				float denominator = fmaxf(1.0f + bx * ax + by * ay, 1.0f / 8.0f);
				nx = (bx + ax) / denominator;
				ny = (by + ay) / denominator;
			} else if (before) {
				nx = bx;
				ny = by;
			} else if (after) {
				nx = ax;
				ny = ay;
			}
			rvn_points_add(&out, x + nx * distance, y + ny * distance);
		}
		rvn_points_add(&out, NAN, NAN);
		i = end + 1;
	}
	*out_count = out.count;
	return out.xy;
}
