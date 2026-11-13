/*
 * Verify-first spike (ticket 0108): GL and Metal in Raven's macOS native child views.
 *
 * A real window, with Raven drawing through MoltenVK, holds a label and two viewports in native
 * mode (Rviewport_set_native). The left one gets an NSOpenGLContext on the child view and is
 * cleared green; the right one gets Metal frames in the child's CAMetalLayer, cleared magenta.
 * Each is read back (glReadPixels, and a copy of the Metal drawable), then the window is
 * captured with screencapture so the composite on screen can be checked too.
 *
 * Prints one line per check, and the viewports' rectangles in the capture's pixels.
 */
#define GL_SILENCE_DEPRECATION
#import <AppKit/AppKit.h>
#import <Metal/Metal.h>
#import <OpenGL/gl.h>
#import <QuartzCore/CAMetalLayer.h>
#import <objc/runtime.h>
#include <Raven.h>
#include <stdio.h>
#include <stdlib.h>

static int g_failures;

static void check(int ok, const char* what) {
	printf("%s  %s\n", ok ? "PASS" : "FAIL", what);
	if (!ok) g_failures++;
}

static void frames(int count) {
	for (int i = 0; i < count; i++) Rwait_frame(0.016);
}

/* ---- OpenGL on the child view ------------------------------------------------------------- */

static NSOpenGLContext* g_gl;

/* The view whose layer is `layer`, below `root`: Raven reports the child's layer, not its view. */
static NSView* view_of_layer(NSView* root, CALayer* layer) {
	if (root.layer == layer) return root;
	for (NSView* child in root.subviews) {
		NSView* found = view_of_layer(child, layer);
		if (found) return found;
	}
	return nil;
}

static void gl_attach(NSWindow* window, CAMetalLayer* layer) {
	printf("      the layer's delegate is %s\n", layer.delegate ? class_getName([(id)layer.delegate class]) : "nil");
	NSView* view = view_of_layer(window.contentView, layer);
	check(view != nil, "GL: the child's NSView is found in the window by its CAMetalLayer");
	if (!view) return;
	NSOpenGLPixelFormatAttribute attributes[] = {
		NSOpenGLPFAOpenGLProfile, NSOpenGLProfileVersion3_2Core,
		NSOpenGLPFAColorSize, 24, NSOpenGLPFAAlphaSize, 8, NSOpenGLPFADoubleBuffer, NSOpenGLPFAAccelerated, 0
	};
	NSOpenGLPixelFormat* format = [[NSOpenGLPixelFormat alloc] initWithAttributes:attributes];
	g_gl = [[NSOpenGLContext alloc] initWithFormat:format shareContext:nil];
	check(g_gl != nil, "GL: an NSOpenGLContext is created");
	[view setWantsBestResolutionOpenGLSurface:YES];
	[g_gl setView:view];
	check([g_gl view] == view, "GL: the context accepts the child view (setView:)");
}

static void gl_draw(int width, int height) {
	if (!g_gl) return;
	[g_gl makeCurrentContext];
	[g_gl update];
	glViewport(0, 0, width, height);
	glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
	glClear(GL_COLOR_BUFFER_BIT);
	[g_gl flushBuffer];
}

