/**
 * Raven - map/map.h
 * The parts of the Map widget (widgets/map.c) that know nothing of widgets:
 * JSON and tile values, Mapbox Vector Tiles, MapLibre's style JSON and its
 * expressions, earcut, and tiles prepared for drawing.
 *
 * Ported from compose-raven's maps spike (compose/spikes/maps), which stays
 * the reference: the same algorithms, so the two can be measured against
 * each other.
 */

#ifndef RVN_MAP_H
#define RVN_MAP_H

#include "../core/base.h"
#include "../core/thread.h"

/* ---------------------------------------------------------------------------
 *  Values (map/json.c): a JSON document, a tile's property values, and what
 *  a style expression evaluates to.
 * ------------------------------------------------------------------------- */

typedef enum rvn_map_kind_t {
	RVN_MV_NULL,
	RVN_MV_BOOL,
	RVN_MV_NUMBER,
	RVN_MV_STRING,
	RVN_MV_ARRAY,
	RVN_MV_OBJECT,
	/** Only from an expression: a colour, its channels straight (not premultiplied). */
	RVN_MV_COLOR
} rvn_map_kind_t;

typedef struct rvn_map_member_t rvn_map_member_t;

typedef struct rvn_map_value_t {
	uint8_t kind;
	uint8_t boolean;
	/** Items of an array, members of an object, bytes of a string. */
	uint32_t count;
	union {
		double number;
		const char* string;
		const struct rvn_map_value_t* items;
		const rvn_map_member_t* members;
		float color[4];
	} as;
} rvn_map_value_t;

struct rvn_map_member_t {
	const char* key;
	rvn_map_value_t value;
};

/** Parses JSON into values allocated in the arena. R_FALSE on a syntax error. */
int rvn_map_json_parse(rvn_arena_t* arena, const char* text, size_t length, rvn_map_value_t* out);
/** A member of an object, or NULL. */
const rvn_map_value_t* rvn_map_member(const rvn_map_value_t* object, const char* key);
/** A string member, or NULL. */
const char* rvn_map_member_string(const rvn_map_value_t* object, const char* key);
/** A number member, or the fallback. */
double rvn_map_member_number(const rvn_map_value_t* object, const char* key, double fallback);

/* ---------------------------------------------------------------------------
 *  Mapbox Vector Tiles 2.1 (map/tile.c)
 * ------------------------------------------------------------------------- */

typedef enum rvn_map_geometry_t {
	RVN_MAP_UNKNOWN,
	RVN_MAP_POINT,
	RVN_MAP_LINESTRING,
	RVN_MAP_POLYGON
} rvn_map_geometry_t;

typedef struct rvn_map_feature_t {
	rvn_map_geometry_t type;
	/** Pairs of indices into the layer's keys and values. */
	const uint32_t* tags;
	uint32_t tag_count;
	/** The raw command stream, decoded by rvn_map_feature_rings. */
	const uint32_t* commands;
	uint32_t command_count;
} rvn_map_feature_t;

typedef struct rvn_map_layer_t {
	const char* name;
	uint32_t extent;
	const char** keys;
	uint32_t key_count;
	rvn_map_value_t* values;
	uint32_t value_count;
	rvn_map_feature_t* features;
	uint32_t feature_count;
} rvn_map_layer_t;

typedef struct rvn_map_tile_data_t {
	rvn_map_layer_t* layers;
	uint32_t layer_count;
} rvn_map_tile_data_t;

/**
 * Decodes a whole tile into the arena, inflating it first if it is gzip or
 * zlib compressed. Nothing decoded points into `bytes`. R_FALSE if it is malformed.
 */
int rvn_map_tile_decode(rvn_arena_t* arena, const uint8_t* bytes, size_t length, rvn_map_tile_data_t* out);
const rvn_map_layer_t* rvn_map_tile_layer(const rvn_map_tile_data_t* tile, const char* name);

/** Rings of x, y pairs in tile coordinates, decoded from a feature (a ClosePath repeats no point). */
typedef struct rvn_map_rings_t {
	float* xy;
	uint32_t point_count, point_cap;
	/** Where each ring starts, in points; one more than the rings, the last is point_count. */
	uint32_t* starts;
	uint32_t ring_count, start_cap;
} rvn_map_rings_t;

