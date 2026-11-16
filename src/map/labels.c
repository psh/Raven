/**
 * Raven - map/labels.c
 * Labels, as MapLibre's symbol layers place them, in part (the Kotlin spike's
 * Labels.kt): the text of text-field, at text-size, in the face text-font
 * names (regular, bold or italic: Raven's default font, with the faces it
 * derives), in text-color with a text-halo-color halo text-halo-width wide,
 * with the icon-image of the style's sprite sheet at icon-size, and kept
 * apart by collision, the layers higher in the style first. At a point, the
 * icon on it and the text wrapped at text-max-width and placed by
 * text-anchor and text-offset; along a line, every symbol-spacing: glyph by
 * glyph, each turned to the line where it stands, reading left to right,
 * and left out where the line bends more than 45 degrees between two
 * glyphs; or upright, an icon with its text (road shields); or, an icon
 * alone, turned to the line where it stands, plus icon-rotate (one-way
 * arrows). Left out: the style's own fonts, letter spacing, halo blur, and
 * fading in and out.
 *
 * The placement is kept and moves with the map, as MapLibre's labels do
 * between placements: through a pan of up to a quarter of the view or a
 * zoom of up to a quarter of a level, the text keeping its size; placed
 * again past those, or once the camera has settled somewhere else.
 */

#include "map.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#define RVN_LABEL_PI 3.14159265358979323846f
/* MapLibre's text-max-angle: the most a line may turn between two glyphs of a label */
#define RVN_LABEL_MAX_TURN (45.0f * RVN_LABEL_PI / 180.0f)
#define RVN_LABEL_CELL 64.0f
/* how far past the view the collision grid reaches: boxes further out share its edge cells */
#define RVN_LABEL_GRID_MARGIN 1024.0f

/* ---------------------------------------------------------------------------
 *  Sprite sheets
 * ------------------------------------------------------------------------- */

static int rvn_icon_compare(const void* a, const void* b) {
	return strcmp(((const rvn_map_icon_t*)a)->name, ((const rvn_map_icon_t*)b)->name);
}

rvn_map_sprite_t* rvn_map_sprite_create(const char* json, size_t json_length, const void* png, size_t png_length) {
	rvn_map_sprite_t* sprite = (rvn_map_sprite_t*)rvn_calloc(sizeof(rvn_map_sprite_t));
	rvn_map_value_t index;
	uint8_t* pixels;
	uint32_t i;
	rvn_arena_init(&sprite->arena, 16 * 1024);
	if (!rvn_map_json_parse(&sprite->arena, json, json_length, &index) || index.kind != RVN_MV_OBJECT) goto fail;
	sprite->icons = (rvn_map_icon_t*)rvn_arena_alloc(&sprite->arena, sizeof(rvn_map_icon_t) * (index.count ? index.count : 1));
	for (i = 0; i < index.count; i++) {
		const rvn_map_value_t* v = &index.as.members[i].value;
		rvn_map_icon_t* icon = &sprite->icons[sprite->icon_count];
		icon->name = index.as.members[i].key;
		icon->x = (int)rvn_map_member_number(v, "x", 0.0);
		icon->y = (int)rvn_map_member_number(v, "y", 0.0);
		icon->width = (int)rvn_map_member_number(v, "width", 0.0);
		icon->height = (int)rvn_map_member_number(v, "height", 0.0);
		icon->ratio = (float)rvn_map_member_number(v, "pixelRatio", 1.0);
		if (icon->width > 0 && icon->height > 0 && icon->ratio > 0.0f) sprite->icon_count++;
	}
	qsort(sprite->icons, sprite->icon_count, sizeof(rvn_map_icon_t), rvn_icon_compare);
	pixels = Rimage_decode(png, png_length, &sprite->width, &sprite->height);
	if (!pixels) goto fail;
	sprite->texture = Rtexture_create_from_pixels(pixels, sprite->width, sprite->height);
	Rpixels_free(pixels);
	if (sprite->texture == R_INVALID_ID) goto fail;
	return sprite;
fail:
	rvn_arena_free(&sprite->arena);
	rvn_free(sprite);
	return NULL;
}

void rvn_map_sprite_free(rvn_map_sprite_t* sprite) {
	if (!sprite) return;
	if (sprite->texture != R_INVALID_ID) Rtexture_destroy(sprite->texture);
	rvn_arena_free(&sprite->arena);
	rvn_free(sprite);
}

const rvn_map_icon_t* rvn_map_sprite_find(const rvn_map_sprite_t* sprite, const char* name) {
	rvn_map_icon_t key;
	if (!sprite || !name) return NULL;
	key.name = name;
	return (const rvn_map_icon_t*)bsearch(&key, sprite->icons, sprite->icon_count, sizeof(rvn_map_icon_t), rvn_icon_compare);
}

/* ---------------------------------------------------------------------------
 *  The labels' state
 * ------------------------------------------------------------------------- */

/* the faces of the default font a style's text-font names */
enum { RVN_FACE_REGULAR, RVN_FACE_BOLD, RVN_FACE_ITALIC, RVN_FACE_BOLD_ITALIC, RVN_FACE_COUNT };

typedef struct rvn_measure_t {
	uint64_t hash;
	int face;
	float size;
	const char* text;
	float width;
} rvn_measure_t;

typedef struct rvn_label_box_t {
	float left, top, right, bottom;
} rvn_label_box_t;

typedef struct rvn_cell_node_t {
	uint32_t box;
	int32_t next;
} rvn_cell_node_t;

/** How a layer's texts look. */
typedef struct rvn_glyph_style_t {
	float size, height;
	float color[4], halo[4];
	int has_halo;
	float halo_width, padding;
	int face;
} rvn_glyph_style_t;

