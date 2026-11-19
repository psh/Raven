/**
 * Raven - render/image_decode.c
 * The only file that includes stb_image and stb_image_write. Compiled as
 * static functions so they never clash with another copy of stb linked into
 * the application.
 * Thread-safe: called from the job system for asynchronous textures.
 */

#include "renderer.h"
#include "../core/fs.h"

#if defined(RAVEN_WITH_STB_IMAGE)

#define STB_IMAGE_STATIC
#define STBI_NO_STDIO
#define STBI_NO_HDR
#define STBI_NO_LINEAR
#define STBI_NO_FAILURE_STRINGS
#define STBI_MALLOC(size) rvn_alloc(size)
#define STBI_REALLOC(ptr, size) rvn_realloc(ptr, size)
#define STBI_FREE(ptr) rvn_free(ptr)
#define STB_IMAGE_IMPLEMENTATION

#define STB_IMAGE_WRITE_STATIC
#define STBI_WRITE_NO_STDIO
#define STBIW_MALLOC(size) rvn_alloc(size)
#define STBIW_REALLOC(ptr, size) rvn_realloc(ptr, size)
#define STBIW_FREE(ptr) rvn_free(ptr)
#define STB_IMAGE_WRITE_IMPLEMENTATION

#if defined(__GNUC__) || defined(__clang__)
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-function"
#pragma GCC diagnostic ignored "-Wsign-compare"
#pragma GCC diagnostic ignored "-Wcast-qual"
#pragma GCC diagnostic ignored "-Wunused-parameter"
#elif defined(_MSC_VER)
#pragma warning(push, 0)
#endif

#include "stb/stb_image.h"
#include "stb/stb_image_write.h"

#if defined(__GNUC__) || defined(__clang__)
#pragma GCC diagnostic pop
#elif defined(_MSC_VER)
#pragma warning(pop)
#endif

#if defined(__GNUC__) && !defined(__clang__)
/* GCC reports this when it inlines stb_image into the function below; the
 * fields it names are only read when decoding through callbacks, which is not used */
#pragma GCC diagnostic ignored "-Wmaybe-uninitialized"
#endif

static uint8_t* rvn_bitmap_decode(const uint8_t* data, size_t size, int* width, int* height) {
	int w = 0, h = 0, channels = 0;
	uint8_t* pixels;
	if (!data || size == 0 || size > 0x7FFFFFFFu) return NULL;
	pixels = stbi_load_from_memory(data, (int)size, &w, &h, &channels, 4);
	if (!pixels) return NULL;
	*width = w;
	*height = h;
	return pixels;
}

uint8_t* rvn_inflate(const uint8_t* data, size_t size, int zlib_header, size_t size_hint, size_t* out_size) {
	int length = 0;
	char* out;
	if (!data || size == 0 || size > 0x7FFFFFFFu) return NULL;
	/* the hint is only the first allocation, grown as needed: a large one is not trusted */
	if (size_hint == 0 || size_hint > 64u * 1024 * 1024) size_hint = 16384;
	out = stbi_zlib_decode_malloc_guesssize_headerflag((const char*)data, (int)size, (int)size_hint, &length, zlib_header);
	if (!out) return NULL;
	*out_size = (size_t)length;
	return (uint8_t*)out;
}

static uint8_t* rvn_png_encode(const uint8_t* rgba, int width, int height, size_t* png_size) {
	int length = 0;
	/* (the pixels are only read: stb_image_write takes them without const) */
	uint8_t* png = stbi_write_png_to_mem((const unsigned char*)rgba, width * 4, width, height, 4, &length);
	if (!png) return NULL;
	*png_size = (size_t)length;
	return png;
}

#else /* RAVEN_WITH_STB_IMAGE */

uint8_t* rvn_inflate(const uint8_t* data, size_t size, int zlib_header, size_t size_hint, size_t* out_size) {
	RVN_UNUSED(data);
	RVN_UNUSED(size);
	RVN_UNUSED(zlib_header);
	RVN_UNUSED(size_hint);
	RVN_UNUSED(out_size);
	return NULL;
}

static uint8_t* rvn_bitmap_decode(const uint8_t* data, size_t size, int* width, int* height) {
	RVN_UNUSED(data);
	RVN_UNUSED(size);
	RVN_UNUSED(width);
	RVN_UNUSED(height);
	return NULL;
}

#endif

/* Encodes, or reports why not; `function` names the caller in the report. */
static uint8_t* rvn_png_encode_checked(const char* function, const uint8_t* rgba, int width, int height, size_t* png_size) {
	*png_size = 0;
#if !defined(RAVEN_WITH_STB_IMAGE)
	RVN_UNUSED(rgba);
	RVN_UNUSED(width);
	RVN_UNUSED(height);
	rvn_error(R_ERROR_INVALID_OPERATION, R_SEVERITY_ERROR, "%s: Raven was built without RAVEN_WITH_STB_IMAGE", function);
	return NULL;
#else
	{
		uint8_t* png;
		/* (stb_image_write counts in int: a row and the whole image must fit) */
		if (!rgba || width <= 0 || height <= 0 || width > 0x7FFFFFFF / 4 / height) {
			rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "%s: invalid image %d x %d", function, width, height);
			return NULL;
		}
		png = rvn_png_encode(rgba, width, height, png_size);
		if (!png) rvn_error(R_ERROR_OUT_OF_MEMORY, R_SEVERITY_ERROR, "%s: cannot encode %d x %d pixels", function, width, height);
		return png;
	}
#endif
}

uint8_t* Rimage_encode_png(const uint8_t* rgba, int width, int height, size_t* png_size) {
	size_t size = 0;
	uint8_t* png = rvn_png_encode_checked("Rimage_encode_png", rgba, width, height, &size);
	if (png_size) *png_size = size;
	return png;
}

int Rimage_save_png(const uint8_t* rgba, int width, int height, const char* path) {
	size_t size = 0;
	uint8_t* png;
	int ok;
	if (!path || !path[0]) {
		rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "Rimage_save_png: no path");
		return R_FALSE;
	}
	png = rvn_png_encode_checked("Rimage_save_png", rgba, width, height, &size);
	if (!png) return R_FALSE;
	ok = rvn_write_file(path, png, size);
	rvn_free(png);
	if (!ok) rvn_error(R_ERROR_IO, R_SEVERITY_ERROR, "Rimage_save_png: cannot write %s", path);
	return ok;
}

uint8_t* rvn_image_decode_at(const uint8_t* data, size_t size, float svg_scale, int* width, int* height, float* density) {
	if (density) *density = 1.0f;
	if (data && rvn_svg_detect(data, size)) {
		float scale = svg_scale > 0.0f ? svg_scale : RVN_SVG_DENSITY;
		if (density) *density = scale;
		return rvn_svg_decode(data, size, scale, width, height);
	}
	return rvn_bitmap_decode(data, size, width, height);
}

uint8_t* rvn_image_decode(const uint8_t* data, size_t size, int* width, int* height) {
	return rvn_image_decode_at(data, size, 0.0f, width, height, NULL);
}
