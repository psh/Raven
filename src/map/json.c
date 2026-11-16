/**
 * Raven - map/json.c
 * A small JSON reader for style files: values in an arena, the document kept
 * whole so that expressions are evaluated on it as written.
 */

#include "map.h"

#include <math.h>
#include <string.h>

typedef struct rvn_json_reader_t {
	rvn_arena_t* arena;
	const char* text;
	size_t pos, length;
	int depth;
} rvn_json_reader_t;

/* deeper documents are refused rather than overflowing the stack */
#define RVN_JSON_MAX_DEPTH 256

static void rvn_json_space(rvn_json_reader_t* r) {
	while (r->pos < r->length) {
		char c = r->text[r->pos];
		if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
		r->pos++;
	}
}

static int rvn_json_value(rvn_json_reader_t* r, rvn_map_value_t* out);

static int rvn_json_hex4(rvn_json_reader_t* r, uint32_t* out) {
	uint32_t v = 0;
	int i;
	if (r->pos + 4 > r->length) return R_FALSE;
	for (i = 0; i < 4; i++) {
		char c = r->text[r->pos++];
		v <<= 4;
		if (c >= '0' && c <= '9') v |= (uint32_t)(c - '0');
		else if (c >= 'a' && c <= 'f') v |= (uint32_t)(c - 'a' + 10);
		else if (c >= 'A' && c <= 'F') v |= (uint32_t)(c - 'A' + 10);
		else return R_FALSE;
	}
	*out = v;
	return R_TRUE;
}

static int rvn_json_string(rvn_json_reader_t* r, const char** out, uint32_t* out_length) {
	size_t start, end, n = 0;
	char* s;
	if (r->pos >= r->length || r->text[r->pos] != '"') return R_FALSE;
	start = ++r->pos;
	/* the common case, no escape: copied as it is */
	end = start;
	while (end < r->length && r->text[end] != '"' && r->text[end] != '\\') end++;
	if (end < r->length && r->text[end] == '"') {
		*out = rvn_arena_strndup(r->arena, r->text + start, end - start);
		*out_length = (uint32_t)(end - start);
		r->pos = end + 1;
		return *out != NULL;
	}
	/* escaped: never longer than the source */
	end = start;
	while (end < r->length && r->text[end] != '"') end += r->text[end] == '\\' ? 2 : 1;
	if (end >= r->length) return R_FALSE;
	s = (char*)rvn_arena_alloc(r->arena, end - start + 1);
	if (!s) return R_FALSE;
	while (r->pos < end) {
		char c = r->text[r->pos++];
		if (c != '\\') {
			s[n++] = c;
			continue;
		}
		c = r->text[r->pos++];
		switch (c) {
			case 'n': s[n++] = '\n'; break;
			case 't': s[n++] = '\t'; break;
			case 'r': s[n++] = '\r'; break;
			case 'b': s[n++] = '\b'; break;
			case 'f': s[n++] = '\f'; break;
			case 'u': {
				uint32_t code, low;
				if (!rvn_json_hex4(r, &code)) return R_FALSE;
				/* a surrogate pair is one code point */
				if (code >= 0xD800 && code < 0xDC00 && r->pos + 6 <= end && r->text[r->pos] == '\\' && r->text[r->pos + 1] == 'u') {
					r->pos += 2;
					if (!rvn_json_hex4(r, &low)) return R_FALSE;
					code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00);
				}
				n += (size_t)rvn_utf8_encode(code, s + n);
				break;
			}
			default: s[n++] = c; break;
		}
	}
	s[n] = '\0';
	r->pos = end + 1;
	*out = s;
	*out_length = (uint32_t)n;
	return R_TRUE;
}

static int rvn_json_number(rvn_json_reader_t* r, double* out) {
	const char* p = r->text + r->pos;
	const char* end = r->text + r->length;
	double result = 0.0;
	int negative = 0, digits = 0;
	if (p < end && *p == '-') {
		negative = 1;
		p++;
	}
	while (p < end && *p >= '0' && *p <= '9') {
		result = result * 10.0 + (double)(*p++ - '0');
		digits++;
	}
	if (p < end && *p == '.') {
		double scale = 1.0;
		p++;
		while (p < end && *p >= '0' && *p <= '9') {
			scale *= 0.1;
			result += (double)(*p++ - '0') * scale;
			digits++;
		}
	}
	if (!digits) return R_FALSE;
	if (p < end && (*p == 'e' || *p == 'E')) {
		int exponent = 0, exponent_negative = 0;
		p++;
		if (p < end && (*p == '-' || *p == '+')) exponent_negative = *p++ == '-';
		while (p < end && *p >= '0' && *p <= '9') {
			if (exponent < 400) exponent = exponent * 10 + (*p - '0');
			p++;
		}
		result *= pow(10.0, exponent_negative ? -exponent : exponent);
	}
	r->pos = (size_t)(p - r->text);
	*out = negative ? -result : result;
	return R_TRUE;
}

static int rvn_json_word(rvn_json_reader_t* r, const char* word) {
	size_t n = strlen(word);
	if (r->pos + n > r->length || memcmp(r->text + r->pos, word, n) != 0) return R_FALSE;
	r->pos += n;
	return R_TRUE;
}