/** What a layer's symbols are, at a zoom: read once a placement, not once a symbol. */
typedef struct rvn_symbol_style_t {
	uint32_t text;
	float max_width;
	const char* anchor;
	float offset[2];
	float line_height, spacing, icon_size;
	int icon_overlaps;
	/** Along a line, each glyph turned to it; else upright, at points along it. */
	int along_line, upright;
	/** Radians an icon turns, past the line it follows. */
	float icon_rotate;
} rvn_symbol_style_t;

/** What placement leaves to draw: a text (a line of a label, or a glyph along a line), or an icon. */
typedef struct rvn_label_op_t {
	float x, y, width, height, rotation;
	const char* text;
	const rvn_map_icon_t* icon;
	uint32_t style;
} rvn_label_op_t;

/** A point of a line on screen, and the line's direction there, in radians. */
typedef struct rvn_along_t {
	float x, y, angle;
} rvn_along_t;

struct rvn_map_labels_t {
	/* widths measured, by face, size and text: labels repeat across frames */
	rvn_measure_t* measures;
	uint32_t measure_cap, measure_count;
	rvn_arena_t measure_arena;
	float* height_sizes;
	float* heights;
	uint32_t height_count, height_cap;
	Rid faces[RVN_FACE_COUNT];
	int faces_ready;
	char* buffer;
	size_t buffer_cap;

	/* the last placement, and what it was made for */
	int placed_once;
	double zoom, center_x, center_y;
	float width, height;
	int version;
	rvn_label_op_t* ops;
	uint32_t op_count, op_cap;
	rvn_arena_t op_arena;
	rvn_glyph_style_t* styles;
	uint32_t style_count, style_cap;

	/* placing: the collision grid, the symbols placed, the screen lines */
	rvn_label_box_t* boxes;
	uint32_t box_count, box_cap;
	rvn_cell_node_t* nodes;
	uint32_t node_count, node_cap;
	int32_t* cells;
	uint32_t cell_cap;
	int columns, rows;
	float grid_x, grid_y;
	uint64_t* seen;
	uint32_t seen_cap, seen_count;
	float* xs;
	float* ys;
	float* cumulative;
	uint32_t line_cap;
	float* starts;
	uint32_t* offsets;
	rvn_along_t* glyphs;
	uint32_t glyph_cap;
	const rvn_map_sprite_t* sprite;
	const rvn_map_view_t* view;
	int placed, candidates, icons;
};

rvn_map_labels_t* rvn_map_labels_create(void) {
	rvn_map_labels_t* labels = (rvn_map_labels_t*)rvn_calloc(sizeof(rvn_map_labels_t));
	rvn_arena_init(&labels->measure_arena, 64 * 1024);
	rvn_arena_init(&labels->op_arena, 64 * 1024);
	return labels;
}

void rvn_map_labels_free(rvn_map_labels_t* labels) {
	if (!labels) return;
	rvn_free(labels->measures);
	rvn_arena_free(&labels->measure_arena);
	rvn_free(labels->height_sizes);
	rvn_free(labels->heights);
	rvn_free(labels->buffer);
	rvn_free(labels->ops);
	rvn_arena_free(&labels->op_arena);
	rvn_free(labels->styles);
	rvn_free(labels->boxes);
	rvn_free(labels->nodes);
	rvn_free(labels->cells);
	rvn_free(labels->seen);
	rvn_free(labels->xs);
	rvn_free(labels->ys);
	rvn_free(labels->cumulative);
	rvn_free(labels->starts);
	rvn_free(labels->offsets);
	rvn_free(labels->glyphs);
	rvn_free(labels);
}

/* ---------------------------------------------------------------------------
 *  Measuring, as Rdraw_text draws
 * ------------------------------------------------------------------------- */

static uint64_t rvn_fnv(uint64_t hash, const void* data, size_t length) {
	const uint8_t* p = (const uint8_t*)data;
	size_t i;
	for (i = 0; i < length; i++) {
		hash ^= p[i];
		hash *= 0x100000001B3ull;
	}
	return hash;
}

#define RVN_FNV_START 0xCBF29CE484222325ull

static Rid rvn_label_font(rvn_map_labels_t* labels, int face) {
	if (!labels->faces_ready) {
		Rid base = Rtheme_get_font();
		if (!Rfont_is_valid(base)) base = Rfont_get_default();
		labels->faces[RVN_FACE_REGULAR] = R_INVALID_ID;
		labels->faces[RVN_FACE_BOLD] = Rfont_get_variant(base, R_FONT_BOLD);
		labels->faces[RVN_FACE_ITALIC] = Rfont_get_variant(base, R_FONT_ITALIC);
		labels->faces[RVN_FACE_BOLD_ITALIC] = Rfont_get_variant(base, R_FONT_BOLD_ITALIC);
		labels->faces_ready = R_TRUE;
	}
	return labels->faces[face];
}

static void rvn_measure_grow(rvn_map_labels_t* labels) {
	uint32_t cap = labels->measure_cap ? labels->measure_cap * 2 : 4096, i;
	rvn_measure_t* table = (rvn_measure_t*)rvn_calloc(sizeof(rvn_measure_t) * cap);
	for (i = 0; i < labels->measure_cap; i++) {
		rvn_measure_t* m = &labels->measures[i];
		uint32_t at;
		if (!m->text) continue;
		for (at = (uint32_t)m->hash & (cap - 1); table[at].text; at = (at + 1) & (cap - 1)) {}
		table[at] = *m;
	}
	rvn_free(labels->measures);
	labels->measures = table;
	labels->measure_cap = cap;
}

