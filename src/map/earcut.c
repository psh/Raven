/**
 * Raven - map/earcut.c
 * A port of Mapbox's earcut (github.com/mapbox/earcut, ISC licence), the
 * polygon triangulation MapLibre uses: ear clipping on a linked list of the
 * outer ring, with holes bridged into it first, and two fallbacks for rings
 * that intersect themselves. The z-order hash that speeds ear tests up on
 * large polygons is left out.
 */

#include "map.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

typedef struct rvn_map_earcut_node_t {
	uint32_t i;
	float x, y;
	struct rvn_map_earcut_node_t* prev;
	struct rvn_map_earcut_node_t* next;
	int steiner;
} rvn_node_t;

#define RVN_EARCUT_BLOCK 4096

typedef struct rvn_map_earcut_block_t {
	struct rvn_map_earcut_block_t* next;
	rvn_node_t nodes[RVN_EARCUT_BLOCK];
} rvn_block_t;

static rvn_node_t* rvn_earcut_new_node(rvn_map_earcut_t* e, uint32_t i, float x, float y) {
	rvn_node_t* p;
	if (!e->block || e->block_used == RVN_EARCUT_BLOCK) {
		rvn_block_t* next = e->block ? e->block->next : e->blocks;
		if (!next) {
			next = (rvn_block_t*)rvn_alloc(sizeof(rvn_block_t));
			next->next = NULL;
			if (e->block) e->block->next = next;
			else e->blocks = next;
		}
		e->block = next;
		e->block_used = 0;
	}
	p = &e->block->nodes[e->block_used++];
	p->i = i;
	p->x = x;
	p->y = y;
	p->prev = p->next = NULL;
	p->steiner = 0;
	return p;
}

static void rvn_earcut_add(rvn_map_earcut_t* e, uint32_t a, uint32_t b, uint32_t c) {
	RVN_ARRAY_RESERVE(e->indices, e->index_cap, e->index_count + 3);
	e->indices[e->index_count++] = a;
	e->indices[e->index_count++] = b;
	e->indices[e->index_count++] = c;
}

static float rvn_area(const rvn_node_t* p, const rvn_node_t* q, const rvn_node_t* r) {
	return (q->y - p->y) * (r->x - q->x) - (q->x - p->x) * (r->y - q->y);
}

static int rvn_equals(const rvn_node_t* a, const rvn_node_t* b) {
	return a->x == b->x && a->y == b->y;
}

static rvn_node_t* rvn_insert_node(rvn_map_earcut_t* e, uint32_t i, float x, float y, rvn_node_t* last) {
	rvn_node_t* p = rvn_earcut_new_node(e, i, x, y);
	if (!last) {
		p->prev = p;
		p->next = p;
	} else {
		p->next = last->next;
		p->prev = last;
		last->next->prev = p;
		last->next = p;
	}
	return p;
}

static void rvn_remove_node(rvn_node_t* p) {
	p->next->prev = p->prev;
	p->prev->next = p->next;
}

static float rvn_signed_area(const float* data, uint32_t start, uint32_t end) {
	float sum = 0.0f;
	uint32_t i, j = end - 2;
	for (i = start; i < end; i += 2) {
		sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1]);
		j = i;
	}
	return sum;
}

static rvn_node_t* rvn_linked_list(rvn_map_earcut_t* e, const float* data, uint32_t start, uint32_t end, int clockwise) {
	rvn_node_t* last = NULL;
	if (end <= start) return NULL;
	if (clockwise == (rvn_signed_area(data, start, end) > 0)) {
		uint32_t i;
		for (i = start; i < end; i += 2) last = rvn_insert_node(e, i / 2, data[i], data[i + 1], last);
	} else {
		uint32_t i = end;
		while (i > start) {
			i -= 2;
			last = rvn_insert_node(e, i / 2, data[i], data[i + 1], last);
		}
	}
	if (last && rvn_equals(last, last->next)) {
		rvn_remove_node(last);
		last = last->next;
	}
	return last;
}