static void gl_read_back(int width, int height) {
	if (!g_gl) return;
	[g_gl makeCurrentContext];
	glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
	glClear(GL_COLOR_BUFFER_BIT);
	unsigned char pixel[4] = {0};
	glReadPixels(width / 2, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
	GLenum status = glGetError();
	printf("      GL read back %d,%d,%d (error 0x%x)\n", pixel[0], pixel[1], pixel[2], status);
	check(status == GL_NO_ERROR && pixel[0] < 10 && pixel[1] > 245 && pixel[2] < 10, "GL: the default framebuffer of the child view clears and reads back green");
	[g_gl flushBuffer];
}

/* ---- Metal in the child's CAMetalLayer ---------------------------------------------------- */

static id<MTLDevice> g_device;
static id<MTLCommandQueue> g_queue;

static int metal_draw(CAMetalLayer* layer, int read_back) {
	if (!g_device) {
		g_device = layer.device ? layer.device : MTLCreateSystemDefaultDevice();
		layer.device = g_device;
		layer.framebufferOnly = NO;
		g_queue = [g_device newCommandQueue];
	}
	id<CAMetalDrawable> drawable = [layer nextDrawable];
	if (!drawable) return -1;
	MTLRenderPassDescriptor* pass = [MTLRenderPassDescriptor renderPassDescriptor];
	pass.colorAttachments[0].texture = drawable.texture;
	pass.colorAttachments[0].loadAction = MTLLoadActionClear;
	pass.colorAttachments[0].storeAction = MTLStoreActionStore;
	pass.colorAttachments[0].clearColor = MTLClearColorMake(1.0, 0.0, 1.0, 1.0);
	id<MTLCommandBuffer> commands = [g_queue commandBuffer];
	[[commands renderCommandEncoderWithDescriptor:pass] endEncoding];
	id<MTLBuffer> copy = nil;
	NSUInteger width = drawable.texture.width, height = drawable.texture.height;
	if (read_back) {
		copy = [g_device newBufferWithLength:4 options:MTLResourceStorageModeShared];
		id<MTLBlitCommandEncoder> blit = [commands blitCommandEncoder];
		[blit copyFromTexture:drawable.texture sourceSlice:0 sourceLevel:0 sourceOrigin:MTLOriginMake(width / 2, height / 2, 0)
			sourceSize:MTLSizeMake(1, 1, 1) toBuffer:copy destinationOffset:0 destinationBytesPerRow:4 destinationBytesPerImage:4];
		[blit endEncoding];
	}
	[commands presentDrawable:drawable];
	[commands commit];
	[commands waitUntilCompleted];
	if (!read_back) return 1;
	const unsigned char* bgra = (const unsigned char*)[copy contents];
	printf("      Metal read back %d,%d,%d from a %lux%lu drawable\n", bgra[2], bgra[1], bgra[0], (unsigned long)width, (unsigned long)height);
	return bgra[2] > 245 && bgra[1] < 10 && bgra[0] > 245;
}

/* ---- the window --------------------------------------------------------------------------- */

static void print_rect(const char* name, Rid widget, float scale) {
	float x, y, width, height;
	Rwidget_get_rect(widget, &x, &y, &width, &height);
	printf("RECT %s %d %d %d %d\n", name, (int)(x * scale), (int)(y * scale), (int)(width * scale), (int)(height * scale));
}

int main(int argc, char** argv) {
	const char* capture = argc > 1 ? argv[1] : NULL;
	if (!Rinit(R_BACKEND_VULKAN)) {
		printf("FAIL  Raven starts with the Vulkan backend (MoltenVK)\n");
		return 1;
	}
	Rid window = Rwindow_create("Raven - GL and Metal in child views", 720, 420, 0);
	Rid column = Rwidget_create(R_WIDGET_VBOX);
	Rid label = Rwidget_add_child(column, Rlabel_create("Raven draws this label with Vulkan (MoltenVK); left: GL, right: Metal"));
	Rid row = Rwidget_add_child(column, Rwidget_create(R_WIDGET_HBOX));
	Rid gl_view = Rwidget_add_child(row, Rwidget_create(R_WIDGET_VIEWPORT));
	Rid metal_view = Rwidget_add_child(row, Rwidget_create(R_WIDGET_VIEWPORT));
	(void)label;
	Rslot_set_fill(row, 1.0f);
	Rslot_set_fill(gl_view, 1.0f);
	Rslot_set_fill(metal_view, 1.0f);
	Rwindow_set_content(window, column);
	Rwindow_show(window);
	frames(10);

	RNativeWindow gl_native, metal_native;
	check(Rviewport_set_native(gl_view, R_TRUE) && Rviewport_set_native(metal_view, R_TRUE), "both viewports get a native child window");
	frames(5);
	check(Rviewport_get_native_handle(gl_view, &gl_native) && Rviewport_get_native_handle(metal_view, &metal_native)
		&& gl_native.system == R_WINDOW_SYSTEM_COCOA, "their handles are Cocoa ones");
	CAMetalLayer* gl_layer = (__bridge CAMetalLayer*)(void*)gl_native.window;
	CAMetalLayer* metal_layer = (__bridge CAMetalLayer*)(void*)metal_native.window;
	check([metal_layer isKindOfClass:[CAMetalLayer class]], "a child's handle is a CAMetalLayer");

	RNativeWindow top;
	Rwindow_get_native_handle(window, &top);
	NSWindow* ns_window = (__bridge NSWindow*)top.display;
	int gl_width, gl_height;
	Rviewport_get_pixel_size(gl_view, &gl_width, &gl_height);
	gl_attach(ns_window, gl_layer);
	int metal_ok = metal_draw(metal_layer, 1);
	check(metal_ok == 1, "Metal: a frame rendered into the child's CAMetalLayer reads back magenta");
	gl_read_back(gl_width, gl_height);

	/* keep drawing for a second, Raven's frames in between, then capture the window */
	for (int i = 0; i < 60; i++) {
		Rwait_frame(0.016);
		Rviewport_get_pixel_size(gl_view, &gl_width, &gl_height);
		gl_draw(gl_width, gl_height);
		metal_draw(metal_layer, 0);
	}
	float scale = Rwindow_get_scale(window);
	printf("SCALE %.2f\n", scale);
	print_rect("gl", gl_view, scale);
	print_rect("metal", metal_view, scale);
	print_rect("label", label, scale);
	if (capture && ns_window) {
		char command[1024];
		snprintf(command, sizeof(command), "screencapture -x -o -t bmp -l%ld '%s'", (long)[ns_window windowNumber], capture);
		printf("capture: %s -> %d\n", command, system(command));
	}
	Rshutdown();
	printf("%d failure(s)\n", g_failures);
	return g_failures ? 1 : 0;
}