/** Decodes the feature's geometry into rings, reusing their storage. R_FALSE on an unknown command. */
int  rvn_map_feature_rings(const rvn_map_feature_t* feature, rvn_map_rings_t* rings);
void rvn_map_rings_free(rvn_map_rings_t* rings);
/** Twice the signed area of a ring: positive for an exterior ring in tile coordinates (y down). */
double rvn_map_ring_area(const float* xy, uint32_t point_count);

/* ---------------------------------------------------------------------------
 *  earcut (map/earcut.c)
 * ------------------------------------------------------------------------- */

typedef struct rvn_map_earcut_t {
	/** The nodes of the linked lists, in blocks that never move, reused from one polygon to the next. */
	struct rvn_map_earcut_block_t* blocks;
	struct rvn_map_earcut_block_t* block;
	uint32_t block_used;
	struct rvn_map_earcut_node_t** queue;
	uint32_t queue_cap;
	/** The triangles, three vertex indices each. */
	uint32_t* indices;
	uint32_t index_count, index_cap;
} rvn_map_earcut_t;

/**
 * Triangulates a polygon: `xy` holds `point_count` points, the outer ring
 * first, then each hole, starting at the point index in `holes`. The
 * triangles land in earcut->indices.
 */
void rvn_map_earcut(rvn_map_earcut_t* earcut, const float* xy, uint32_t point_count, const uint32_t* holes, uint32_t hole_count);
void rvn_map_earcut_free(rvn_map_earcut_t* earcut);

/* ---------------------------------------------------------------------------
 *  Style (map/style.c): MapLibre's style JSON, the part fill and line layers
 *  need, and the expressions it is written in.
 * ------------------------------------------------------------------------- */

typedef enum rvn_map_layer_type_t {
	RVN_MAP_LAYER_OTHER,
	RVN_MAP_LAYER_BACKGROUND,
	RVN_MAP_LAYER_FILL,
	RVN_MAP_LAYER_FILL_EXTRUSION,
	RVN_MAP_LAYER_LINE,
	RVN_MAP_LAYER_SYMBOL
} rvn_map_layer_type_t;

typedef struct rvn_map_style_layer_t {
	const char* id;
	rvn_map_layer_type_t type;
	const char* source_layer;
	double min_zoom, max_zoom;
	const rvn_map_value_t* filter;
	const rvn_map_value_t* paint;
	const rvn_map_value_t* layout;
	/** A fill or a flat extrusion. */
	int is_fill;
	/** fill-antialias: the fill draws its outline over the mesh's aliased edges. */
	int antialias;
} rvn_map_style_layer_t;

typedef struct rvn_map_style_t {
	rvn_arena_t arena;
	rvn_map_value_t root;
	float background[4];
	/** The fill and line layers drawn, in order. */
	rvn_map_style_layer_t* layers;
	uint32_t layer_count;
	/** The symbol layers (labels and icons), in order: placed from the last. */
	rvn_map_style_layer_t* symbols;
	uint32_t symbol_count;
	/** Held by the map and by each tile being prepared with it, on any thread. */
	rvn_atomic_t references;
} rvn_map_style_t;

/** Parses a style, keeping the layers drawn from `source` (NULL: its first vector source). R_FALSE on bad JSON. */
int  rvn_map_style_parse(rvn_map_style_t* style, const char* json, size_t length, const char* source);
void rvn_map_style_free(rvn_map_style_t* style);
/** A style parsed on the heap, with one reference; NULL on bad JSON. */
rvn_map_style_t* rvn_map_style_create(const char* json, size_t length, const char* source);
void rvn_map_style_retain(rvn_map_style_t* style);
/** Drops a reference: the last one frees the style. Thread-safe. */
void rvn_map_style_release(rvn_map_style_t* style);

/** What an expression reads: the zoom, and the feature's properties and geometry type. */
typedef struct rvn_map_context_t {
	double zoom;
	const rvn_map_layer_t* layer;
	const rvn_map_feature_t* feature;
	/** Strings made by expressions (concat, upcase...): freed by whoever owns the context. */
	rvn_arena_t* scratch;
} rvn_map_context_t;