/* The width of `length` bytes of text (-1: all of it) in a face at a size. */
static float rvn_label_width(rvn_map_labels_t* labels, const char* text, int length, float size, int face) {
	uint64_t hash;
	uint32_t at;
	size_t n = length < 0 ? strlen(text) : (size_t)length;
	float width = 0.0f, height = 0.0f;
	char* copy;
	if ((labels->measure_count + 1) * 10 > labels->measure_cap * 7) rvn_measure_grow(labels);
	hash = rvn_fnv(rvn_fnv(rvn_fnv(RVN_FNV_START, &face, sizeof(face)), &size, sizeof(size)), text, n);
	for (at = (uint32_t)hash & (labels->measure_cap - 1); labels->measures[at].text; at = (at + 1) & (labels->measure_cap - 1)) {
		rvn_measure_t* m = &labels->measures[at];
		if (m->hash == hash && m->face == face && m->size == size && strncmp(m->text, text, n) == 0 && m->text[n] == '\0') return m->width;
	}
	copy = rvn_arena_strndup(&labels->measure_arena, text, n);
	if (!copy) return 0.0f;
	Rfont_measure_text(rvn_label_font(labels, face), size, copy, &width, &height);
	labels->measures[at].hash = hash;
	labels->measures[at].face = face;
	labels->measures[at].size = size;
	labels->measures[at].text = copy;
	labels->measures[at].width = width;
	labels->measure_count++;
	return width;
}

/* The height of a line of text at a size. */
static float rvn_label_line_height(rvn_map_labels_t* labels, float size) {
	float width, height = 0.0f;
	uint32_t i;
	for (i = 0; i < labels->height_count; i++) if (labels->height_sizes[i] == size) return labels->heights[i];
	Rfont_measure_text(R_INVALID_ID, size, "Ag", &width, &height);
	RVN_ARRAY_RESERVE(labels->height_sizes, labels->height_cap, labels->height_count + 1);
	labels->heights = (float*)rvn_realloc(labels->heights, sizeof(float) * labels->height_cap);
	labels->height_sizes[labels->height_count] = size;
	labels->heights[labels->height_count++] = height;
	return height;
}

/* A scratch buffer of at least `size` bytes. */
static char* rvn_label_buffer(rvn_map_labels_t* labels, size_t size) {
	if (size > labels->buffer_cap) {
		labels->buffer_cap = size * 2;
		labels->buffer = (char*)rvn_realloc(labels->buffer, labels->buffer_cap);
	}
	return labels->buffer;
}

/* ---------------------------------------------------------------------------
 *  Collision: the boxes placed, in a grid of cells so a test looks only at its neighbours
 * ------------------------------------------------------------------------- */

static void rvn_grid_reset(rvn_map_labels_t* labels, float width, float height) {
	uint32_t cells, i;
	labels->grid_x = -RVN_LABEL_GRID_MARGIN;
	labels->grid_y = -RVN_LABEL_GRID_MARGIN;
	labels->columns = (int)ceilf((width + RVN_LABEL_GRID_MARGIN * 2.0f) / RVN_LABEL_CELL) + 1;
	labels->rows = (int)ceilf((height + RVN_LABEL_GRID_MARGIN * 2.0f) / RVN_LABEL_CELL) + 1;
	cells = (uint32_t)(labels->columns * labels->rows);
	RVN_ARRAY_RESERVE(labels->cells, labels->cell_cap, cells);
	for (i = 0; i < cells; i++) labels->cells[i] = -1;
	labels->box_count = 0;
	labels->node_count = 0;
}

static int rvn_grid_column(const rvn_map_labels_t* labels, float x) {
	int c = (int)floorf((x - labels->grid_x) / RVN_LABEL_CELL);
	return c < 0 ? 0 : c >= labels->columns ? labels->columns - 1 : c;
}

static int rvn_grid_row(const rvn_map_labels_t* labels, float y) {
	int r = (int)floorf((y - labels->grid_y) / RVN_LABEL_CELL);
	return r < 0 ? 0 : r >= labels->rows ? labels->rows - 1 : r;
}

static int rvn_grid_fits(const rvn_map_labels_t* labels, const rvn_label_box_t* box) {
	int c0 = rvn_grid_column(labels, box->left), c1 = rvn_grid_column(labels, box->right);
	int r0 = rvn_grid_row(labels, box->top), r1 = rvn_grid_row(labels, box->bottom), c, r;
	for (r = r0; r <= r1; r++) {
		for (c = c0; c <= c1; c++) {
			int32_t node;
			for (node = labels->cells[r * labels->columns + c]; node >= 0; node = labels->nodes[node].next) {
				const rvn_label_box_t* other = &labels->boxes[labels->nodes[node].box];
				if (box->left < other->right && other->left < box->right && box->top < other->bottom && other->top < box->bottom) return R_FALSE;
			}
		}
	}
	return R_TRUE;
}

static void rvn_grid_insert(rvn_map_labels_t* labels, const rvn_label_box_t* box) {
	int c0 = rvn_grid_column(labels, box->left), c1 = rvn_grid_column(labels, box->right);
	int r0 = rvn_grid_row(labels, box->top), r1 = rvn_grid_row(labels, box->bottom), c, r;
	uint32_t index = labels->box_count;
	RVN_ARRAY_RESERVE(labels->boxes, labels->box_cap, labels->box_count + 1);
	labels->boxes[labels->box_count++] = *box;
	for (r = r0; r <= r1; r++) {
		for (c = c0; c <= c1; c++) {
			int32_t* cell = &labels->cells[r * labels->columns + c];
			RVN_ARRAY_RESERVE(labels->nodes, labels->node_cap, labels->node_count + 1);
			labels->nodes[labels->node_count].box = index;
			labels->nodes[labels->node_count].next = *cell;
			*cell = (int32_t)labels->node_count++;
		}
	}
}

/* The symbols placed this placement, by a hash of what and where: the same one in the next tile's buffer is placed once. */
static int rvn_seen_has(const rvn_map_labels_t* labels, uint64_t key) {
	uint32_t at;
	if (!labels->seen_cap) return R_FALSE;
	for (at = (uint32_t)key & (labels->seen_cap - 1); labels->seen[at]; at = (at + 1) & (labels->seen_cap - 1)) {
		if (labels->seen[at] == key) return R_TRUE;
	}
	return R_FALSE;
}

