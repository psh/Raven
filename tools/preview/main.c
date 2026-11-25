/*
 * raven_preview: renders Raven XML to PNG files, with no window shown.
 *
 *   raven_preview variant-A.xml -o variant-A.png [--scale 2] [--theme dark] [--size 420x260]
 *   raven_preview variants/notes-*.xml -o previews/      one PNG per file
 *   raven_preview notes.xml -o after.png --script 'type "New note" "buy milk"; click "Add"'
 *
 * It renders on headless Vulkan (no window system, nothing on screen) through
 * Rwindow_save_preview. With no usable Vulkan driver it exits with 77, as
 * tests/test_render does, so a test that runs it can be reported as skipped.
 */
#include <Raven.h>
#include "script.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#ifdef _WIN32
#include <direct.h>
#define make_directory(path) _mkdir(path)
#else
#define make_directory(path) mkdir(path, 0777)
#endif

#define EXIT_USAGE 2
#define EXIT_NO_VULKAN 77

static const char g_usage[] =
	"raven_preview: renders Raven XML to PNG files, with no window shown.\n"
	"\n"
	"usage: raven_preview FILE.xml... [options]\n"
	"\n"
	"  -o PATH          the PNG to write; a folder (one that exists, or ends with /) gets\n"
	"                   one FILE.png per file, as several files need. Default: FILE.png\n"
	"                   beside each file\n"
	"  --scale N        device pixels per UI pixel, drawn at that scale (default 1)\n"
	"  --theme NAME     dark, light, glass-dark, glass-light, high-contrast-dark,\n"
	"                   high-contrast-light, a look (WindowsLight), or a theme file\n"
	"  --size WxH       the window's size, instead of the XML's Width and Height\n"
	"  --script S       steps run before the capture, separated by ';':\n"
	"                     click SELECTOR\n"
	"                     type [SELECTOR] \"TEXT\"\n"
	"                     key [SELECTOR] KEY          (Enter, Ctrl+S, Shift+Tab, a)\n"
	"                     act expand|collapse|select|focus SELECTOR\n"
	"                     wait-idle\n"
	"                   with the driver's selectors (role=button name=\"Add\" [1]); a\n"
	"                   bare quoted word is name=\"...\"\n"
	"  --null           the null backend, which can't capture (for tests: exits with 77)\n"
	"  -h, --help       this\n"
	"\n"
	"Animations are off, so what a script does shows at once. Exits with 0 when every\n"
	"file was written, 1 when one failed or Raven warned about one (an unknown\n"
	"attribute: its PNG is written all the same, and a UI test would fail on it), 2 for\n"
	"a mistake in the options, and 77 when there is no usable Vulkan driver.\n";

/* Raven's errors are printed, except while finding out whether Vulkan works, and counted:
 * a file Raven warned about fails, as it would fail a UI test (raven_test.h). */
static int g_quiet;
static int g_problems;

static void print_error(RError code, RSeverity severity, const char* detail) {
	if (g_quiet || severity == R_SEVERITY_WEAK_WARNING) return;
	g_problems++;
	fprintf(stderr, "raven_preview: %s: %s\n", Rerror_to_string(code), detail);
}

static int usage_error(const char* message, const char* detail) {
	fprintf(stderr, "raven_preview: %s%s%s\n(raven_preview --help says how to use it)\n", message, detail ? " " : "", detail ? detail : "");
	return EXIT_USAGE;
}

static int is_directory(const char* path) {
	struct stat info;
	return stat(path, &info) == 0 && (info.st_mode & S_IFMT) == S_IFDIR;
}

/* Makes a folder and the folders it is in, as needed. With `up_to_last`, only those of the file a path names. */
static int make_folders(const char* path, int up_to_last) {
	char folder[1024];
	size_t i, length = strlen(path);
	if (length >= sizeof(folder)) return R_FALSE;
	memcpy(folder, path, length + 1);
	if (up_to_last) {
		while (length > 0 && folder[length - 1] != '/' && folder[length - 1] != '\\') length--;
		folder[length] = '\0';
	}
	for (i = 1; i <= length; i++) {
		if (folder[i] == '/' || folder[i] == '\\' || folder[i] == '\0') {
			char kept = folder[i];
			folder[i] = '\0';
			/* (a drive, "C:", is no folder to make) */
			if (folder[i - 1] != ':' && !is_directory(folder)) make_directory(folder);
			folder[i] = kept;
		}
	}
	return length == 0 || is_directory(folder);
}

