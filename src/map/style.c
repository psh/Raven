/**
 * Raven - map/style.c
 * MapLibre's style JSON (maplibre.org/maplibre-style-spec), the part fill and
 * line layers need: a layer's source layer, zoom range, filter and paint, and
 * the expressions they are written in. A fill extrusion is drawn as a flat
 * fill, as from straight above. Left out, as in the Kotlin spike: patterns,
 * line-offset, and data-driven paint (a paint value that reads a feature's
 * properties).
 */

#include "map.h"
#include "../widget/widget.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

static const rvn_map_value_t g_rvn_map_null = { RVN_MV_NULL, 0, 0, { 0 } };

static rvn_map_value_t rvn_mv_bool(int value) {
	rvn_map_value_t v = g_rvn_map_null;
	v.kind = RVN_MV_BOOL;
	v.boolean = (uint8_t)(value != 0);
	return v;
}

static rvn_map_value_t rvn_mv_number(double value) {
	rvn_map_value_t v = g_rvn_map_null;
	v.kind = RVN_MV_NUMBER;
	v.as.number = value;
	return v;
}

static rvn_map_value_t rvn_mv_string(const char* value) {
	rvn_map_value_t v = g_rvn_map_null;
	v.kind = RVN_MV_STRING;
	v.as.string = value;
	v.count = (uint32_t)strlen(value);
	return v;
}

static int rvn_mv_true(rvn_map_value_t v) {
	return v.kind == RVN_MV_BOOL && v.boolean;
}

/** Equality as the style spec has it. */
static int rvn_mv_same(rvn_map_value_t a, rvn_map_value_t b) {
	if (a.kind != b.kind) return 0;
	switch (a.kind) {
		case RVN_MV_NULL: return 1;
		case RVN_MV_BOOL: return a.boolean == b.boolean;
		case RVN_MV_NUMBER: return a.as.number == b.as.number;
		case RVN_MV_STRING: return a.count == b.count && memcmp(a.as.string, b.as.string, a.count) == 0;
		case RVN_MV_COLOR: return memcmp(a.as.color, b.as.color, sizeof(a.as.color)) == 0;
		default: return a.as.items == b.as.items;
	}
}

static const char* rvn_mv_text(rvn_map_value_t v, rvn_arena_t* scratch) {
	char buffer[64];
	size_t i, n;
	switch (v.kind) {
		case RVN_MV_STRING: return v.as.string;
		case RVN_MV_BOOL: return v.boolean ? "true" : "false";
		case RVN_MV_NUMBER:
			/* a whole number without its ".0" */
			if (fmod(v.as.number, 1.0) == 0.0 && fabs(v.as.number) < 1e15) snprintf(buffer, sizeof(buffer), "%lld", (long long)v.as.number);
			else snprintf(buffer, sizeof(buffer), "%.15g", v.as.number);
			/* whatever the C locale's decimal point */
			n = strlen(buffer);
			for (i = 0; i < n; i++) if (buffer[i] == ',') buffer[i] = '.';
			return scratch ? rvn_arena_strndup(scratch, buffer, n) : NULL;
		default: return NULL;
	}
}

static const char* rvn_layer_get(const rvn_map_context_t* c, const char* key, const rvn_map_value_t** value) {
	uint32_t i;
	if (!c->feature || !c->layer) return NULL;
	for (i = 0; i + 1 < c->feature->tag_count; i += 2) {
		const char* k = c->layer->keys[c->feature->tags[i]];
		if (strcmp(k, key) == 0) {
			*value = &c->layer->values[c->feature->tags[i + 1]];
			return k;
		}
	}
	return NULL;
}

static rvn_map_value_t rvn_map_interpolate(const rvn_map_value_t* e, const rvn_map_context_t* c);