static void rvn_seen_add(rvn_map_labels_t* labels, uint64_t key) {
	uint32_t at;
	if ((labels->seen_count + 1) * 2 > labels->seen_cap) {
		uint64_t* old = labels->seen;
		uint32_t old_cap = labels->seen_cap, i;
		labels->seen_cap = old_cap ? old_cap * 2 : 1024;
		labels->seen = (uint64_t*)rvn_calloc(sizeof(uint64_t) * labels->seen_cap);
		for (i = 0; i < old_cap; i++) {
			if (!old[i]) continue;
			for (at = (uint32_t)old[i] & (labels->seen_cap - 1); labels->seen[at]; at = (at + 1) & (labels->seen_cap - 1)) {}
			labels->seen[at] = old[i];
		}
		rvn_free(old);
	}
	for (at = (uint32_t)key & (labels->seen_cap - 1); labels->seen[at]; at = (at + 1) & (labels->seen_cap - 1)) {
		if (labels->seen[at] == key) return;
	}
	labels->seen[at] = key;
	labels->seen_count++;
}

static uint64_t rvn_seen_key(const char* text, const char* icon, int x, int y) {
	uint64_t hash = RVN_FNV_START;
	if (text) hash = rvn_fnv(hash, text, strlen(text));
	hash = rvn_fnv(hash, "|", 1);
	if (icon) hash = rvn_fnv(hash, icon, strlen(icon));
	hash = rvn_fnv(hash, &x, sizeof(x));
	hash = rvn_fnv(hash, &y, sizeof(y));
	return hash ? hash : 1;
}

/* ---------------------------------------------------------------------------
 *  Placing
 * ------------------------------------------------------------------------- */

static rvn_label_op_t* rvn_label_op(rvn_map_labels_t* labels) {
	rvn_label_op_t* op;
	RVN_ARRAY_RESERVE(labels->ops, labels->op_cap, labels->op_count + 1);
	op = &labels->ops[labels->op_count++];
	memset(op, 0, sizeof(*op));
	return op;
}

static int rvn_round(float v) {
	return (int)floorf(v + 0.5f);
}

static int rvn_ends_with(const char* text, const char* end) {
	size_t n = strlen(text), m = strlen(end);
	return n >= m && strcmp(text + n - m, end) == 0;
}

/*
 * Places a symbol at (x, y): its icon centred on it, its text, wrapped, placed
 * by the layer's anchor and offset, both only where they overlap nothing
 * placed. Returns whether it placed it.
 */
static int rvn_place_point(rvn_map_labels_t* labels, const char* text, const char* icon_name, float x, float y, const rvn_symbol_style_t* style) {
	const rvn_glyph_style_t* st = &labels->styles[style->text];
	const rvn_map_icon_t* icon = rvn_map_sprite_find(labels->sprite, icon_name);
	float margin = style->max_width, left = 0.0f, top = 0.0f, w = 0.0f;
	rvn_label_box_t boxes[2], icon_box;
	int box_count = 0, line_count = 0, i;
	const char* lines[64];
	float widths[64];
	if (x < -margin || y < -margin || x > labels->view->width + margin || y > labels->view->height + margin) return R_FALSE;
	if (!text && !icon) return R_FALSE;
	if (icon) {
		float iw = (float)icon->width / icon->ratio * style->icon_size, ih = (float)icon->height / icon->ratio * style->icon_size;
		icon_box.left = x - iw / 2.0f;
		icon_box.top = y - ih / 2.0f;
		icon_box.right = x + iw / 2.0f;
		icon_box.bottom = y + ih / 2.0f;
		if (!style->icon_overlaps) boxes[box_count++] = icon_box;
	}
	if (text) {
		/* words in lines no wider than the maximum width, the text's own line breaks kept */
		size_t n = strlen(text);
		char* candidate = rvn_label_buffer(labels, n * 2 + 2);
		const char* p = text;
		float h;
		size_t line_length = 0;
		while (*p && line_count < 64) {
			const char* word = p;
			size_t word_length;
			int paragraph_end;
			while (*p && *p != ' ' && *p != '\n') p++;
			word_length = (size_t)(p - word);
			paragraph_end = *p == '\n' || *p == '\0';
			if (*p) p++;
			if (word_length) {
				if (line_length) {
					/* the line with this word: too wide, the word starts a new line */
					memcpy(candidate + line_length, " ", 1);
					memcpy(candidate + line_length + 1, word, word_length);
					if (rvn_label_width(labels, candidate, (int)(line_length + 1 + word_length), st->size, st->face) > style->max_width) {
						lines[line_count++] = rvn_arena_strndup(&labels->op_arena, candidate, line_length);
						memcpy(candidate, word, word_length);
						line_length = word_length;
					} else {
						line_length += 1 + word_length;
					}
				} else {
					memcpy(candidate, word, word_length);
					line_length = word_length;
				}
			}
			if (paragraph_end && line_length && line_count < 64) {
				lines[line_count++] = rvn_arena_strndup(&labels->op_arena, candidate, line_length);
				line_length = 0;
			}
		}
		for (i = 0; i < line_count; i++) {
			widths[i] = rvn_label_width(labels, lines[i], -1, st->size, st->face);
			if (widths[i] > w) w = widths[i];
		}
		h = style->line_height * (float)line_count;
		/* where the anchor puts the box, then the offset */
		left = x + style->offset[0] - (rvn_ends_with(style->anchor, "left") ? 0.0f : rvn_ends_with(style->anchor, "right") ? w : w / 2.0f);
		top = y + style->offset[1] - (strncmp(style->anchor, "top", 3) == 0 ? 0.0f : strncmp(style->anchor, "bottom", 6) == 0 ? h : h / 2.0f);
		boxes[box_count].left = left - st->padding;
		boxes[box_count].top = top - st->padding;
		boxes[box_count].right = left + w + st->padding;
		boxes[box_count].bottom = top + h + st->padding;
		box_count++;
	}
	{
		/* the same symbol in the next tile's buffer, or again in the same place, once */
		uint64_t key = rvn_seen_key(text, icon_name, rvn_round(x / 8.0f), rvn_round(y / 8.0f));
		if (rvn_seen_has(labels, key)) return R_FALSE;
		for (i = 0; i < box_count; i++) if (!rvn_grid_fits(labels, &boxes[i])) return R_FALSE;
		for (i = 0; i < box_count; i++) rvn_grid_insert(labels, &boxes[i]);
		rvn_seen_add(labels, key);
	}
	if (icon) {
		rvn_label_op_t* op = rvn_label_op(labels);
		op->icon = icon;
		op->x = icon_box.left;
		op->y = icon_box.top;
		op->width = icon_box.right - icon_box.left;
		op->height = icon_box.bottom - icon_box.top;
		labels->icons++;
	}
	for (i = 0; i < line_count; i++) {
		rvn_label_op_t* op = rvn_label_op(labels);
		op->text = lines[i];
		op->x = left + (w - widths[i]) / 2.0f;
		op->y = top + (float)i * style->line_height;
		op->style = style->text;
	}
	return R_TRUE;
}