static int ends_with_separator(const char* path) {
	size_t length = strlen(path);
	return length > 0 && (path[length - 1] == '/' || path[length - 1] == '\\');
}

/* The file's name without its folder and without its extension. */
static void base_name(const char* path, char* out, size_t size) {
	const char* name = path;
	const char* p;
	const char* dot;
	for (p = path; *p; p++) {
		if (*p == '/' || *p == '\\') name = p + 1;
	}
	dot = strrchr(name, '.');
	snprintf(out, size, "%.*s", (int)(dot && dot != name ? (size_t)(dot - name) : strlen(name)), name);
}

/* Where the PNG of `input` goes. */
static void output_path(const char* input, const char* output, int into_folder, char* out, size_t size) {
	char name[512];
	if (!output) {
		const char* dot = strrchr(input, '.');
		const char* slash = strrchr(input, '/');
		const char* backslash = strrchr(input, '\\');
		if (backslash > slash) slash = backslash;
		if (dot && (!slash || dot > slash)) snprintf(out, size, "%.*s.png", (int)(dot - input), input);
		else snprintf(out, size, "%s.png", input);
		return;
	}
	if (!into_folder) {
		snprintf(out, size, "%s", output);
		return;
	}
	base_name(input, name, sizeof(name));
	snprintf(out, size, "%s%s%s.png", output, ends_with_separator(output) ? "" : "/", name);
}

static int apply_theme(const char* name) {
	static const struct { const char* name; RTheme theme; } themes[] = {
		{ "dark", R_THEME_DARK }, { "light", R_THEME_LIGHT },
		{ "glass-dark", R_THEME_GLASS_DARK }, { "glass-light", R_THEME_GLASS_LIGHT },
		{ "high-contrast-dark", R_THEME_HIGH_CONTRAST_DARK }, { "high-contrast-light", R_THEME_HIGH_CONTRAST_LIGHT }
	};
	struct stat info;
	size_t i;
	for (i = 0; i < sizeof(themes) / sizeof(themes[0]); i++) {
		if (strcmp(name, themes[i].name) == 0) {
			Rtheme_apply(themes[i].theme);
			return R_TRUE;
		}
	}
	/* a theme file, else a theme or a look that Raven knows by name */
	if (stat(name, &info) == 0) {
		const char* loaded = Rtheme_load(name);
		return loaded && Rtheme_use(loaded);
	}
	return Rtheme_use(name);
}

/* Starts Raven on headless Vulkan, and checks that a window can be captured. */
static int start(int null_backend, float scale) {
	RInitDesc desc;
	Rid probe;
	uint8_t* pixels;
	int width = 0, height = 0;
	memset(&desc, 0, sizeof(desc));
	desc.backend = null_backend ? R_BACKEND_NULL : R_BACKEND_VULKAN;
	desc.headless = R_TRUE;
	desc.app_name = "raven_preview";
	/* drawn at the scale from the start: Rwindow_save_preview has nothing to change */
	desc.ui_scale = scale;
	g_quiet = R_TRUE;
	if (!Rinit_ex(&desc)) {
		g_quiet = R_FALSE;
		return R_FALSE;
	}
	probe = Rwindow_create("probe", 16, 16, R_WINDOW_DEFAULT);
	pixels = probe != R_INVALID_ID ? Rwindow_capture(probe, &width, &height) : NULL;
	Rpixels_free(pixels);
	if (probe != R_INVALID_ID) Rwindow_destroy(probe);
	g_quiet = R_FALSE;
	/* (the null backend's capture is only the clear color: Rwindow_save_preview refuses it) */
	if (!pixels || null_backend) {
		Rshutdown();
		return R_FALSE;
	}
	return R_TRUE;
}