rvn_map_value_t rvn_map_evaluate(const rvn_map_value_t* expression, const rvn_map_context_t* c) {
	const rvn_map_value_t* x;
	const char* op;
	uint32_t n;
#define ARG(i) rvn_map_evaluate(&x[(i)], c)
	if (!expression) return g_rvn_map_null;
	if (expression->kind != RVN_MV_ARRAY) return *expression;
	if (expression->count == 0 || expression->as.items[0].kind != RVN_MV_STRING) return *expression;
	x = expression->as.items;
	n = expression->count;
	op = x[0].as.string;
	switch (op[0]) {
		case 'l':
			if (strcmp(op, "literal") == 0) return n > 1 ? x[1] : g_rvn_map_null;
			break;
		case 'g':
			if (strcmp(op, "get") == 0 && n > 1) {
				rvn_map_value_t key = ARG(1);
				const rvn_map_value_t* value;
				if (key.kind == RVN_MV_STRING && rvn_layer_get(c, key.as.string, &value)) return *value;
				return g_rvn_map_null;
			}
			if (strcmp(op, "geometry-type") == 0) {
				static const char* const names[] = { "Unknown", "Point", "LineString", "Polygon" };
				return rvn_mv_string(names[c->feature ? c->feature->type : 0]);
			}
			break;
		case 'h':
			if (strcmp(op, "has") == 0 && n > 1) {
				rvn_map_value_t key = ARG(1);
				const rvn_map_value_t* value;
				return rvn_mv_bool(key.kind == RVN_MV_STRING && rvn_layer_get(c, key.as.string, &value));
			}
			break;
		case 'z':
			if (strcmp(op, "zoom") == 0) return rvn_mv_number(c->zoom);
			break;
		case 't':
			if (strcmp(op, "typeof") == 0 && n > 1) {
				rvn_map_value_t v = ARG(1);
				static const char* const names[] = { "null", "boolean", "number", "string", "array", "object", "object" };
				return rvn_mv_string(names[v.kind]);
			}
			if (strcmp(op, "to-number") == 0 && n > 1) {
				rvn_map_value_t v = ARG(1);
				if (v.kind == RVN_MV_NUMBER) return v;
				if (v.kind == RVN_MV_BOOL) return rvn_mv_number(v.boolean ? 1.0 : 0.0);
				if (v.kind == RVN_MV_STRING) {
					float f;
					return rvn_mv_number(rvn_parse_float(v.as.string, &f) ? (double)f : 0.0);
				}
				return rvn_mv_number(0.0);
			}
			if (strcmp(op, "to-string") == 0 && n > 1) {
				const char* s = rvn_mv_text(ARG(1), c->scratch);
				return rvn_mv_string(s ? s : "");
			}
			break;
		case '!':
			if (op[1] == '\0' && n > 1) return rvn_mv_bool(!rvn_mv_true(ARG(1)));
			if (strcmp(op, "!=") == 0 && n > 2) return rvn_mv_bool(!rvn_mv_same(ARG(1), ARG(2)));
			break;
		case '=':
			if (strcmp(op, "==") == 0 && n > 2) return rvn_mv_bool(rvn_mv_same(ARG(1), ARG(2)));
			break;
		case '<':
		case '>': {
			rvn_map_value_t a, b;
			int cmp;
			if (n < 3) break;
			a = ARG(1);
			b = ARG(2);
			if (a.kind == RVN_MV_NUMBER && b.kind == RVN_MV_NUMBER) cmp = a.as.number < b.as.number ? -1 : a.as.number > b.as.number ? 1 : 0;
			else if (a.kind == RVN_MV_STRING && b.kind == RVN_MV_STRING) cmp = strcmp(a.as.string, b.as.string);
			else return rvn_mv_bool(0);
			if (op[0] == '<') return rvn_mv_bool(op[1] == '=' ? cmp <= 0 : cmp < 0);
			return rvn_mv_bool(op[1] == '=' ? cmp >= 0 : cmp > 0);
		}
		case 'a':
			if (strcmp(op, "all") == 0) {
				uint32_t i;
				for (i = 1; i < n; i++) if (!rvn_mv_true(ARG(i))) return rvn_mv_bool(0);
				return rvn_mv_bool(1);
			}
			if (strcmp(op, "any") == 0) {
				uint32_t i;
				for (i = 1; i < n; i++) if (rvn_mv_true(ARG(i))) return rvn_mv_bool(1);
				return rvn_mv_bool(0);
			}
			break;
		case 'i':
			if (strcmp(op, "in") == 0 && n > 2) {
				rvn_map_value_t needle = ARG(1), haystack = ARG(2);
				if (haystack.kind == RVN_MV_ARRAY) {
					uint32_t i;
					for (i = 0; i < haystack.count; i++) if (rvn_mv_same(haystack.as.items[i], needle)) return rvn_mv_bool(1);
					return rvn_mv_bool(0);
				}
				if (haystack.kind == RVN_MV_STRING && needle.kind == RVN_MV_STRING) return rvn_mv_bool(strstr(haystack.as.string, needle.as.string) != NULL);
				return rvn_mv_bool(0);
			}
			if (strcmp(op, "interpolate") == 0) return rvn_map_interpolate(expression, c);
			break;
		case 'm':
			if (strcmp(op, "match") == 0 && n > 2) {
				rvn_map_value_t input = ARG(1);
				uint32_t i;
				for (i = 2; i + 1 < n; i += 2) {
					const rvn_map_value_t* labels = &x[i];
					int hit = 0;
					if (labels->kind == RVN_MV_ARRAY) {
						uint32_t k;
						for (k = 0; k < labels->count && !hit; k++) hit = rvn_mv_same(labels->as.items[k], input);
					} else {
						hit = rvn_mv_same(*labels, input);
					}
					if (hit) return ARG(i + 1);
				}
				return ARG(n - 1);
			}
			break;
		case 'c':
			if (strcmp(op, "case") == 0) {
				uint32_t i;
				for (i = 1; i + 1 < n; i += 2) if (rvn_mv_true(ARG(i))) return ARG(i + 1);
				return n > 1 ? ARG(n - 1) : g_rvn_map_null;
			}
			if (strcmp(op, "coalesce") == 0) {
				uint32_t i;
				for (i = 1; i < n; i++) {
					rvn_map_value_t v = ARG(i);
					if (v.kind != RVN_MV_NULL) return v;
				}
				return g_rvn_map_null;
			}
			if (strcmp(op, "concat") == 0) {
				rvn_text_t text = { NULL, 0, 0 };
				const char* s;
				uint32_t i;
				for (i = 1; i < n; i++) {
					s = rvn_mv_text(ARG(i), c->scratch);
					if (s) rvn_text_add(&text, s);
				}
				s = c->scratch && text.data ? rvn_arena_strndup(c->scratch, text.data, text.length) : NULL;
				rvn_free(text.data);
				return rvn_mv_string(s ? s : "");
			}
			break;
		case 'u':
		case 'd':
			if ((strcmp(op, "upcase") == 0 || strcmp(op, "downcase") == 0) && n > 1) {
				rvn_map_value_t v = ARG(1);
				char* s;
				uint32_t i;
				if (v.kind != RVN_MV_STRING || !c->scratch) return g_rvn_map_null;
				s = rvn_arena_strndup(c->scratch, v.as.string, v.count);
				if (!s) return g_rvn_map_null;
				/* ASCII only: Raven has no case mapping of its own for other scripts */
				for (i = 0; i < v.count; i++) {
					if (op[0] == 'u' && s[i] >= 'a' && s[i] <= 'z') s[i] = (char)(s[i] - 32);
					else if (op[0] == 'd' && s[i] >= 'A' && s[i] <= 'Z') s[i] = (char)(s[i] + 32);
				}
				return rvn_mv_string(s);
			}
			break;
		case 's':
			if (strcmp(op, "step") == 0 && n > 2) {
				rvn_map_value_t input = ARG(1);
				uint32_t i = 3, result = 2;
				if (input.kind != RVN_MV_NUMBER) return ARG(2);
				while (i + 1 < n && x[i].kind == RVN_MV_NUMBER && input.as.number >= x[i].as.number) {
					result = i + 1;
					i += 2;
				}
				return ARG(result);
			}
			break;
		default:
			break;
	}
	return g_rvn_map_null;
#undef ARG
}

