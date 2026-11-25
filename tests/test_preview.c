/*
 * Previews: Rwindow_save_preview, and raven_preview's scripts (tools/preview/script.c).
 *
 * The windows are rendered with Vulkan without a window system (headless), as
 * raven_preview renders them; without a usable driver the test is reported as
 * skipped (exit code 77), as test_render is. The null backend's refusal is
 * checked first, on any machine.
 */
#include "test.h"
#include "../tools/preview/script.h"
#include <stdlib.h>

#define SKIP_CODE 77

#ifndef RAVEN_PREVIEW_EXAMPLES
#define RAVEN_PREVIEW_EXAMPLES "."
#endif

/* A PNG read back: its pixels and size. */
typedef struct Png {
	uint8_t* pixels;
	int width, height;
} Png;

static Png read_png(const char* path) {
	Png png = { NULL, 0, 0 };
	FILE* file = fopen(path, "rb");
	long size;
	uint8_t* data;
	if (!file) return png;
	fseek(file, 0, SEEK_END);
	size = ftell(file);
	fseek(file, 0, SEEK_SET);
	data = (uint8_t*)malloc((size_t)size);
	if (data && fread(data, 1, (size_t)size, file) == (size_t)size) png.pixels = Rimage_decode(data, (size_t)size, &png.width, &png.height);
	free(data);
	fclose(file);
	return png;
}

static Rid window_from_example(const char* name) {
	char path[1024];
	snprintf(path, sizeof(path), "%s/%s", RAVEN_PREVIEW_EXAMPLES, name);
	return Rwindow_create_from_xml_file(path);
}

/* The null backend draws nothing: a preview is refused, and said why. */
static void test_null_backend_refuses(void) {
	Rid window;
	Png png;
	CHECK(test_init(-1));
	window = Rwindow_create("null", 100, 80, R_WINDOW_DEFAULT);
	remove("test_preview_null.png");
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rwindow_save_preview(window, "test_preview_null.png", 1.0f));
	EXPECT_ERRORS_END(1);
	png = read_png("test_preview_null.png");
	CHECK(png.pixels == NULL);
	Rpixels_free(png.pixels);
	/* misuse is reported too */
	EXPECT_ERRORS_BEGIN();
	CHECK(!Rwindow_save_preview(window, NULL, 1.0f));
	CHECK(!Rwindow_save_preview(R_INVALID_ID, "x.png", 1.0f));
	EXPECT_ERRORS_END(2);
	Rwindow_destroy(window);
	Rshutdown();
}

static int start_vulkan(void) {
	RInitDesc desc;
	Rid probe;
	uint8_t* pixels;
	int width = 0, height = 0;
	memset(&desc, 0, sizeof(desc));
	desc.backend = R_BACKEND_VULKAN;
	desc.headless = R_TRUE;
	desc.app_name = "test_preview";
	Rerror_set_callback(test_error_callback);
	EXPECT_ERRORS_BEGIN();
	if (!Rinit_ex(&desc)) return 0;
	probe = Rwindow_create("probe", 16, 16, R_WINDOW_DEFAULT);
	pixels = probe != R_INVALID_ID ? Rwindow_capture(probe, &width, &height) : NULL;
	Rpixels_free(pixels);
	if (probe != R_INVALID_ID) Rwindow_destroy(probe);
	g_test_expect_errors = 0;
	if (!pixels) {
		Rshutdown();
		return 0;
	}
	Rset_animations_enabled(R_FALSE);
	return 1;
}

/* The spike's three notes variants, at 1x and drawn at 2x: the window is left as it was. */
static void test_variants_at_two_scales(void) {
	static const char* variants[] = { "notes-A.xml", "notes-B.xml", "notes-C.xml" };
	size_t i;
	for (i = 0; i < 3; i++) {
		Rid window = window_from_example(variants[i]);
		Png one, two;
		int width = 0, height = 0;
		CHECK(window != R_INVALID_ID);
		CHECK(Rwindow_save_preview(window, "test_preview_1x.png", 1.0f));
		CHECK(Rwindow_save_preview(window, "test_preview_2x.png", 2.0f));
		one = read_png("test_preview_1x.png");
		two = read_png("test_preview_2x.png");
		CHECK(one.pixels && one.width == 420 && one.height == 260);
		CHECK(two.pixels && two.width == 840 && two.height == 520);
		/* drawn, not stretched: the 2x picture is not the 1x one with each pixel doubled */
		if (one.pixels && two.pixels) {
			int x, y, doubled = 1;
			for (y = 0; y < 260 && doubled; y++) {
				for (x = 0; x < 420 && doubled; x++) {
					if (memcmp(one.pixels + (y * 420 + x) * 4, two.pixels + (y * 2 * 840 + x * 2) * 4, 4) != 0 ||
						memcmp(two.pixels + (y * 2 * 840 + x * 2) * 4, two.pixels + (y * 2 * 840 + x * 2 + 1) * 4, 4) != 0) doubled = 0;
				}
			}
			CHECK(!doubled);
			/* opaque: alpha 255 everywhere */
			CHECK(one.pixels[3] == 255 && one.pixels[(420 * 260 - 1) * 4 + 3] == 255);
		}
		Rpixels_free(one.pixels);
		Rpixels_free(two.pixels);
		Rwindow_get_size(window, &width, &height);
		CHECK(width == 420 && height == 260);
		CHECK_NEAR(Rget_ui_scale(), 1.0f);
		Rwindow_get_framebuffer_size(window, &width, &height);
		CHECK(width == 420 && height == 260);
		Rwindow_destroy(window);
	}
}

