/**
 * Raven tests - mask textures (Rtexture_create_mask_from_file_async).
 *
 * Headless, with a backend of the test's own that records the textures Raven
 * makes, so the pixels a mask is uploaded with can be read: its alpha only, one
 * byte per pixel, in R_TEXTURE_ALPHA8.
 */

#include "test.h"

#include <stdlib.h>

/* ---------------------------------------------------------------------------
 *  A backend that records the textures
 * ------------------------------------------------------------------------- */

typedef struct Recorded {
	Rid id;
	int width, height;
	RTextureFormat format;
	uint8_t* pixels;
} Recorded;

#define MAX_RECORDED 64
static Recorded g_recorded[MAX_RECORDED];
static int g_recorded_count;

static int record_init(const RBackendInitInfo* info) { (void)info; return 1; }
static void record_shutdown(void) {}
static int record_create_surface(Rid window, const RNativeWindow* native, Ruint width, Ruint height) {
	(void)window; (void)native; (void)width; (void)height;
	return 1;
}
static void record_destroy_surface(Rid window) { (void)window; }

static int record_create_texture(Rid texture, const RTextureDesc* desc, const void* pixels) {
	Recorded* record;
	size_t size;
	if (g_recorded_count == MAX_RECORDED) return 1;
	record = &g_recorded[g_recorded_count++];
	record->id = texture;
	record->width = (int)desc->width;
	record->height = (int)desc->height;
	record->format = desc->format;
	record->pixels = NULL;
	if (pixels) {
		size = (size_t)desc->width * (size_t)desc->height * (desc->format == R_TEXTURE_ALPHA8 ? 1u : 4u);
		record->pixels = (uint8_t*)malloc(size);
		memcpy(record->pixels, pixels, size);
	}
	return 1;
}
static void record_update_texture(Rid texture, Ruint x, Ruint y, Ruint width, Ruint height, const void* pixels) {
	(void)texture; (void)x; (void)y; (void)width; (void)height; (void)pixels;
}
static void record_set_filter(Rid texture, RFilterType filter) { (void)texture; (void)filter; }
static void record_destroy_texture(Rid texture) { (void)texture; }
static void record_render_frame(const RDrawData* data, RCapture* capture) { (void)data; (void)capture; }
static void record_wait_idle(void) {}

/* The last texture made for `id` with pixels, or NULL. */
static const Recorded* last_upload(Rid id) {
	int i;
	for (i = g_recorded_count - 1; i >= 0; i--) {
		if (g_recorded[i].id == id && g_recorded[i].pixels) return &g_recorded[i];
	}
	return NULL;
}

static int uploads_of(Rid id) {
	int i, count = 0;
	for (i = 0; i < g_recorded_count; i++) count += g_recorded[i].id == id && g_recorded[i].pixels;
	return count;
}

/* The coverage at (x, y) as a fraction of the mask's size, from the top left. */
static int coverage(const Recorded* mask, float fx, float fy) {
	int x = (int)(fx * (float)mask->width), y = (int)(fy * (float)mask->height);
	return mask->pixels[(size_t)y * (size_t)mask->width + (size_t)x];
}

static void write_file(const char* path, const char* text) {
	FILE* file = fopen(path, "wb");
	if (!file) return;
	fputs(text, file);
	fclose(file);
}

static Rid g_window;

/*
 * Decoded, then given to the backend: Raven hands textures to it on its render
 * thread, at a frame, so the test draws frames until `count` uploads arrived.
 */
static void wait_uploads(Rid texture, int count) {
	int i;
	Rtexture_wait(texture);
	for (i = 0; i < 400 && uploads_of(texture) < count; i++) {
		Rdraw_frame();
		Rwait_events(0.005);
	}
}

static void wait_uploaded(Rid texture) {
	wait_uploads(texture, 1);
}

/* ---------------------------------------------------------------------------
 *  Fixtures: the shapes of Google's icons
 * ------------------------------------------------------------------------- */

/*
 * A Material Symbol as fonts.google.com/icons downloads it: one path in the
 * 0 -960 960 960 box, filled with the site's grey (#e3e3e3). Here a square
 * covering the middle half.
 */
static const char g_symbol[] =
	"<svg xmlns=\"http://www.w3.org/2000/svg\" height=\"24px\" viewBox=\"0 -960 960 960\" width=\"24px\" fill=\"#e3e3e3\">"
	"<path d=\"M240-720h480v480H240v-480Z\"/></svg>";