static rvn_map_value_t rvn_map_interpolate(const rvn_map_value_t* e, const rvn_map_context_t* c) {
	const rvn_map_value_t* x = e->as.items;
	uint32_t n = e->count, upper;
	rvn_map_value_t input, a, b;
	double base = 1.0, z0, z1, t;
	float ca[4], cb[4];
	if (n < 5) return g_rvn_map_null;
	if (x[1].kind == RVN_MV_ARRAY && x[1].count >= 2 && x[1].as.items[0].kind == RVN_MV_STRING &&
		strcmp(x[1].as.items[0].as.string, "exponential") == 0 && x[1].as.items[1].kind == RVN_MV_NUMBER) base = x[1].as.items[1].as.number;
	input = rvn_map_evaluate(&x[2], c);
	if (input.kind != RVN_MV_NUMBER) return g_rvn_map_null;
	if (input.as.number <= x[3].as.number) return rvn_map_evaluate(&x[4], c);
	if (input.as.number >= x[n - 2].as.number) return rvn_map_evaluate(&x[n - 1], c);
	for (upper = 5; upper + 1 < n && x[upper].as.number <= input.as.number; upper += 2) {}
	z0 = x[upper - 2].as.number;
	z1 = x[upper].as.number;
	if (fabs(base - 1.0) < 1e-9) t = (input.as.number - z0) / (z1 - z0);
	else t = (pow(base, input.as.number - z0) - 1.0) / (pow(base, z1 - z0) - 1.0);
	a = rvn_map_evaluate(&x[upper - 1], c);
	b = rvn_map_evaluate(&x[upper + 1], c);
	if (a.kind == RVN_MV_NUMBER && b.kind == RVN_MV_NUMBER) return rvn_mv_number(a.as.number + (b.as.number - a.as.number) * t);
	if (rvn_map_parse_color(&a, ca) && rvn_map_parse_color(&b, cb)) {
		rvn_map_value_t v = g_rvn_map_null;
		int i;
		v.kind = RVN_MV_COLOR;
		for (i = 0; i < 4; i++) v.as.color[i] = ca[i] + (cb[i] - ca[i]) * (float)t;
		return v;
	}
	return a;
}