/* The line on screen, at (ox, oy) plus the points times scale, with the distance along it at each point. Returns its length. */
static float rvn_screen_line(rvn_map_labels_t* labels, const rvn_map_line_label_t* label, float ox, float oy, float scale, int* off_screen) {
	uint32_t i, n = label->point_count;
	float min_x = INFINITY, min_y = INFINITY, max_x = -INFINITY, max_y = -INFINITY;
	if (n > labels->line_cap) {
		labels->line_cap = n * 2;
		labels->xs = (float*)rvn_realloc(labels->xs, sizeof(float) * labels->line_cap);
		labels->ys = (float*)rvn_realloc(labels->ys, sizeof(float) * labels->line_cap);
		labels->cumulative = (float*)rvn_realloc(labels->cumulative, sizeof(float) * labels->line_cap);
	}
	for (i = 0; i < n; i++) {
		float x = ox + label->xy[i * 2] * scale, y = oy + label->xy[i * 2 + 1] * scale;
		labels->xs[i] = x;
		labels->ys[i] = y;
		labels->cumulative[i] = i ? labels->cumulative[i - 1] + hypotf(x - labels->xs[i - 1], y - labels->ys[i - 1]) : 0.0f;
		if (x < min_x) min_x = x;
		if (x > max_x) max_x = x;
		if (y < min_y) min_y = y;
		if (y > max_y) max_y = y;
	}
	*off_screen = max_x < 0.0f || min_x > labels->view->width || max_y < 0.0f || min_y > labels->view->height;
	return labels->cumulative[n - 1];
}

/* The point `distance` along the screen line of n points, and its direction there. */
static rvn_along_t rvn_line_at(const rvn_map_labels_t* labels, uint32_t n, float distance) {
	rvn_along_t at;
	uint32_t i = 1;
	float segment, t;
	while (i < n - 1 && labels->cumulative[i] < distance) i++;
	segment = labels->cumulative[i] - labels->cumulative[i - 1];
	t = segment > 0.0f ? rvn_clampf((distance - labels->cumulative[i - 1]) / segment, 0.0f, 1.0f) : 0.0f;
	at.x = labels->xs[i - 1] + (labels->xs[i] - labels->xs[i - 1]) * t;
	at.y = labels->ys[i - 1] + (labels->ys[i] - labels->ys[i - 1]) * t;
	at.angle = atan2f(labels->ys[i] - labels->ys[i - 1], labels->xs[i] - labels->xs[i - 1]);
	return at;
}

/* The turn from `from` to `to`, between -pi and pi. */
static float rvn_turn(float to, float from) {
	float d = to - from;
	while (d > RVN_LABEL_PI) d -= 2.0f * RVN_LABEL_PI;
	while (d < -RVN_LABEL_PI) d += 2.0f * RVN_LABEL_PI;
	return d;
}

/* A label upright at the middle of every symbol-spacing of its line: a road shield, its number on its icon. */
static int rvn_place_at_points_along(rvn_map_labels_t* labels, const rvn_map_line_label_t* label, float ox, float oy, float scale, const rvn_symbol_style_t* style) {
	int off_screen, repeats, r, any = R_FALSE;
	float length = rvn_screen_line(labels, label, ox, oy, scale, &off_screen);
	if (off_screen) return R_FALSE;
	repeats = (int)(length / style->spacing);
	if (repeats < 1) repeats = 1;
	for (r = 0; r < repeats; r++) {
		rvn_along_t at = rvn_line_at(labels, label->point_count, ((float)r + 0.5f) * length / (float)repeats);
		if (rvn_place_point(labels, label->text, label->icon, at.x, at.y, style)) any = R_TRUE;
	}
	return any;
}

/*
 * A label's icon at the middle of every symbol-spacing of its line, turned to
 * the line where it stands, plus icon-rotate: never flipped, since the way
 * the line runs is what a one-way arrow shows.
 */