static int render(const char* input, const char* png, int width, int height, const char* script, float scale) {
	Rid window;
	int px_width = 0, px_height = 0, ok;
	g_problems = 0;
	window = Rwindow_create_from_xml_file(input);
	if (window == R_INVALID_ID) {
		fprintf(stderr, "raven_preview: %s: no window was built from it\n", input);
		return R_FALSE;
	}
	if (width > 0 && height > 0) Rwindow_set_size(window, width, height);
	if (script) {
		char error[1024];
		Rwait_idle(600);
		if (!preview_run_script(window, script, error, sizeof(error))) {
			fprintf(stderr, "raven_preview: %s: the script stopped at %s\n", input, error);
			Rwindow_destroy(window);
			return R_FALSE;
		}
	}
	ok = Rwindow_save_preview(window, png, scale);
	if (ok) {
		Rwindow_get_framebuffer_size(window, &px_width, &px_height);
		printf("%s  %dx%d\n", png, px_width, px_height);
	}
	Rwindow_destroy(window);
	if (ok && g_problems) {
		fprintf(stderr, "raven_preview: %s: written, but Raven reported %d problem%s with it (above)\n",
			input, g_problems, g_problems == 1 ? "" : "s");
		return R_FALSE;
	}
	return ok;
}

int main(int argc, char** argv) {
	const char** inputs;
	const char* output = NULL;
	const char* theme = NULL;
	const char* script = NULL;
	float scale = 1.0f;
	int width = 0, height = 0, null_backend = R_FALSE, count = 0, into_folder, failed = 0, i;

	inputs = (const char**)calloc((size_t)argc, sizeof(*inputs));
	if (!inputs) return 1;
	for (i = 1; i < argc; i++) {
		const char* arg = argv[i];
		int has_value = i + 1 < argc;
		if (strcmp(arg, "-h") == 0 || strcmp(arg, "--help") == 0) {
			fputs(g_usage, stdout);
			free(inputs);
			return 0;
		} else if (strcmp(arg, "-o") == 0 || strcmp(arg, "--output") == 0) {
			if (!has_value) return usage_error("-o needs a path", NULL);
			output = argv[++i];
		} else if (strcmp(arg, "--scale") == 0) {
			char* end;
			if (!has_value) return usage_error("--scale needs a number", NULL);
			scale = strtof(argv[++i], &end);
			if (*end || !(scale >= 0.25f && scale <= 4.0f)) return usage_error("--scale is a number from 0.25 to 4, not", argv[i]);
		} else if (strcmp(arg, "--theme") == 0) {
			if (!has_value) return usage_error("--theme needs a name", NULL);
			theme = argv[++i];
		} else if (strcmp(arg, "--size") == 0) {
			char extra;
			if (!has_value) return usage_error("--size needs WxH", NULL);
			if (sscanf(argv[++i], "%dx%d%c", &width, &height, &extra) != 2 || width <= 0 || height <= 0)
				return usage_error("--size is WxH (420x260), not", argv[i]);
		} else if (strcmp(arg, "--script") == 0) {
			if (!has_value) return usage_error("--script needs steps", NULL);
			script = argv[++i];
		} else if (strcmp(arg, "--null") == 0) {
			null_backend = R_TRUE;
		} else if (arg[0] == '-' && arg[1]) {
			return usage_error("unknown option", arg);
		} else {
			inputs[count++] = arg;
		}
	}
	if (count == 0) return usage_error("no XML file given", NULL);
	into_folder = output && (count > 1 || ends_with_separator(output) || is_directory(output));

	Rerror_set_callback(print_error);
	if (!start(null_backend, scale)) {
		fprintf(stderr, null_backend ? "raven_preview: the null backend draws nothing: nothing written\n"
			: "raven_preview: no usable Vulkan driver to render without a window system: nothing written\n");
		free(inputs);
		return EXIT_NO_VULKAN;
	}
	if (output && !make_folders(output, !into_folder)) {
		fprintf(stderr, "raven_preview: cannot make the folder of %s\n", output);
		Rshutdown();
		free(inputs);
		return 1;
	}
	/* what a script does shows at once, and nothing is caught halfway */
	Rset_animations_enabled(R_FALSE);
	if (theme && !apply_theme(theme)) {
		fprintf(stderr, "raven_preview: no theme %s\n", theme);
		Rshutdown();
		free(inputs);
		return EXIT_USAGE;
	}
	for (i = 0; i < count; i++) {
		char png[1024];
		output_path(inputs[i], output, into_folder, png, sizeof(png));
		if (!render(inputs[i], png, width, height, script, scale)) failed++;
	}
	Rshutdown();
	free(inputs);
	return failed ? 1 : 0;
}