/* Arrays and objects are read into a growing buffer, then copied to the arena at their size. */
static int rvn_json_array(rvn_json_reader_t* r, rvn_map_value_t* out) {
	rvn_map_value_t* items = NULL;
	uint32_t count = 0, cap = 0;
	r->pos++;
	rvn_json_space(r);
	if (r->pos < r->length && r->text[r->pos] == ']') {
		r->pos++;
		out->kind = RVN_MV_ARRAY;
		return R_TRUE;
	}
	for (;;) {
		RVN_ARRAY_RESERVE(items, cap, count + 1);
		if (!rvn_json_value(r, &items[count])) goto fail;
		count++;
		rvn_json_space(r);
		if (r->pos >= r->length) goto fail;
		if (r->text[r->pos] == ',') {
			r->pos++;
			continue;
		}
		if (r->text[r->pos++] != ']') goto fail;
		break;
	}
	out->kind = RVN_MV_ARRAY;
	out->count = count;
	{
		rvn_map_value_t* copy = (rvn_map_value_t*)rvn_arena_alloc(r->arena, sizeof(*copy) * count);
		if (!copy) goto fail;
		memcpy(copy, items, sizeof(*copy) * count);
		out->as.items = copy;
	}
	rvn_free(items);
	return R_TRUE;
fail:
	rvn_free(items);
	return R_FALSE;
}

static int rvn_json_object(rvn_json_reader_t* r, rvn_map_value_t* out) {
	rvn_map_member_t* members = NULL;
	uint32_t count = 0, cap = 0, length;
	r->pos++;
	rvn_json_space(r);
	if (r->pos < r->length && r->text[r->pos] == '}') {
		r->pos++;
		out->kind = RVN_MV_OBJECT;
		return R_TRUE;
	}
	for (;;) {
		RVN_ARRAY_RESERVE(members, cap, count + 1);
		rvn_json_space(r);
		if (!rvn_json_string(r, &members[count].key, &length)) goto fail;
		rvn_json_space(r);
		if (r->pos >= r->length || r->text[r->pos++] != ':') goto fail;
		if (!rvn_json_value(r, &members[count].value)) goto fail;
		count++;
		rvn_json_space(r);
		if (r->pos >= r->length) goto fail;
		if (r->text[r->pos] == ',') {
			r->pos++;
			continue;
		}
		if (r->text[r->pos++] != '}') goto fail;
		break;
	}
	out->kind = RVN_MV_OBJECT;
	out->count = count;
	{
		rvn_map_member_t* copy = (rvn_map_member_t*)rvn_arena_alloc(r->arena, sizeof(*copy) * count);
		if (!copy) goto fail;
		memcpy(copy, members, sizeof(*copy) * count);
		out->as.members = copy;
	}
	rvn_free(members);
	return R_TRUE;
fail:
	rvn_free(members);
	return R_FALSE;
}

static int rvn_json_value(rvn_json_reader_t* r, rvn_map_value_t* out) {
	int ok;
	memset(out, 0, sizeof(*out));
	rvn_json_space(r);
	if (r->pos >= r->length) return R_FALSE;
	if (++r->depth > RVN_JSON_MAX_DEPTH) return R_FALSE;
	switch (r->text[r->pos]) {
		case '{': ok = rvn_json_object(r, out); break;
		case '[': ok = rvn_json_array(r, out); break;
		case '"':
			out->kind = RVN_MV_STRING;
			ok = rvn_json_string(r, &out->as.string, &out->count);
			break;
		case 't':
			out->kind = RVN_MV_BOOL;
			out->boolean = 1;
			ok = rvn_json_word(r, "true");
			break;
		case 'f':
			out->kind = RVN_MV_BOOL;
			ok = rvn_json_word(r, "false");
			break;
		case 'n':
			ok = rvn_json_word(r, "null");
			break;
		default:
			out->kind = RVN_MV_NUMBER;
			ok = rvn_json_number(r, &out->as.number);
			break;
	}
	r->depth--;
	return ok;
}

int rvn_map_json_parse(rvn_arena_t* arena, const char* text, size_t length, rvn_map_value_t* out) {
	rvn_json_reader_t r;
	r.arena = arena;
	r.text = text;
	r.pos = 0;
	r.length = length;
	r.depth = 0;
	if (!rvn_json_value(&r, out)) return R_FALSE;
	rvn_json_space(&r);
	return r.pos == r.length;
}

const rvn_map_value_t* rvn_map_member(const rvn_map_value_t* object, const char* key) {
	uint32_t i;
	if (!object || object->kind != RVN_MV_OBJECT) return NULL;
	for (i = 0; i < object->count; i++) {
		if (strcmp(object->as.members[i].key, key) == 0) return &object->as.members[i].value;
	}
	return NULL;
}

const char* rvn_map_member_string(const rvn_map_value_t* object, const char* key) {
	const rvn_map_value_t* v = rvn_map_member(object, key);
	return v && v->kind == RVN_MV_STRING ? v->as.string : NULL;
}

double rvn_map_member_number(const rvn_map_value_t* object, const char* key, double fallback) {
	const rvn_map_value_t* v = rvn_map_member(object, key);
	return v && v->kind == RVN_MV_NUMBER ? v->as.number : fallback;
}