int rvn_map_accepts(const rvn_map_value_t* filter, const rvn_map_context_t* context) {
	return !filter || rvn_mv_true(rvn_map_evaluate(filter, context));
}

/* ---------------------------------------------------------------------------
 *  Colours
 * ------------------------------------------------------------------------- */

static int rvn_hex_digit(char c) {
	if (c >= '0' && c <= '9') return c - '0';
	if (c >= 'a' && c <= 'f') return c - 'a' + 10;
	if (c >= 'A' && c <= 'F') return c - 'A' + 10;
	return -1;
}

static float rvn_color_part(const char* s, size_t length) {
	char buffer[32];
	float f = 0.0f;
	int percent;
	while (length && (*s == ' ' || *s == '\t')) {
		s++;
		length--;
	}
	while (length && (s[length - 1] == ' ' || s[length - 1] == '\t')) length--;
	percent = length && s[length - 1] == '%';
	if (percent) length--;
	if (length >= sizeof(buffer)) return 0.0f;
	memcpy(buffer, s, length);
	buffer[length] = '\0';
	rvn_parse_float(buffer, &f);
	return percent ? f / 100.0f : f;
}

static float rvn_hsl_channel(int n, float h, float s, float l) {
	float k = fmodf((float)n + h / 30.0f, 12.0f);
	float a = s * fminf(l, 1.0f - l);
	return rvn_clampf(l - a * fmaxf(-1.0f, fminf(fminf(k - 3.0f, 9.0f - k), 1.0f)), 0.0f, 1.0f);
}