/*
 * A two-tone Material Icon: an invisible frame (fill="none"), a solid layer and
 * a lighter one at opacity .3. Here a solid bar over the top of a light square.
 */
static const char g_two_tone[] =
	"<svg xmlns=\"http://www.w3.org/2000/svg\" height=\"24\" viewBox=\"0 0 24 24\" width=\"24\">"
	"<path d=\"M0 0h24v24H0V0z\" fill=\"none\"/>"
	"<path d=\"M4 8h16v12H4z\" opacity=\".3\"/>"
	"<path d=\"M4 4h16v4H4z\"/></svg>";

/* ---------------------------------------------------------------------------
 *  Tests
 * ------------------------------------------------------------------------- */

static void test_symbol(void) {
	const Recorded* mask;
	Rid texture;
	write_file("test_mask_symbol.svg", g_symbol);
	texture = Rtexture_create_mask_from_file_async("test_mask_symbol.svg");
	CHECK(texture != R_INVALID_ID);
	wait_uploaded(texture);
	CHECK(Rtexture_is_ready(texture));
	mask = last_upload(texture);
	CHECK(mask != NULL);
	if (mask) {
		CHECK(mask->format == R_TEXTURE_ALPHA8);
		/* an SVG starts at 2 pixels per unit: 24 units are 48 pixels */
		CHECK(mask->width == 48 && mask->height == 48);
		/* the grey fill is gone: coverage only, full where the icon is, none outside */
		CHECK(coverage(mask, 0.5f, 0.5f) == 255);
		CHECK(coverage(mask, 0.1f, 0.1f) == 0);
		CHECK(coverage(mask, 0.9f, 0.5f) == 0);
	}
	Rtexture_destroy(texture);
	remove("test_mask_symbol.svg");
}

static void test_two_tone(void) {
	const Recorded* mask;
	Rid texture;
	write_file("test_mask_two_tone.svg", g_two_tone);
	texture = Rtexture_create_mask_from_file_async("test_mask_two_tone.svg");
	wait_uploaded(texture);
	mask = last_upload(texture);
	CHECK(mask != NULL);
	if (mask) {
		int light = coverage(mask, 0.5f, 0.6f);
		CHECK(mask->format == R_TEXTURE_ALPHA8);
		CHECK(coverage(mask, 0.5f, 0.25f) == 255);
		/* the lighter layer: the tint at .3 */
		CHECK(light >= 74 && light <= 80);
		/* the frame filled with none covers nothing */
		CHECK(coverage(mask, 0.05f, 0.05f) == 0);
		CHECK(coverage(mask, 0.5f, 0.95f) == 0);
	}
	Rtexture_destroy(texture);
	remove("test_mask_two_tone.svg");
}

static void test_raster(void) {
	/* 2 x 2: opaque red, half see-through green, see-through blue, quarter white */
	static const uint8_t rgba[16] = {
		255, 0, 0, 255, 0, 255, 0, 128,
		0, 0, 255, 0, 255, 255, 255, 64,
	};
	const Recorded* mask;
	Rid texture;
	CHECK(Rimage_save_png(rgba, 2, 2, "test_mask_raster.png"));
	texture = Rtexture_create_mask_from_file_async("test_mask_raster.png");
	wait_uploaded(texture);
	mask = last_upload(texture);
	CHECK(mask != NULL);
	if (mask) {
		CHECK(mask->format == R_TEXTURE_ALPHA8);
		CHECK(mask->width == 2 && mask->height == 2);
		/* the alpha channel, whatever the colour */
		CHECK(mask->pixels[0] == 255);
		CHECK(mask->pixels[1] == 128);
		CHECK(mask->pixels[2] == 0);
		CHECK(mask->pixels[3] == 64);
	}
	/* a mask can't be updated with colour pixels */
	EXPECT_ERRORS_BEGIN();
	Rtexture_update(texture, rgba, 2, 2);
	EXPECT_ERRORS_END(1);
	Rtexture_destroy(texture);
	remove("test_mask_raster.png");
}

