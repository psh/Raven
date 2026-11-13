/*
 * Spike: a Lottie animation rendered by ThorVG (its C API, CPU engine) over a custom-drawn
 * widget, in a real Raven window.
 *
 * An Overlay holds a custom widget painting stripes (what compose-raven's Canvas will be) and,
 * above it, a viewport in texture mode (what Viewport(image) will be). Each frame ThorVG renders
 * the animation into a straight-alpha RGBA buffer, which goes to the texture with Rtexture_update.
 * The window is captured at two frames, so the dot can be seen moving over the stripes.
 *
 * Prints one line per check, the viewport's rectangle in the captures' pixels, and how long a
 * frame takes to render.
 */
#include <Raven.h>
#include <thorvg_capi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <objc/message.h>
#include <objc/runtime.h>

#define SIZE 256

static int g_failures;

static void check(int ok, const char* what) {
	printf("%s  %s\n", ok ? "PASS" : "FAIL", what);
	if (!ok) g_failures++;
}

static char* read_file(const char* path, size_t* size) {
	FILE* file = fopen(path, "rb");
	if (!file) return NULL;
	fseek(file, 0, SEEK_END);
	*size = (size_t)ftell(file);
	fseek(file, 0, SEEK_SET);
	char* data = malloc(*size + 1);
	*size = fread(data, 1, *size, file);
	data[*size] = 0;
	fclose(file);
	return data;
}

static void paint_stripes(Rid widget, RPainter* painter, float width, float height, void* user_data) {
	(void)widget;
	(void)user_data;
	for (int i = 0; i * 24.0f < width; i++) {
		float shade = (i % 2) ? 0.18f : 0.32f;
		Rdraw_rect(painter, i * 24.0f, 0.0f, 24.0f, height, 0.0f, shade, shade * 0.8f, shade * 1.6f, 1.0f);
	}
}

/* Captures the window with screencapture, by the window number of its NSWindow. */
static void capture(Rid window, const char* path) {
	RNativeWindow native;
	char command[1024];
	Rwindow_get_native_handle(window, &native);
	long number = ((long (*)(void*, SEL))objc_msgSend)(native.display, sel_registerName("windowNumber"));
	snprintf(command, sizeof(command), "screencapture -x -o -t bmp -l%ld '%s'", number, path);
	printf("      capture %s: %d\n", path, system(command));
}

int main(int argc, char** argv) {
	const char* lottie_path = argc > 1 ? argv[1] : "dot.json";
	const char* capture_prefix = argc > 2 ? argv[2] : NULL;

	size_t size = 0;
	char* lottie = read_file(lottie_path, &size);
	check(lottie != NULL, "the Lottie file is read");
	if (!lottie) return 1;

	check(tvg_engine_init(0) == TVG_RESULT_SUCCESS, "ThorVG's engine starts");
	Tvg_Canvas canvas = tvg_swcanvas_create(TVG_ENGINE_OPTION_DEFAULT);
	uint32_t* pixels = calloc(SIZE * SIZE, sizeof(uint32_t));
	check(tvg_swcanvas_set_target(canvas, pixels, SIZE, SIZE, SIZE, TVG_COLORSPACE_ABGR8888S) == TVG_RESULT_SUCCESS,
		"a CPU canvas targets our straight-alpha RGBA buffer");
	Tvg_Animation animation = tvg_animation_new();
	Tvg_Paint picture = tvg_animation_get_picture(animation);
	check(tvg_picture_load_data(picture, lottie, (uint32_t)size, "lottie+json", NULL, true) == TVG_RESULT_SUCCESS,
		"the Lottie animation loads from memory");
	tvg_picture_set_size(picture, SIZE, SIZE);
	tvg_canvas_add(canvas, picture);
	float total = 0.0f;
	tvg_animation_get_total_frame(animation, &total);
	printf("      %.0f frames\n", total);

	if (!Rinit(R_BACKEND_VULKAN)) {
		printf("FAIL  Raven starts\n");
		return 1;
	}
	Rid window = Rwindow_create("Raven - ThorVG Lottie over a custom canvas", 480, 320, 0);
	Rid overlay = Rwidget_create(R_WIDGET_OVERLAY);
	Rid stripes = Rwidget_add_child(overlay, Rwidget_create(R_WIDGET_CUSTOM));
	Rcustom_set_paint_callback(stripes, paint_stripes, NULL);
	Rid viewport = Rwidget_add_child(overlay, Rwidget_create(R_WIDGET_VIEWPORT));
	Rwidget_set_size(viewport, SIZE, SIZE);
	Rslot_set_halign(viewport, R_HALIGN_CENTER);
	Rslot_set_valign(viewport, R_VALIGN_CENTER);
	Rwindow_set_content(window, overlay);
	Rwindow_show(window);

	Rid texture = R_INVALID_ID;
	double rendering = 0.0;
	int rendered = 0;
	for (int frame = 0; frame < (int)total; frame++) {
		double start = Rget_time();
		tvg_animation_set_frame(animation, (float)frame);
		tvg_canvas_update(canvas);
		tvg_canvas_draw(canvas, true);
		tvg_canvas_sync(canvas);
		rendering += Rget_time() - start;
		rendered++;
		if (texture == R_INVALID_ID) {
			texture = Rtexture_create_from_pixels((const uint8_t*)pixels, SIZE, SIZE);
			Rviewport_set_texture(viewport, texture);
		} else {
			Rtexture_update(texture, (const uint8_t*)pixels, SIZE, SIZE);
		}
		Rwidget_invalidate(viewport);
		Rwait_frame(1.0 / 30.0);
		if (frame == 0) {
			/* the dot's centre starts at (48, 128): opaque orange there, transparent in a corner */
			const uint8_t* p = (const uint8_t*)&pixels[128 * SIZE + 48];
			const uint8_t* corner = (const uint8_t*)&pixels[0];
			printf("      frame 0: centre of the dot %d,%d,%d,%d, corner alpha %d\n", p[0], p[1], p[2], p[3], corner[3]);
			check(p[0] > 240 && p[1] > 100 && p[1] < 130 && p[2] < 10 && p[3] == 255 && corner[3] == 0,
				"frame 0 is the orange dot on a transparent background, straight RGBA as Raven takes it");
		}
		if (capture_prefix && (frame == 0 || frame == (int)total - 1)) {
			char path[1024];
			for (int i = 0; i < 3; i++) Rwait_frame(1.0 / 30.0);
			snprintf(path, sizeof(path), "%s-%d.bmp", capture_prefix, frame);
			capture(window, path);
		}
	}
	printf("      %.3f ms per %dx%d frame on the CPU\n", rendering * 1000.0 / rendered, SIZE, SIZE);
	float x, y, width, height, scale = Rwindow_get_scale(window);
	Rwidget_get_rect(viewport, &x, &y, &width, &height);
	printf("RECT viewport %d %d %d %d\n", (int)(x * scale), (int)(y * scale), (int)(width * scale), (int)(height * scale));

	Rshutdown();
	tvg_animation_del(animation);
	tvg_canvas_destroy(canvas);
	tvg_engine_term();
	free(pixels);
	free(lottie);
	printf("%d failure(s)\n", g_failures);
	return g_failures ? 1 : 0;
}