/** Evaluates a style expression: a literal, or an array starting with its operator. */
rvn_map_value_t rvn_map_evaluate(const rvn_map_value_t* expression, const rvn_map_context_t* context);
/** Whether a filter keeps the feature; no filter keeps every one. */
int rvn_map_accepts(const rvn_map_value_t* filter, const rvn_map_context_t* context);
/** A CSS colour as the style spec writes it, or R_FALSE. */
int rvn_map_parse_color(const rvn_map_value_t* value, float out[4]);

/** A paint or layout property at a zoom, for every feature alike (kind NULL when missing). */
rvn_map_value_t rvn_map_layer_paint(const rvn_map_style_layer_t* layer, const char* name, double zoom);
rvn_map_value_t rvn_map_layer_layout(const rvn_map_style_layer_t* layer, const char* name, double zoom);
double rvn_map_layer_number(const rvn_map_style_layer_t* layer, const char* name, double zoom, double fallback);
/** The colour (opacity applied) a fill or a line draws in at a zoom. R_FALSE when it has none. */
int rvn_map_layer_color(const rvn_map_style_layer_t* layer, double zoom, float out[4]);
/** A fill's outline colour: fill-outline-color, else the fill's own. */
int rvn_map_layer_outline_color(const rvn_map_style_layer_t* layer, double zoom, float out[4]);
/** line-cap at a zoom, as an RLineCap. */
int rvn_map_layer_line_cap(const rvn_map_style_layer_t* layer, double zoom);
/** line-dasharray at a zoom, in line widths: R_FALSE for a solid line. */
int rvn_map_layer_dashes(const rvn_map_style_layer_t* layer, double zoom, float* pattern, int* count, int max);

/* ---------------------------------------------------------------------------
 *  Prepared tiles (map/prepare.c)
 * ------------------------------------------------------------------------- */

/** What one style layer draws of one tile, in the tile's unit square (0 to 1 across). */
typedef struct rvn_map_bucket_t {
	/** Triangles, six floats each. */
	float* fills;
	uint32_t triangle_count;
	/** Lines as x, y pairs, a NaN pair after each: a line layer's lines, or a fill's outlines. */
	float* lines;
	uint32_t line_point_count;
	uint32_t segment_count;
} rvn_map_bucket_t;

/** A dashed or offset shape of a bucket's lines, cut for a width (map/lines.c). */
typedef struct rvn_map_shape_t {
	float unit;
	/** The offset it was cut at, in line widths. */
	float offset;
	float* xy;
	uint32_t point_count;
} rvn_map_shape_t;

/** A symbol of a tile at a point of its unit square: its text, its icon, either NULL. */
typedef struct rvn_map_label_t {
	const char* text;
	const char* icon;
	float x, y;
} rvn_map_label_t;

/** A symbol along a line of a tile: its text and icon, and the line in the tile's unit square. */
typedef struct rvn_map_line_label_t {
	const char* text;
	const char* icon;
	const float* xy;
	uint32_t point_count;
} rvn_map_line_label_t;

/** What one symbol layer places of one tile. */
typedef struct rvn_map_symbol_bucket_t {
	rvn_map_label_t* labels;
	uint32_t label_count;
	rvn_map_line_label_t* lines;
	uint32_t line_count;
} rvn_map_symbol_bucket_t;

/* the shapes kept per bucket: undashed offset 0 is the bucket itself, then the two sides of a gap */
#define RVN_MAP_SHAPES 3

typedef struct rvn_map_prepared_t {
	int z, x, y;
	/** One per style layer, empty where it draws nothing. */
	rvn_map_bucket_t* buckets;
	rvn_map_shape_t* shapes;
	uint32_t bucket_count;
	/** One per symbol layer of the style; their texts and lines are in the arena. */
	rvn_map_symbol_bucket_t* symbols;
	uint32_t symbol_count;
	rvn_arena_t arena;
	size_t arena_bytes;
	size_t bytes;
	double decode_ms, prepare_ms;
	uint32_t triangle_count, segment_count;
} rvn_map_prepared_t;

/**
 * Decodes a tile and fills each style layer's bucket from the features its
 * filter keeps at the tile's zoom: the layers drawn at that zoom or the next,
 * or, for a tile of the source's deepest zoom, every layer drawn from it on.
 * R_FALSE (and nothing kept) if the tile is malformed.
 */