static int rvn_place_icons_along(rvn_map_labels_t* labels, const rvn_map_line_label_t* label, float ox, float oy, float scale, const rvn_symbol_style_t* style) {
	const rvn_map_icon_t* icon = rvn_map_sprite_find(labels->sprite, label->icon);
	int off_screen, repeats, r, any = R_FALSE;
	float length, w, h, reach;
	if (!icon) return R_FALSE;
	length = rvn_screen_line(labels, label, ox, oy, scale, &off_screen);
	if (off_screen) return R_FALSE;
	w = (float)icon->width / icon->ratio * style->icon_size;
	h = (float)icon->height / icon->ratio * style->icon_size;
	if (length < w * 2.0f) return R_FALSE;
	/* a turned icon stays within its half diagonal of its centre */
	reach = hypotf(w, h) / 2.0f;
	repeats = (int)(length / style->spacing);
	if (repeats < 1) repeats = 1;
	for (r = 0; r < repeats; r++) {
		rvn_along_t at = rvn_line_at(labels, label->point_count, ((float)r + 0.5f) * length / (float)repeats);
		rvn_label_box_t box;
		rvn_label_op_t* op;
		box.left = at.x - reach;
		box.top = at.y - reach;
		box.right = at.x + reach;
		box.bottom = at.y + reach;
		if (!rvn_grid_fits(labels, &box)) continue;
		rvn_grid_insert(labels, &box);
		op = rvn_label_op(labels);
		op->icon = icon;
		op->x = at.x - w / 2.0f;
		op->y = at.y - h / 2.0f;
		op->width = w;
		op->height = h;
		op->rotation = at.angle + style->icon_rotate;
		labels->icons++;
		any = R_TRUE;
	}
	return any;
}

/*
 * A text along its line: at the middle of every symbol-spacing of line long
 * enough for it, glyph by glyph, each turned to the line where it stands.
 * Returns whether it placed it once at least.
 */
static int rvn_place_along(rvn_map_labels_t* labels, const rvn_map_line_label_t* label, float ox, float oy, float scale, const rvn_symbol_style_t* style) {
	const rvn_glyph_style_t* st = &labels->styles[style->text];
	size_t n = strlen(label->text), k;
	char* flat;
	uint32_t glyph_count = 0, i;
	int off_screen, repeats, r, any = R_FALSE;
	float length, width, half;
	length = rvn_screen_line(labels, label, ox, oy, scale, &off_screen);
	if (off_screen) return R_FALSE;
	/* on one line */
	flat = rvn_label_buffer(labels, n + 1);
	for (k = 0; k < n; k++) flat[k] = label->text[k] == '\n' ? ' ' : label->text[k];
	flat[n] = '\0';
	width = rvn_label_width(labels, flat, (int)n, st->size, st->face);
	if (width + st->size > length) return R_FALSE;
	/* each glyph's advance, from the widths of the text up to it, so kerning is kept */
	if (n + 1 > labels->glyph_cap) {
		labels->glyph_cap = (uint32_t)(n + 1) * 2;
		labels->starts = (float*)rvn_realloc(labels->starts, sizeof(float) * labels->glyph_cap);
		labels->offsets = (uint32_t*)rvn_realloc(labels->offsets, sizeof(uint32_t) * labels->glyph_cap);
		labels->glyphs = (rvn_along_t*)rvn_realloc(labels->glyphs, sizeof(rvn_along_t) * labels->glyph_cap);
		flat = labels->buffer;
	}
	{
		const char* p = flat;
		const char* end = flat + n;
		while (p < end) {
			labels->offsets[glyph_count++] = (uint32_t)(p - flat);
			rvn_utf8_decode(&p, end);
		}
		labels->offsets[glyph_count] = (uint32_t)n;
	}
	labels->starts[0] = 0.0f;
	for (i = 1; i <= glyph_count; i++) labels->starts[i] = rvn_label_width(labels, flat, (int)labels->offsets[i], st->size, st->face);
	half = st->height / 2.0f + st->padding;
	repeats = (int)(length / style->spacing);
	if (repeats < 1) repeats = 1;
	for (r = 0; r < repeats; r++) {
		float center = ((float)r + 0.5f) * length / (float)repeats, previous = 0.0f;
		int reversed, ok = R_TRUE;
		rvn_along_t mid;
		uint64_t key;
		if (center - width / 2.0f < 0.0f || center + width / 2.0f > length) continue;
		/* reading left to right: along the line, or back along it when it runs leftward */
		reversed = cosf(rvn_line_at(labels, label->point_count, center).angle) < 0.0f;
		for (i = 0; i < glyph_count; i++) {
			float middle = (labels->starts[i] + labels->starts[i + 1]) / 2.0f - width / 2.0f;
			rvn_along_t at = rvn_line_at(labels, label->point_count, reversed ? center - middle : center + middle);
			if (reversed) at.angle += RVN_LABEL_PI;
			if (i > 0 && fabsf(rvn_turn(at.angle, previous)) > RVN_LABEL_MAX_TURN) {
				ok = R_FALSE;
				break;
			}
			previous = at.angle;
			labels->glyphs[i] = at;
		}
		if (!ok) continue;
		/* the same name placed nearby already, from the next tile's buffer or a piece of the same street */
		mid = labels->glyphs[glyph_count / 2];
		key = rvn_seen_key(flat, NULL, rvn_round(mid.x / 64.0f), rvn_round(mid.y / 64.0f));
		if (rvn_seen_has(labels, key)) continue;
		for (i = 0; i < glyph_count && ok; i++) {
			rvn_label_box_t box;
			box.left = labels->glyphs[i].x - half;
			box.top = labels->glyphs[i].y - half;
			box.right = labels->glyphs[i].x + half;
			box.bottom = labels->glyphs[i].y + half;
			ok = rvn_grid_fits(labels, &box);
		}
		if (!ok) continue;
		for (i = 0; i < glyph_count; i++) {
			rvn_label_box_t box;
			box.left = labels->glyphs[i].x - half;
			box.top = labels->glyphs[i].y - half;
			box.right = labels->glyphs[i].x + half;
			box.bottom = labels->glyphs[i].y + half;
			rvn_grid_insert(labels, &box);
		}
		rvn_seen_add(labels, key);
		any = R_TRUE;
		for (i = 0; i < glyph_count; i++) {
			const rvn_along_t* at = &labels->glyphs[i];
			float advance = labels->starts[i + 1] - labels->starts[i];
			float c = cosf(at->angle), s = sinf(at->angle), dx = -advance / 2.0f, dy = -st->height / 2.0f;
			rvn_label_op_t* op;
			if (flat[labels->offsets[i]] == ' ') continue;
			/* the glyph's top left corner, so its middle sits on the line */
			op = rvn_label_op(labels);
			op->text = rvn_arena_strndup(&labels->op_arena, flat + labels->offsets[i], labels->offsets[i + 1] - labels->offsets[i]);
			op->x = at->x + dx * c - dy * s;
			op->y = at->y + dx * s + dy * c;
			op->rotation = at->angle;
			op->style = style->text;
		}
	}
	return any;
}