int rvn_map_parse_color(const rvn_map_value_t* value, float out[4]) {
	const char* s;
	size_t length;
	if (!value) return R_FALSE;
	if (value->kind == RVN_MV_COLOR) {
		memcpy(out, value->as.color, sizeof(float) * 4);
		return R_TRUE;
	}
	if (value->kind != RVN_MV_STRING) return R_FALSE;
	s = value->as.string;
	length = value->count;
	while (length && *s == ' ') {
		s++;
		length--;
	}
	while (length && s[length - 1] == ' ') length--;
	if (length && s[0] == '#') {
		int digits[8], i, count = (int)length - 1;
		if (count != 3 && count != 4 && count != 6 && count != 8) return R_FALSE;
		for (i = 0; i < count; i++) {
			digits[i] = rvn_hex_digit(s[1 + i]);
			if (digits[i] < 0) return R_FALSE;
		}
		if (count <= 4) {
			/* #rgb and #rgba: each digit doubled */
			for (i = 0; i < count; i++) out[i] = (float)(digits[i] * 17) / 255.0f;
		} else {
			for (i = 0; i < count / 2; i++) out[i] = (float)(digits[i * 2] * 16 + digits[i * 2 + 1]) / 255.0f;
		}
		if (count == 3 || count == 6) out[3] = 1.0f;
		return R_TRUE;
	}
	{
		const char* open = memchr(s, '(', length);
		const char* p;
		size_t name_length;
		float parts[4] = { 0.0f, 0.0f, 0.0f, 1.0f };
		int count = 0;
		if (!open || s[length - 1] != ')') return R_FALSE;
		name_length = (size_t)(open - s);
		p = open + 1;
		while (p < s + length - 1 && count < 4) {
			const char* comma = p;
			while (comma < s + length - 1 && *comma != ',') comma++;
			parts[count++] = rvn_color_part(p, (size_t)(comma - p));
			p = comma + 1;
		}
		if (count < 3) return R_FALSE;
		if ((name_length == 3 && rvn_strnicmp(s, "rgb", 3) == 0) || (name_length == 4 && rvn_strnicmp(s, "rgba", 4) == 0)) {
			out[0] = parts[0] / 255.0f;
			out[1] = parts[1] / 255.0f;
			out[2] = parts[2] / 255.0f;
			out[3] = parts[3];
			return R_TRUE;
		}
		if ((name_length == 3 && rvn_strnicmp(s, "hsl", 3) == 0) || (name_length == 4 && rvn_strnicmp(s, "hsla", 4) == 0)) {
			/* the hue has no unit: parsed as it is, not as a percentage */
			out[0] = rvn_hsl_channel(0, parts[0], parts[1], parts[2]);
			out[1] = rvn_hsl_channel(8, parts[0], parts[1], parts[2]);
			out[2] = rvn_hsl_channel(4, parts[0], parts[1], parts[2]);
			out[3] = parts[3];
			return R_TRUE;
		}
	}
	return R_FALSE;
}

/* ---------------------------------------------------------------------------
 *  Layers
 * ------------------------------------------------------------------------- */

rvn_map_value_t rvn_map_layer_paint(const rvn_map_style_layer_t* layer, const char* name, double zoom) {
	rvn_map_context_t c;
	const rvn_map_value_t* v = rvn_map_member(layer->paint, name);
	if (!v) return g_rvn_map_null;
	memset(&c, 0, sizeof(c));
	c.zoom = zoom;
	return rvn_map_evaluate(v, &c);
}

rvn_map_value_t rvn_map_layer_layout(const rvn_map_style_layer_t* layer, const char* name, double zoom) {
	rvn_map_context_t c;
	const rvn_map_value_t* v = rvn_map_member(layer->layout, name);
	if (!v) return g_rvn_map_null;
	memset(&c, 0, sizeof(c));
	c.zoom = zoom;
	return rvn_map_evaluate(v, &c);
}

double rvn_map_layer_number(const rvn_map_style_layer_t* layer, const char* name, double zoom, double fallback) {
	rvn_map_value_t v = rvn_map_layer_paint(layer, name, zoom);
	return v.kind == RVN_MV_NUMBER ? v.as.number : fallback;
}

static int rvn_layer_color(const rvn_map_style_layer_t* layer, const char* name, const char* opacity, double zoom, float out[4]) {
	rvn_map_value_t v = rvn_map_layer_paint(layer, name, zoom);
	if (!rvn_map_parse_color(&v, out)) return R_FALSE;
	if (opacity) out[3] = rvn_clampf(out[3] * (float)rvn_map_layer_number(layer, opacity, zoom, 1.0), 0.0f, 1.0f);
	return R_TRUE;
}

int rvn_map_layer_color(const rvn_map_style_layer_t* layer, double zoom, float out[4]) {
	switch (layer->type) {
		case RVN_MAP_LAYER_FILL: return rvn_layer_color(layer, "fill-color", "fill-opacity", zoom, out);
		/* its roofs, opaque: MapLibre's depth test keeps overlapping parts from showing through
		   each other, which a flat, translucent fill can't */
		case RVN_MAP_LAYER_FILL_EXTRUSION: return rvn_layer_color(layer, "fill-extrusion-color", NULL, zoom, out);
		case RVN_MAP_LAYER_LINE: return rvn_layer_color(layer, "line-color", "line-opacity", zoom, out);
		default: return R_FALSE;
	}
}

int rvn_map_layer_outline_color(const rvn_map_style_layer_t* layer, double zoom, float out[4]) {
	if (layer->type != RVN_MAP_LAYER_FILL || !rvn_map_member(layer->paint, "fill-outline-color")) return rvn_map_layer_color(layer, zoom, out);
	return rvn_layer_color(layer, "fill-outline-color", "fill-opacity", zoom, out);
}