int  rvn_map_prepare(rvn_map_prepared_t* tile, const rvn_map_style_t* style, int z, int x, int y,
	const uint8_t* bytes, size_t length, int deepest);
void rvn_map_prepared_free(rvn_map_prepared_t* tile);
/** The bytes a prepared tile holds: its buckets and the line shapes cut so far. */
size_t rvn_map_prepared_memory(const rvn_map_prepared_t* tile);

/** The text a symbol layer gives a feature (text-field, text-transform), in the scratch arena; NULL if empty. */
const char* rvn_map_label_text(const rvn_map_style_layer_t* layer, const rvn_map_context_t* context);
/** The name of the icon a symbol layer gives a feature (icon-image); NULL if none. */
const char* rvn_map_icon_name(const rvn_map_style_layer_t* layer, const rvn_map_context_t* context);
/** Upper or lower case, in place where it can (ASCII, Latin-1, Greek and Cyrillic): Raven has no full case mapping. */
char* rvn_map_change_case(rvn_arena_t* arena, const char* text, int upper);

/* ---------------------------------------------------------------------------
 *  Sprite sheets (map/labels.c)
 * ------------------------------------------------------------------------- */

typedef struct rvn_map_icon_t {
	const char* name;
	int x, y, width, height;
	/** Pixels of the sheet in a logical unit. */
	float ratio;
} rvn_map_icon_t;

typedef struct rvn_map_sprite_t {
	rvn_arena_t arena;
	/** Sorted by name. */
	rvn_map_icon_t* icons;
	uint32_t icon_count;
	Rid texture;
	int width, height;
} rvn_map_sprite_t;

/** Reads a sprite sheet's JSON index and decodes its PNG into a texture. NULL if either can't be read. */
rvn_map_sprite_t* rvn_map_sprite_create(const char* json, size_t json_length, const void* png, size_t png_length);
void rvn_map_sprite_free(rvn_map_sprite_t* sprite);
const rvn_map_icon_t* rvn_map_sprite_find(const rvn_map_sprite_t* sprite, const char* name);

/* ---------------------------------------------------------------------------
 *  Labels (map/labels.c): placed as the symbol layers say, in part, kept apart
 *  by collision, and kept while the camera moves a little
 * ------------------------------------------------------------------------- */

typedef struct rvn_map_labels_t rvn_map_labels_t;

/** What the labels of the last frame cost. */
typedef struct rvn_map_label_stats_t {
	int placed, candidates, icons;
	/** Whether the frame drew the placement before it, moved, rather than placing again. */
	int reused;
	double ms, place_ms;
} rvn_map_label_stats_t;

/** Where the map looks, for the labels. */
typedef struct rvn_map_view_t {
	double center_x, center_y, zoom;
	float width, height;
} rvn_map_view_t;

rvn_map_labels_t* rvn_map_labels_create(void);
void rvn_map_labels_free(rvn_map_labels_t* labels);
/**
 * Draws the labels of the tiles, each in its copy of the world (`copies`: 0,
 * or a world east or west past the antimeridian), placing them again when the view has moved
 * too far, the tiles or the sprite changed (`version`), or the camera has
 * `settled` somewhere other than where they were placed.
 */
void rvn_map_labels_draw(rvn_map_labels_t* labels, RPainter* painter, const rvn_map_style_t* style,
	const rvn_map_prepared_t* const* tiles, const int* copies, uint32_t tile_count, const rvn_map_view_t* view, int version, int settled,
	const rvn_map_sprite_t* sprite, rvn_map_label_stats_t* stats);

/* ---------------------------------------------------------------------------
 *  Dashes and line gaps (map/lines.c)
 * ------------------------------------------------------------------------- */

/** Lines (x, y pairs, a NaN pair after each) cut into dashes of `pattern`, the pattern restarting on each line. */
float* rvn_map_dash(const float* xy, uint32_t point_count, const float* pattern, int pattern_count, uint32_t* out_count);
/** Lines offset by `distance` to their left (negative: their right), mitred, at most four times it at sharp corners. */
float* rvn_map_offset(const float* xy, uint32_t point_count, float distance, uint32_t* out_count);

#endif