/* A window that is seen through: PNG gets straight alpha, not the premultiplied capture. */
static void test_transparent_window(void) {
	Rid window = Rwindow_create("glass", 64, 48, R_WINDOW_TRANSPARENT);
	Png png;
	Rwindow_set_clear_color(window, 1.0f, 0.0f, 0.0f, 0.5f);
	CHECK(Rwindow_save_preview(window, "test_preview_alpha.png", 1.0f));
	png = read_png("test_preview_alpha.png");
	CHECK(png.pixels && png.width == 64 && png.height == 48);
	if (png.pixels) {
		const uint8_t* p = png.pixels + (24 * 64 + 32) * 4;
		CHECK(abs((int)p[3] - 128) <= 2);
		CHECK(p[0] >= 250 && p[1] <= 2 && p[2] <= 2);
	}
	Rpixels_free(png.pixels);
	Rwindow_destroy(window);
}

static int pixels_differ(const Png* a, const Png* b) {
	return a->width != b->width || a->height != b->height ||
		memcmp(a->pixels, b->pixels, (size_t)a->width * (size_t)a->height * 4) != 0;
}

/* A script's state shows in the capture. */
static void test_script(void) {
	Rid window = window_from_example("notes-A.xml");
	RAccessibleInfo info;
	char error[512];
	Png before, after;
	CHECK(Rwindow_save_preview(window, "test_preview_before.png", 1.0f));
	CHECK(preview_run_script(window, "type role=text-field \"buy bread\"; key Enter; wait-idle", error, sizeof(error)));
	CHECK_STR(error, "");
	CHECK(Raccessible_describe(Raccessible_find(0, "role=text-field"), &info));
	CHECK_STR(info.value, "buy bread");
	CHECK(Rwindow_save_preview(window, "test_preview_after.png", 1.0f));
	before = read_png("test_preview_before.png");
	after = read_png("test_preview_after.png");
	CHECK(before.pixels && after.pixels && pixels_differ(&before, &after));
	Rpixels_free(before.pixels);
	Rpixels_free(after.pixels);

	/* a bare quoted word is a name; typing without a selector goes where the focus is */
	CHECK(preview_run_script(window, "click \"Add\"; act focus role=text-field; type \" and jam\"", error, sizeof(error)));
	CHECK(Raccessible_describe(Raccessible_find(0, "role=text-field"), &info));
	CHECK_STR(info.value, "buy bread and jam");
	Rwindow_destroy(window);
}

/* A step that fails stops the script and says which step and why. */
static void test_script_failures(void) {
	Rid window = window_from_example("notes-A.xml");
	char error[512];
	CHECK(!preview_run_script(window, "wait-idle; click \"Nope\"; type role=text-field \"never\"", error, sizeof(error)));
	CHECK(strstr(error, "step 2, click \"Nope\"") != NULL);
	CHECK(strstr(error, "name=\"Nope\"") != NULL);
	CHECK(!preview_run_script(window, "jump", error, sizeof(error)));
	CHECK(strstr(error, "unknown verb jump") != NULL);
	CHECK(!preview_run_script(window, "key Hyper+Q", error, sizeof(error)));
	CHECK(strstr(error, "unknown key") != NULL);
	CHECK(!preview_run_script(window, "type role=text-field \"open", error, sizeof(error)));
	CHECK(strstr(error, "not closed") != NULL);
	CHECK(!preview_run_script(window, "act squash role=button", error, sizeof(error)));
	CHECK(strstr(error, "squash") != NULL);
	/* nothing was typed by the step after the failure */
	{
		RAccessibleInfo info;
		CHECK(Raccessible_describe(Raccessible_find(0, "role=text-field"), &info));
		CHECK_STR(info.value, "");
	}
	Rwindow_destroy(window);
}

int main(void) {
	RUN(test_null_backend_refuses);
	if (!start_vulkan()) {
		printf("test_preview: SKIPPED, no usable Vulkan driver without a window system\n");
		return SKIP_CODE;
	}
	RUN(test_variants_at_two_scales);
	RUN(test_transparent_window);
	RUN(test_script);
	RUN(test_script_failures);
	Rshutdown();
	return test_report("test_preview");
}