static rvn_node_t* rvn_filter_points(rvn_node_t* start, rvn_node_t* end) {
	rvn_node_t* p = start;
	int again;
	if (!end) end = start;
	do {
		again = 0;
		if (!p->steiner && (rvn_equals(p, p->next) || rvn_area(p->prev, p, p->next) == 0.0f)) {
			rvn_remove_node(p);
			p = end = p->prev;
			if (p == p->next) break;
			again = 1;
		} else {
			p = p->next;
		}
	} while (again || p != end);
	return end;
}

static int rvn_point_in_triangle(float ax, float ay, float bx, float by, float cx, float cy, float px, float py) {
	return (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
		(ax - px) * (by - py) >= (bx - px) * (ay - py) &&
		(bx - px) * (cy - py) >= (cx - px) * (by - py);
}

static int rvn_is_ear(const rvn_node_t* ear) {
	const rvn_node_t* a = ear->prev;
	const rvn_node_t* b = ear;
	const rvn_node_t* c = ear->next;
	const rvn_node_t* p;
	float x0, y0, x1, y1;
	if (rvn_area(a, b, c) >= 0) return 0;
	x0 = fminf(a->x, fminf(b->x, c->x));
	y0 = fminf(a->y, fminf(b->y, c->y));
	x1 = fmaxf(a->x, fmaxf(b->x, c->x));
	y1 = fmaxf(a->y, fmaxf(b->y, c->y));
	for (p = c->next; p != a; p = p->next) {
		if (p->x >= x0 && p->x <= x1 && p->y >= y0 && p->y <= y1 &&
			rvn_point_in_triangle(a->x, a->y, b->x, b->y, c->x, c->y, p->x, p->y) && rvn_area(p->prev, p, p->next) >= 0) return 0;
	}
	return 1;
}

static int rvn_sign(float n) {
	return n > 0 ? 1 : n < 0 ? -1 : 0;
}

static int rvn_on_segment(const rvn_node_t* p, const rvn_node_t* q, const rvn_node_t* r) {
	return q->x <= fmaxf(p->x, r->x) && q->x >= fminf(p->x, r->x) && q->y <= fmaxf(p->y, r->y) && q->y >= fminf(p->y, r->y);
}

static int rvn_intersects(const rvn_node_t* p1, const rvn_node_t* q1, const rvn_node_t* p2, const rvn_node_t* q2) {
	int o1 = rvn_sign(rvn_area(p1, q1, p2));
	int o2 = rvn_sign(rvn_area(p1, q1, q2));
	int o3 = rvn_sign(rvn_area(p2, q2, p1));
	int o4 = rvn_sign(rvn_area(p2, q2, q1));
	if (o1 != o2 && o3 != o4) return 1;
	if (o1 == 0 && rvn_on_segment(p1, p2, q1)) return 1;
	if (o2 == 0 && rvn_on_segment(p1, q2, q1)) return 1;
	if (o3 == 0 && rvn_on_segment(p2, p1, q2)) return 1;
	if (o4 == 0 && rvn_on_segment(p2, q1, q2)) return 1;
	return 0;
}

static int rvn_locally_inside(const rvn_node_t* a, const rvn_node_t* b) {
	return rvn_area(a->prev, a, a->next) < 0 ?
		rvn_area(a, b, a->next) >= 0 && rvn_area(a, a->prev, b) >= 0 :
		rvn_area(a, b, a->prev) < 0 || rvn_area(a, a->next, b) < 0;
}

static int rvn_intersects_polygon(const rvn_node_t* a, const rvn_node_t* b) {
	const rvn_node_t* p = a;
	do {
		if (p->i != a->i && p->next->i != a->i && p->i != b->i && p->next->i != b->i && rvn_intersects(p, p->next, a, b)) return 1;
		p = p->next;
	} while (p != a);
	return 0;
}

static int rvn_middle_inside(const rvn_node_t* a, const rvn_node_t* b) {
	const rvn_node_t* p = a;
	int inside = 0;
	float px = (a->x + b->x) / 2, py = (a->y + b->y) / 2;
	do {
		if (((p->y > py) != (p->next->y > py)) && p->next->y != p->y &&
			(px < (p->next->x - p->x) * (py - p->y) / (p->next->y - p->y) + p->x)) inside = !inside;
		p = p->next;
	} while (p != a);
	return inside;
}

static int rvn_is_valid_diagonal(const rvn_node_t* a, const rvn_node_t* b) {
	return a->next->i != b->i && a->prev->i != b->i && !rvn_intersects_polygon(a, b) &&
		((rvn_locally_inside(a, b) && rvn_locally_inside(b, a) && rvn_middle_inside(a, b) &&
			(rvn_area(a->prev, a, b->prev) != 0.0f || rvn_area(a, b->prev, b) != 0.0f)) ||
		(rvn_equals(a, b) && rvn_area(a->prev, a, a->next) > 0 && rvn_area(b->prev, b, b->next) > 0));
}

static rvn_node_t* rvn_split_polygon(rvn_map_earcut_t* e, rvn_node_t* a, rvn_node_t* b) {
	rvn_node_t* a2 = rvn_earcut_new_node(e, a->i, a->x, a->y);
	rvn_node_t* b2 = rvn_earcut_new_node(e, b->i, b->x, b->y);
	rvn_node_t* an = a->next;
	rvn_node_t* bp = b->prev;
	a->next = b;
	b->prev = a;
	a2->next = an;
	an->prev = a2;
	b2->next = a2;
	a2->prev = b2;
	bp->next = b2;
	b2->prev = bp;
	return b2;
}

static void rvn_earcut_linked(rvn_map_earcut_t* e, rvn_node_t* ear, int pass);

static rvn_node_t* rvn_cure_local_intersections(rvn_map_earcut_t* e, rvn_node_t* start) {
	rvn_node_t* p = start;
	do {
		rvn_node_t* a = p->prev;
		rvn_node_t* b = p->next->next;
		if (!rvn_equals(a, b) && rvn_intersects(a, p, p->next, b) && rvn_locally_inside(a, b) && rvn_locally_inside(b, a)) {
			rvn_earcut_add(e, a->i, p->i, b->i);
			rvn_remove_node(p);
			rvn_remove_node(p->next);
			p = start = b;
		}
		p = p->next;
	} while (p != start);
	return rvn_filter_points(p, NULL);
}

static void rvn_split_earcut(rvn_map_earcut_t* e, rvn_node_t* start) {
	rvn_node_t* a = start;
	do {
		rvn_node_t* b = a->next->next;
		while (b != a->prev) {
			if (a->i != b->i && rvn_is_valid_diagonal(a, b)) {
				rvn_node_t* c = rvn_split_polygon(e, a, b);
				a = rvn_filter_points(a, a->next);
				c = rvn_filter_points(c, c->next);
				rvn_earcut_linked(e, a, 0);
				rvn_earcut_linked(e, c, 0);
				return;
			}
			b = b->next;
		}
		a = a->next;
	} while (a != start);
}

static void rvn_earcut_linked(rvn_map_earcut_t* e, rvn_node_t* ear, int pass) {
	rvn_node_t* stop = ear;
	while (ear->prev != ear->next) {
		rvn_node_t* prev = ear->prev;
		rvn_node_t* next = ear->next;
		if (rvn_is_ear(ear)) {
			rvn_earcut_add(e, prev->i, ear->i, next->i);
			rvn_remove_node(ear);
			ear = stop = next->next;
			continue;
		}
		ear = next;
		if (ear == stop) {
			if (pass == 0) rvn_earcut_linked(e, rvn_filter_points(ear, NULL), 1);
			else if (pass == 1) rvn_earcut_linked(e, rvn_cure_local_intersections(e, rvn_filter_points(ear, NULL)), 2);
			else rvn_split_earcut(e, ear);
			break;
		}
	}
}

static rvn_node_t* rvn_leftmost(rvn_node_t* start) {
	rvn_node_t* p = start;
	rvn_node_t* leftmost = start;
	do {
		if (p->x < leftmost->x || (p->x == leftmost->x && p->y < leftmost->y)) leftmost = p;
		p = p->next;
	} while (p != start);
	return leftmost;
}

static int rvn_sector_contains_sector(const rvn_node_t* m, const rvn_node_t* p) {
	return rvn_area(m->prev, m, p->prev) < 0 && rvn_area(p->next, m, m->next) < 0;
}

static rvn_node_t* rvn_find_hole_bridge(rvn_node_t* hole, rvn_node_t* outer) {
	rvn_node_t* p = outer;
	rvn_node_t* m = NULL;
	rvn_node_t* stop;
	float hx = hole->x, hy = hole->y, qx = -INFINITY, mx, my, tan_min = INFINITY;
	/* the segment left of the hole's leftmost point, at its height, nearest to it */
	do {
		if (hy <= p->y && hy >= p->next->y && p->next->y != p->y) {
			float x = p->x + (hy - p->y) * (p->next->x - p->x) / (p->next->y - p->y);
			if (x <= hx && x > qx) {
				qx = x;
				m = p->x < p->next->x ? p : p->next;
				if (x == hx) return m;
			}
		}
		p = p->next;
	} while (p != outer);
	if (!m) return NULL;
	/* of the points inside the triangle between the hole and the segment, the one with the
	   smallest angle to the hole, so the bridge crosses nothing */
	stop = m;
	mx = m->x;
	my = m->y;
	p = m;
	do {
		if (hx >= p->x && p->x >= mx && hx != p->x &&
			rvn_point_in_triangle(hy < my ? hx : qx, hy, mx, my, hy < my ? qx : hx, hy, p->x, p->y)) {
			float tan = fabsf(hy - p->y) / (hx - p->x);
			if (rvn_locally_inside(p, hole) &&
				(tan < tan_min || (tan == tan_min && (p->x > m->x || (p->x == m->x && rvn_sector_contains_sector(m, p)))))) {
				m = p;
				tan_min = tan;
			}
		}
		p = p->next;
	} while (p != stop);
	return m;
}

static rvn_node_t* rvn_eliminate_hole(rvn_map_earcut_t* e, rvn_node_t* hole, rvn_node_t* outer) {
	rvn_node_t* bridge = rvn_find_hole_bridge(hole, outer);
	rvn_node_t* reverse;
	if (!bridge) return outer;
	reverse = rvn_split_polygon(e, bridge, hole);
	rvn_filter_points(reverse, reverse->next);
	return rvn_filter_points(bridge, bridge->next);
}

static int rvn_compare_x(const void* a, const void* b) {
	const rvn_node_t* p = *(const rvn_node_t* const*)a;
	const rvn_node_t* q = *(const rvn_node_t* const*)b;
	if (p->x != q->x) return p->x < q->x ? -1 : 1;
	if (p->y != q->y) return p->y < q->y ? -1 : 1;
	return 0;
}

static rvn_node_t* rvn_eliminate_holes(rvn_map_earcut_t* e, const float* data, uint32_t length, const uint32_t* holes, uint32_t hole_count, rvn_node_t* outer) {
	uint32_t i, queued = 0;
	RVN_ARRAY_RESERVE(e->queue, e->queue_cap, hole_count);
	for (i = 0; i < hole_count; i++) {
		uint32_t start = holes[i] * 2;
		uint32_t end = i + 1 < hole_count ? holes[i + 1] * 2 : length;
		rvn_node_t* list = rvn_linked_list(e, data, start, end, 0);
		if (!list) continue;
		if (list == list->next) list->steiner = 1;
		e->queue[queued++] = rvn_leftmost(list);
	}
	qsort(e->queue, queued, sizeof(rvn_node_t*), rvn_compare_x);
	for (i = 0; i < queued; i++) outer = rvn_eliminate_hole(e, e->queue[i], outer);
	return outer;
}

void rvn_map_earcut(rvn_map_earcut_t* e, const float* xy, uint32_t point_count, const uint32_t* holes, uint32_t hole_count) {
	uint32_t length = point_count * 2;
	uint32_t outer_length = hole_count ? holes[0] * 2 : length;
	rvn_node_t* outer;
	e->index_count = 0;
	e->block = e->blocks;
	e->block_used = 0;
	outer = rvn_linked_list(e, xy, 0, outer_length, 1);
	if (!outer || outer->next == outer->prev) return;
	if (hole_count) outer = rvn_eliminate_holes(e, xy, length, holes, hole_count, outer);
	rvn_earcut_linked(e, outer, 0);
}

void rvn_map_earcut_free(rvn_map_earcut_t* e) {
	rvn_block_t* b = e->blocks;
	while (b) {
		rvn_block_t* next = b->next;
		rvn_free(b);
		b = next;
	}
	rvn_free(e->queue);
	rvn_free(e->indices);
	memset(e, 0, sizeof(*e));
}