int rvn_map_layer_line_cap(const rvn_map_style_layer_t* layer, double zoom) {
	rvn_map_value_t v = rvn_map_layer_layout(layer, "line-cap", zoom);
	if (v.kind == RVN_MV_STRING && strcmp(v.as.string, "round") == 0) return R_LINE_CAP_ROUND;
	if (v.kind == RVN_MV_STRING && strcmp(v.as.string, "square") == 0) return R_LINE_CAP_SQUARE;
	return R_LINE_CAP_BUTT;
}

int rvn_map_layer_dashes(const rvn_map_style_layer_t* layer, double zoom, float* pattern, int* count, int max) {
	rvn_map_value_t v = rvn_map_layer_paint(layer, "line-dasharray", zoom);
	float sum = 0.0f;
	int i, gaps = 0;
	if (v.kind != RVN_MV_ARRAY || v.count < 2 || (int)v.count > max) return R_FALSE;
	for (i = 0; i < (int)v.count; i++) {
		if (v.as.items[i].kind != RVN_MV_NUMBER) return R_FALSE;
		pattern[i] = (float)v.as.items[i].as.number;
		sum += pattern[i];
		if (i % 2 == 1 && pattern[i] > 0.0f) gaps++;
	}
	/* a pattern of nothing but gaps, or of an empty dash and an empty gap, draws a solid line */
	if (sum <= 0.0f || gaps == 0) return R_FALSE;
	*count = (int)v.count;
	return R_TRUE;
}

/* ---------------------------------------------------------------------------
 *  Symbols: what a layer's labels say
 * ------------------------------------------------------------------------- */

/*
 * Latin Extended-A pairs its letters, capital then small, from an even code
 * point in 0x100-0x137 and 0x14A-0x177, from an odd one in 0x139-0x148 and
 * 0x179-0x17E. Returns 1 for a capital, 0 for a small letter, -1 outside.
 */
static int rvn_map_latin_a_capital(uint32_t c) {
	if ((c >= 0x100 && c <= 0x137 && c != 0x130 && c != 0x131) || (c >= 0x14A && c <= 0x177)) return (c & 1) == 0;
	if ((c >= 0x139 && c <= 0x148) || (c >= 0x179 && c <= 0x17E)) return (c & 1) == 1;
	return -1;
}

/* The upper or lower case of a code point, where Raven knows it. */
static uint32_t rvn_map_case(uint32_t c, int upper) {
	int capital = rvn_map_latin_a_capital(c);
	if (upper) {
		if ((c >= 'a' && c <= 'z') || (c >= 0xE0 && c <= 0xFE && c != 0xF7) || (c >= 0x3B1 && c <= 0x3C9 && c != 0x3C2) || (c >= 0x430 && c <= 0x44F)) return c - 32;
		if (c >= 0x450 && c <= 0x45F) return c - 80;
		if (c == 0xFF) return 0x178;
		if (capital == 0) return c - 1;
	} else {
		if ((c >= 'A' && c <= 'Z') || (c >= 0xC0 && c <= 0xDE && c != 0xD7) || (c >= 0x391 && c <= 0x3A9 && c != 0x3A2) || (c >= 0x410 && c <= 0x42F)) return c + 32;
		if (c >= 0x400 && c <= 0x40F) return c + 80;
		if (c == 0x178) return 0xFF;
		if (capital == 1) return c + 1;
	}
	return c;
}

char* rvn_map_change_case(rvn_arena_t* arena, const char* text, int upper) {
	size_t length = strlen(text), n = 0;
	const char* p = text;
	const char* end = text + length;
	/* a changed code point never takes more bytes than it had: at most as long */
	char* out = (char*)rvn_arena_alloc(arena, length + 1);
	if (!out) return NULL;
	while (p < end) {
		const char* before = p;
		uint32_t c = rvn_utf8_decode(&p, end);
		uint32_t mapped = rvn_map_case(c, upper);
		char bytes[4];
		int k = rvn_utf8_encode(mapped, bytes);
		if (mapped == c || k != (int)(p - before)) {
			memcpy(out + n, before, (size_t)(p - before));
			n += (size_t)(p - before);
		} else {
			memcpy(out + n, bytes, (size_t)k);
			n += (size_t)k;
		}
	}
	out[n] = '\0';
	return out;
}