static float rvn_layout_number(const rvn_map_style_layer_t* layer, const char* name, double zoom, float fallback) {
	rvn_map_value_t v = rvn_map_layer_layout(layer, name, zoom);
	return v.kind == RVN_MV_NUMBER ? (float)v.as.number : fallback;
}

static int rvn_layout_is(const rvn_map_style_layer_t* layer, const char* name, double zoom, const char* text) {
	rvn_map_value_t v = rvn_map_layer_layout(layer, name, zoom);
	return v.kind == RVN_MV_STRING && strcmp(v.as.string, text) == 0;
}

/* The face a style's text-font names ("Noto Sans Bold Italic"). */
static int rvn_label_face(const rvn_map_style_layer_t* layer, double zoom) {
	rvn_map_value_t fonts = rvn_map_layer_layout(layer, "text-font", zoom);
	const char* name;
	int bold, italic;
	if (fonts.kind != RVN_MV_ARRAY || fonts.count == 0 || fonts.as.items[0].kind != RVN_MV_STRING) return RVN_FACE_REGULAR;
	name = fonts.as.items[0].as.string;
	bold = strstr(name, "Bold") != NULL;
	italic = strstr(name, "Italic") != NULL;
	return bold && italic ? RVN_FACE_BOLD_ITALIC : bold ? RVN_FACE_BOLD : italic ? RVN_FACE_ITALIC : RVN_FACE_REGULAR;
}

/* Places the labels of the tiles: layer by layer, the style's last first, each only where its boxes overlap none placed before. */
static void rvn_place_labels(rvn_map_labels_t* labels, const rvn_map_style_t* style, const rvn_map_prepared_t* const* tiles, uint32_t tile_count) {
	const rvn_map_view_t* view = labels->view;
	const double zoom = view->zoom, world = 512.0 * pow(2.0, zoom);
	uint32_t l, t, i;
	labels->op_count = 0;
	labels->style_count = 0;
	rvn_arena_free(&labels->op_arena);
	rvn_arena_init(&labels->op_arena, 64 * 1024);
	rvn_grid_reset(labels, view->width, view->height);
	if (labels->seen_cap) memset(labels->seen, 0, sizeof(uint64_t) * labels->seen_cap);
	labels->seen_count = 0;
	labels->placed = labels->candidates = labels->icons = 0;
	for (l = style->symbol_count; l-- > 0;) {
		const rvn_map_style_layer_t* layer = &style->symbols[l];
		rvn_glyph_style_t* st;
		rvn_symbol_style_t symbols;
		rvn_map_value_t v, offset;
		if (zoom < layer->min_zoom || zoom >= layer->max_zoom) continue;
		RVN_ARRAY_RESERVE(labels->styles, labels->style_cap, labels->style_count + 1);
		st = &labels->styles[labels->style_count];
		memset(st, 0, sizeof(*st));
		st->size = rvn_layout_number(layer, "text-size", zoom, 16.0f);
		st->height = rvn_label_line_height(labels, st->size);
		v = rvn_map_layer_paint(layer, "text-color", zoom);
		if (!rvn_map_parse_color(&v, st->color)) {
			st->color[0] = st->color[1] = st->color[2] = 0.0f;
			st->color[3] = 1.0f;
		}
		v = rvn_map_layer_paint(layer, "text-halo-color", zoom);
		st->has_halo = rvn_map_parse_color(&v, st->halo);
		st->halo_width = (float)rvn_map_layer_number(layer, "text-halo-width", zoom, 0.0);
		st->padding = rvn_layout_number(layer, "text-padding", zoom, 2.0f);
		st->face = rvn_label_face(layer, zoom);
		memset(&symbols, 0, sizeof(symbols));
		symbols.text = labels->style_count++;
		symbols.max_width = rvn_layout_number(layer, "text-max-width", zoom, 10.0f) * st->size;
		v = rvn_map_layer_layout(layer, "text-anchor", zoom);
		symbols.anchor = v.kind == RVN_MV_STRING ? v.as.string : "center";
		offset = rvn_map_layer_layout(layer, "text-offset", zoom);
		if (offset.kind == RVN_MV_ARRAY && offset.count >= 2 && offset.as.items[0].kind == RVN_MV_NUMBER && offset.as.items[1].kind == RVN_MV_NUMBER) {
			symbols.offset[0] = (float)offset.as.items[0].as.number * st->size;
			symbols.offset[1] = (float)offset.as.items[1].as.number * st->size;
		}
		symbols.line_height = st->size * 1.2f;
		symbols.spacing = rvn_layout_number(layer, "symbol-spacing", zoom, 250.0f);
		symbols.icon_size = rvn_layout_number(layer, "icon-size", zoom, 1.0f);
		v = rvn_map_layer_layout(layer, "icon-allow-overlap", zoom);
		symbols.icon_overlaps = v.kind == RVN_MV_BOOL && v.boolean;
		symbols.along_line = rvn_layout_is(layer, "symbol-placement", zoom, "line");
		symbols.upright = rvn_layout_is(layer, "text-rotation-alignment", zoom, "viewport");
		symbols.icon_rotate = rvn_layout_number(layer, "icon-rotate", zoom, 0.0f) * RVN_LABEL_PI / 180.0f;
		for (t = 0; t < tile_count; t++) {
			const rvn_map_prepared_t* tile = tiles[t];
			const rvn_map_symbol_bucket_t* bucket;
			double n = (double)(1 << tile->z);
			float scale = (float)(world / n);
			float ox = (float)((tile->x / n - view->center_x) * world + view->width / 2.0);
			float oy = (float)((tile->y / n - view->center_y) * world + view->height / 2.0);
			if (l >= tile->symbol_count) continue;
			bucket = &tile->symbols[l];
			for (i = 0; i < bucket->line_count; i++) {
				const rvn_map_line_label_t* label = &bucket->lines[i];
				int placed;
				labels->candidates++;
				if (symbols.along_line && !symbols.upright && label->text) placed = rvn_place_along(labels, label, ox, oy, scale, &symbols);
				else if (symbols.along_line && !label->text && label->icon) placed = rvn_place_icons_along(labels, label, ox, oy, scale, &symbols);
				else placed = rvn_place_at_points_along(labels, label, ox, oy, scale, &symbols);
				if (placed) labels->placed++;
			}
			for (i = 0; i < bucket->label_count; i++) {
				const rvn_map_label_t* label = &bucket->labels[i];
				labels->candidates++;
				if (rvn_place_point(labels, label->text, label->icon, ox + label->x * scale, oy + label->y * scale, &symbols)) labels->placed++;
			}
		}
	}
}