static void test_missing_file(void) {
	Rid texture;
	EXPECT_ERRORS_BEGIN();
	texture = Rtexture_create_mask_from_file_async("test_mask_no_such_file.svg");
	CHECK(texture != R_INVALID_ID);
	Rtexture_wait(texture);
	Rdraw_frame();
	EXPECT_ERRORS_END(1);
	CHECK(Rtexture_is_ready(texture));
	CHECK(uploads_of(texture) == 0);
	Rtexture_destroy(texture);
	EXPECT_ERRORS_BEGIN();
	CHECK(Rtexture_create_mask_from_file_async(NULL) == R_INVALID_ID);
	EXPECT_ERRORS_END(1);
}

/* An SVG mask shown bigger is drawn again with more pixels, still a mask; so after a lost device. */
static void test_redrawn_and_restored(void) {
	const Recorded* mask;
	Rid texture, image, box;
	int i, width = 0, height = 0;
	write_file("test_mask_symbol.svg", g_symbol);
	texture = Rtexture_create_mask_from_file_async("test_mask_symbol.svg");
	wait_uploaded(texture);
	image = Rwidget_create(R_WIDGET_IMAGE);
	Rimage_set_texture(image, texture);
	Rimage_set_stretch(image, R_STRETCH_FILL);
	Rwidget_set_size(image, 96.0f, 96.0f);
	box = Rwidget_create(R_WIDGET_HBOX);
	Rwidget_add_child(box, image);
	Rslot_set_valign(image, R_VALIGN_TOP);
	Rwindow_set_content(g_window, box);
	for (i = 0; i < 400 && width != 96; i++) {
		Rdraw_frame();
		Rtexture_get_size(texture, &width, &height);
		if (width != 96) Rwait_events(0.005);
	}
	CHECK(width == 96 && height == 96);
	for (i = 0; i < 400 && !(last_upload(texture) && last_upload(texture)->width == 96); i++) {
		Rdraw_frame();
		Rwait_events(0.005);
	}
	mask = last_upload(texture);
	CHECK(mask != NULL);
	if (mask) {
		CHECK(mask->format == R_TEXTURE_ALPHA8);
		CHECK(mask->width == 96);
		CHECK(coverage(mask, 0.5f, 0.5f) == 255);
		CHECK(coverage(mask, 0.1f, 0.1f) == 0);
	}

	/* a lost device: the mask is made again from its file, still its alpha */
	i = uploads_of(texture);
	EXPECT_ERRORS_BEGIN();
	Rbackend_device_lost();
	wait_uploads(texture, i + 1);
	EXPECT_ERRORS_END(1);
	CHECK(uploads_of(texture) > i);
	mask = last_upload(texture);
	if (mask) {
		CHECK(mask->format == R_TEXTURE_ALPHA8);
		CHECK(mask->width == 96);
		CHECK(coverage(mask, 0.5f, 0.5f) == 255);
		CHECK(coverage(mask, 0.1f, 0.1f) == 0);
	}

	Rwindow_set_content(g_window, R_INVALID_ID);
	Rtexture_destroy(texture);
	remove("test_mask_symbol.svg");
}

int main(void) {
	RBackendVTable backend;
	RInitDesc desc;
	int i;
	memset(&backend, 0, sizeof(backend));
	backend.name = "record";
	backend.Init = record_init;
	backend.Shutdown = record_shutdown;
	backend.CreateSurface = record_create_surface;
	backend.DestroySurface = record_destroy_surface;
	backend.CreateTexture = record_create_texture;
	backend.UpdateTexture = record_update_texture;
	backend.SetTextureFilter = record_set_filter;
	backend.DestroyTexture = record_destroy_texture;
	backend.RenderFrame = record_render_frame;
	backend.WaitIdle = record_wait_idle;
	Rbackend_register(&backend);

	memset(&desc, 0, sizeof(desc));
	desc.backend = R_BACKEND_CUSTOM;
	desc.headless = R_TRUE;
	Rerror_set_callback(test_error_callback);
	if (!Rinit_ex(&desc)) {
		printf("cannot initialize Raven\n");
		return 1;
	}
	Rset_animations_enabled(R_FALSE);
	g_window = Rwindow_create("mask textures", 300, 200, R_WINDOW_DEFAULT);

	RUN(test_symbol);
	RUN(test_two_tone);
	RUN(test_raster);
	RUN(test_missing_file);
	RUN(test_redrawn_and_restored);

	Rdraw_frame();
	Rshutdown();
	for (i = 0; i < g_recorded_count; i++) free(g_recorded[i].pixels);
	return test_report("test_mask_textures");
}