const char* rvn_map_label_text(const rvn_map_style_layer_t* layer, const rvn_map_context_t* context) {
	const rvn_map_value_t* field = rvn_map_member(layer->layout, "text-field");
	rvn_map_value_t value, transform;
	const char* text;
	size_t start, end;
	if (!field) return NULL;
	value = rvn_map_evaluate(field, context);
	if (value.kind == RVN_MV_NULL) return NULL;
	if (value.kind == RVN_MV_STRING && strchr(value.as.string, '{')) {
		/* a legacy field: "{name}" tokens replaced by the feature's properties */
		rvn_text_t out = { NULL, 0, 0 };
		const char* p = value.as.string;
		while (*p) {
			const char* open = strchr(p, '{');
			const char* close = open ? strchr(open, '}') : NULL;
			char key[128];
			const rvn_map_value_t* property;
			if (!open || !close) {
				rvn_text_add(&out, p);
				break;
			}
			rvn_text_append(&out, p, (size_t)(open - p));
			if ((size_t)(close - open - 1) < sizeof(key)) {
				memcpy(key, open + 1, (size_t)(close - open - 1));
				key[close - open - 1] = '\0';
				if (rvn_layer_get(context, key, &property)) {
					const char* s = rvn_mv_text(*property, context->scratch);
					if (s) rvn_text_add(&out, s);
				}
			}
			p = close + 1;
		}
		text = out.data ? rvn_arena_strndup(context->scratch, out.data, out.length) : "";
		rvn_free(out.data);
	} else {
		text = rvn_mv_text(value, context->scratch);
	}
	if (!text) return NULL;
	/* trimmed */
	start = 0;
	end = strlen(text);
	while (start < end && (text[start] == ' ' || text[start] == '\n' || text[start] == '\t')) start++;
	while (end > start && (text[end - 1] == ' ' || text[end - 1] == '\n' || text[end - 1] == '\t')) end--;
	if (start == end) return NULL;
	if (start > 0 || text[end] != '\0') text = rvn_arena_strndup(context->scratch, text + start, end - start);
	transform = rvn_map_layer_layout(layer, "text-transform", context->zoom);
	if (transform.kind == RVN_MV_STRING && strcmp(transform.as.string, "uppercase") == 0) text = rvn_map_change_case(context->scratch, text, 1);
	else if (transform.kind == RVN_MV_STRING && strcmp(transform.as.string, "lowercase") == 0) text = rvn_map_change_case(context->scratch, text, 0);
	return text;
}

const char* rvn_map_icon_name(const rvn_map_style_layer_t* layer, const rvn_map_context_t* context) {
	const rvn_map_value_t* image = rvn_map_member(layer->layout, "icon-image");
	const char* name;
	if (!image) return NULL;
	name = rvn_mv_text(rvn_map_evaluate(image, context), context->scratch);
	return name && name[0] ? name : NULL;
}

/* ---------------------------------------------------------------------------
 *  The style
 * ------------------------------------------------------------------------- */

static rvn_map_layer_type_t rvn_layer_type(const char* type) {
	if (!type) return RVN_MAP_LAYER_OTHER;
	if (strcmp(type, "background") == 0) return RVN_MAP_LAYER_BACKGROUND;
	if (strcmp(type, "fill") == 0) return RVN_MAP_LAYER_FILL;
	if (strcmp(type, "fill-extrusion") == 0) return RVN_MAP_LAYER_FILL_EXTRUSION;
	if (strcmp(type, "line") == 0) return RVN_MAP_LAYER_LINE;
	if (strcmp(type, "symbol") == 0) return RVN_MAP_LAYER_SYMBOL;
	return RVN_MAP_LAYER_OTHER;
}

static void rvn_layer_read(rvn_map_style_layer_t* layer, const rvn_map_value_t* json) {
	const rvn_map_value_t* antialias;
	memset(layer, 0, sizeof(*layer));
	layer->id = rvn_map_member_string(json, "id");
	if (!layer->id) layer->id = "";
	layer->type = rvn_layer_type(rvn_map_member_string(json, "type"));
	layer->source_layer = rvn_map_member_string(json, "source-layer");
	layer->min_zoom = rvn_map_member_number(json, "minzoom", 0.0);
	layer->max_zoom = rvn_map_member_number(json, "maxzoom", 24.0);
	layer->filter = rvn_map_member(json, "filter");
	layer->paint = rvn_map_member(json, "paint");
	layer->layout = rvn_map_member(json, "layout");
	layer->is_fill = layer->type == RVN_MAP_LAYER_FILL || layer->type == RVN_MAP_LAYER_FILL_EXTRUSION;
	antialias = rvn_map_member(layer->paint, "fill-antialias");
	layer->antialias = layer->is_fill && !(antialias && antialias->kind == RVN_MV_BOOL && !antialias->boolean);
}