/* ---------------------------------------------------------------------------
 *  Drawing
 * ------------------------------------------------------------------------- */

static void rvn_label_text(RPainter* painter, Rid font, float size, float x, float y, float rotation, const char* text, const float color[4]) {
	if (rotation == 0.0f) Rdraw_text(painter, font, size, x, y, text, color[0], color[1], color[2], color[3]);
	else Rdraw_text_rotated(painter, font, size, x, y, rotation, text, color[0], color[1], color[2], color[3]);
}

void rvn_map_labels_draw(rvn_map_labels_t* labels, RPainter* painter, const rvn_map_style_t* style,
	const rvn_map_prepared_t* const* tiles, uint32_t tile_count, const rvn_map_view_t* view, int version, int settled,
	const rvn_map_sprite_t* sprite, rvn_map_label_stats_t* stats) {
	/* four diagonals around a letter, for its halo: as wide as eight directions to the eye, at half the draws */
	static const float halo[4][2] = { { -1.0f, -1.0f }, { 1.0f, -1.0f }, { -1.0f, 1.0f }, { 1.0f, 1.0f } };
	double start = rvn_clock_seconds(), world = 512.0 * pow(2.0, view->zoom);
	float scale = labels->placed_once ? (float)pow(2.0, view->zoom - labels->zoom) : 1.0f;
	float dx = (float)((labels->center_x - view->center_x) * world), dy = (float)((labels->center_y - view->center_y) * world);
	float cx = view->width / 2.0f, cy = view->height / 2.0f;
	int moved = labels->zoom != view->zoom || dx != 0.0f || dy != 0.0f;
	int stale = !labels->placed_once || fabs(view->zoom - labels->zoom) > 0.25 || labels->width != view->width ||
		labels->height != view->height || labels->version != version || fabsf(dx) > view->width / 4.0f ||
		fabsf(dy) > view->height / 4.0f || (settled && moved);
	uint32_t i;
	labels->view = view;
	labels->sprite = sprite;
	if (stale) {
		rvn_place_labels(labels, style, tiles, tile_count);
		labels->placed_once = R_TRUE;
		labels->zoom = view->zoom;
		labels->width = view->width;
		labels->height = view->height;
		labels->version = version;
		labels->center_x = view->center_x;
		labels->center_y = view->center_y;
		stats->place_ms = (rvn_clock_seconds() - start) * 1000.0;
		stats->placed = labels->placed;
		stats->candidates = labels->candidates;
		stats->icons = labels->icons;
		scale = 1.0f;
		dx = dy = 0.0f;
	}
	stats->reused = !stale;
	/* a point placed at p0 is now at the view's centre plus (p0 - centre) scaled, plus the pan */
	for (i = 0; i < labels->op_count; i++) {
		const rvn_label_op_t* op = &labels->ops[i];
		float x = cx + (op->x - cx) * scale + dx, y = cy + (op->y - cy) * scale + dy;
		if (op->icon) {
			const rvn_map_icon_t* icon = op->icon;
			float u0, v0, u1, v1;
			if (!sprite || sprite->width <= 0 || sprite->height <= 0) continue;
			u0 = (float)icon->x / (float)sprite->width;
			v0 = (float)icon->y / (float)sprite->height;
			u1 = (float)(icon->x + icon->width) / (float)sprite->width;
			v1 = (float)(icon->y + icon->height) / (float)sprite->height;
			if (op->rotation == 0.0f) Rdraw_image_region(painter, sprite->texture, x, y, op->width, op->height, u0, v0, u1, v1, 1.0f, 1.0f, 1.0f, 1.0f);
			else Rdraw_image_rotated(painter, sprite->texture, x, y, op->width, op->height, u0, v0, u1, v1, op->rotation, 1.0f, 1.0f, 1.0f, 1.0f);
		} else {
			const rvn_glyph_style_t* st = &labels->styles[op->style];
			Rid font = rvn_label_font(labels, st->face);
			if (st->has_halo && st->halo_width > 0.0f) {
				float d = rvn_maxf(st->halo_width * 0.75f, 0.5f);
				int h;
				for (h = 0; h < 4; h++) rvn_label_text(painter, font, st->size, x + halo[h][0] * d, y + halo[h][1] * d, op->rotation, op->text, st->halo);
			}
			rvn_label_text(painter, font, st->size, x, y, op->rotation, op->text, st->color);
		}
	}
	stats->ms = (rvn_clock_seconds() - start) * 1000.0;
}