static int rvn_layer_visible(const rvn_map_style_layer_t* layer) {
	const char* visibility = rvn_map_member_string(layer->layout, "visibility");
	return !visibility || strcmp(visibility, "none") != 0;
}

int rvn_map_style_parse(rvn_map_style_t* style, const char* json, size_t length, const char* source) {
	const rvn_map_value_t* layers;
	uint32_t i;
	int background = R_FALSE;
	memset(style, 0, sizeof(*style));
	rvn_arena_init(&style->arena, 64 * 1024);
	if (!rvn_map_json_parse(&style->arena, json, length, &style->root)) {
		rvn_map_style_free(style);
		return R_FALSE;
	}
	style->background[0] = style->background[1] = style->background[2] = style->background[3] = 1.0f;
	if (!source) {
		/* the first vector source */
		const rvn_map_value_t* sources = rvn_map_member(&style->root, "sources");
		for (i = 0; sources && sources->kind == RVN_MV_OBJECT && i < sources->count; i++) {
			const char* type = rvn_map_member_string(&sources->as.members[i].value, "type");
			if (type && strcmp(type, "vector") == 0) {
				source = sources->as.members[i].key;
				break;
			}
		}
	}
	layers = rvn_map_member(&style->root, "layers");
	if (!layers || layers->kind != RVN_MV_ARRAY) return R_TRUE;
	style->layers = (rvn_map_style_layer_t*)rvn_arena_alloc(&style->arena, sizeof(rvn_map_style_layer_t) * (layers->count ? layers->count : 1));
	style->symbols = (rvn_map_style_layer_t*)rvn_arena_alloc(&style->arena, sizeof(rvn_map_style_layer_t) * (layers->count ? layers->count : 1));
	for (i = 0; i < layers->count; i++) {
		const rvn_map_value_t* json_layer = &layers->as.items[i];
		const char* layer_source = rvn_map_member_string(json_layer, "source");
		rvn_map_style_layer_t layer;
		rvn_layer_read(&layer, json_layer);
		if (layer.type == RVN_MAP_LAYER_BACKGROUND && !background) {
			background = rvn_layer_color(&layer, "background-color", "background-opacity", 14.0, style->background);
			continue;
		}
		if (!rvn_layer_visible(&layer) || !layer_source || !source || strcmp(layer_source, source) != 0) continue;
		if ((layer.type == RVN_MAP_LAYER_FILL && !rvn_map_member(layer.paint, "fill-pattern")) ||
			layer.type == RVN_MAP_LAYER_FILL_EXTRUSION || layer.type == RVN_MAP_LAYER_LINE) {
			style->layers[style->layer_count++] = layer;
		}
		/* labels and icons, at points and along lines */
		if (layer.type == RVN_MAP_LAYER_SYMBOL && (rvn_map_member(layer.layout, "text-field") || rvn_map_member(layer.layout, "icon-image"))) {
			style->symbols[style->symbol_count++] = layer;
		}
	}
	return R_TRUE;
}

void rvn_map_style_free(rvn_map_style_t* style) {
	rvn_arena_free(&style->arena);
	memset(style, 0, sizeof(*style));
}

rvn_map_style_t* rvn_map_style_create(const char* json, size_t length, const char* source) {
	rvn_map_style_t* style = (rvn_map_style_t*)rvn_alloc(sizeof(rvn_map_style_t));
	if (!rvn_map_style_parse(style, json, length, source)) {
		rvn_free(style);
		return NULL;
	}
	rvn_atomic_store(&style->references, 1);
	return style;
}

void rvn_map_style_retain(rvn_map_style_t* style) {
	rvn_atomic_add(&style->references, 1);
}

void rvn_map_style_release(rvn_map_style_t* style) {
	if (!style || rvn_atomic_add(&style->references, -1) != 0) return;
	rvn_map_style_free(style);
	rvn_free(style);
}
