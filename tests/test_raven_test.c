/*
 * raven/raven_test.h, the harness for C apps' UI tests: its checks pass and
 * fail as they say, its messages say why, Raven's errors fail a test, and a
 * failure with capture leaves a PNG of each window. Failures are collected by
 * a hook instead of printed, then compared.
 *
 * Its own checks are counted apart (g_ok, g_bad), since the harness's counters
 * are what is being tested. The part with capture runs on headless Vulkan, and
 * is left out (said so) without it.
 */
#include <Raven.h>
#include <raven/raven_test.h>
#ifdef _WIN32
#include <direct.h>
#define set_env(name, value) _putenv_s(name, value)
#else
#include <sys/stat.h>
#define set_env(name, value) setenv(name, value, 1)
#endif

static int g_ok, g_bad;
static char g_messages[16][512];
static int g_message_count;

#define OK(condition) do { if (condition) g_ok++; else { g_bad++; printf("  FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); } } while (0)

static void collect(const char* file, int line, const char* message, void* user_data) {
	(void)file, (void)line, (void)user_data;
	if (g_message_count < 16) snprintf(g_messages[g_message_count++], sizeof(g_messages[0]), "%s", message);
}

/* The last failure said this. */
static int said(const char* part) {
	int found = g_message_count > 0 && strstr(g_messages[g_message_count - 1], part) != NULL;
	if (!found) printf("  (last message: \"%s\", expected it to hold \"%s\")\n", g_message_count ? g_messages[g_message_count - 1] : "", part);
	return found;
}

static const char UI[] =
	"<Window Title=\"T\" Width=\"300\" Height=\"240\">"
	"  <VBox>"
	"    <TextInput Name=\"field\" AccessibleName=\"Field\"/>"
	"    <Button Name=\"go\" Text=\"Go\"/>"
	"    <Button Name=\"off\" Text=\"Off\" Enabled=\"false\"/>"
	"    <CheckBox Name=\"box\" Text=\"Box\" Checked=\"true\"/>"
	"    <Slider Name=\"slider\" AccessibleName=\"Level\" Min=\"0\" Max=\"10\" Value=\"4\"/>"
	"    <Label Text=\"Hello\"/>"
	"  </VBox>"
	"</Window>";

static void test_passing_checks(void) {
	RTestOptions options = { 0 };
	RAccessibleInfo info;
	options.name = "passing";
	options.on_failure = collect;
	OK(Rtest_begin(&options));
	Rwindow_create_from_xml(UI, strlen(UI));
	OK(RTEST_EXPECT_EXISTS("role=button name=\"Go\""));
	OK(RTEST_EXPECT_ABSENT("role=button name=\"Stop\""));
	OK(RTEST_EXPECT_COUNT("role=button", 2));
	OK(RTEST_EXPECT_TEXT("role=text", "Hello"));
	OK(RTEST_TYPE("role=text-field", "typed"));
	OK(RTEST_EXPECT_TEXT("role=text-field", "typed"));
	OK(RTEST_EXPECT_FOCUSED("role=text-field", R_TRUE));
	OK(RTEST_KEY("role=text-field", R_KEY_BACKSPACE, 0));
	OK(RTEST_EXPECT_TEXT("role=text-field", "type"));
	OK(RTEST_EXPECT_ENABLED("role=button name=\"Go\"", R_TRUE));
	OK(RTEST_EXPECT_ENABLED("role=button name=\"Off\"", R_FALSE));
	OK(RTEST_EXPECT_CHECKED("role=checkbox", R_TRUE));
	OK(RTEST_CLICK("role=checkbox"));
	OK(RTEST_EXPECT_CHECKED("role=checkbox", R_FALSE));
	OK(RTEST_EXPECT_VALUE("role=slider", 4.0));
	OK(RTEST_ACT("role=button name=\"Go\"", R_ACCESSIBLE_FOCUS));
	OK(RTEST_EXPECT_FOCUSED("role=button name=\"Go\"", R_TRUE));
	OK(RTEST_DESCRIBE("role=slider", &info) && info.maximum == 10.0);
	OK(RTEST_CHECK(1 + 1 == 2));
	OK(Rtest_failures() == 0);
	OK(g_message_count == 0);
	/* the errors a test means to cause are counted, not failures */
	Rtest_expect_errors(R_TRUE);
	Rwidget_set_enabled((Rid)0x7FFF1234, R_TRUE);
	OK(Rtest_expect_errors(R_FALSE) == 1);
	OK(Rtest_failures() == 0);
	OK(Rtest_end() == 0);
}

static void test_failing_checks(void) {
	RTestOptions options = { 0 };
	options.name = "failing";
	options.on_failure = collect;
	g_message_count = 0;
	OK(Rtest_begin(&options));
	Rwindow_create_from_xml(UI, strlen(UI));
	OK(!RTEST_EXPECT_TEXT("role=text", "Goodbye"));
	OK(said("RTEST_EXPECT_TEXT(role=text): \"Hello\", expected \"Goodbye\""));
	OK(!RTEST_EXPECT_ABSENT("role=button name=\"Go\""));
	OK(said("RTEST_EXPECT_ABSENT(role=button name=\"Go\"): 1 match, expected 0"));
	OK(!RTEST_EXPECT_EXISTS("role=button"));
	OK(said("2 matches, expected exactly 1"));
	OK(!RTEST_EXPECT_COUNT("role=checkbox", 3));
	OK(said("1 match, expected 3"));
	OK(!RTEST_EXPECT_ENABLED("role=button name=\"Off\"", R_TRUE));
	OK(said("RTEST_EXPECT_ENABLED(role=button name=\"Off\"): disabled, expected enabled"));
	OK(!RTEST_EXPECT_CHECKED("role=checkbox", R_FALSE));
	OK(said("RTEST_EXPECT_CHECKED(role=checkbox): checked, expected not checked"));
	OK(!RTEST_EXPECT_FOCUSED("role=button name=\"Go\"", R_TRUE));
	OK(said("not focused, expected focused"));
	OK(!RTEST_EXPECT_VALUE("role=slider", 5.0));
	OK(said("RTEST_EXPECT_VALUE(role=slider): 4, expected 5"));
	OK(!RTEST_EXPECT_VALUE("role=button name=\"Go\"", 1.0));
	OK(said("it has no number (a button)"));
	/* the driver's own reasons: missing, ambiguous, disabled */
	OK(!RTEST_CLICK("role=button name=\"Stop\""));
	OK(said("RTEST_CLICK(role=button name=\"Stop\"): "));
	OK(said("Stop"));
	OK(!RTEST_CLICK("role=button"));
	OK(said("2 accessibles"));
	OK(!RTEST_CLICK("role=button name=\"Off\""));
	OK(said("disabled"));
	OK(!RTEST_CHECK(1 + 1 == 3));
	OK(said("RTEST_CHECK(1 + 1 == 3)"));
	/* Raven's errors fail the test */
	Rwidget_set_enabled((Rid)0x7FFF1234, R_TRUE);
	OK(said("R_ERROR_INVALID_ID"));
	OK(Rtest_failures() == 14);
	OK(g_message_count == 14);
	OK(Rtest_end() == 1);
}

/* With capture: a failure leaves a PNG of each window in RAVEN_TEST_CAPTURES. */
static void test_captures_on_failure(void) {
	char folder[512], path[600];
	FILE* file;
	RTestOptions options = { 0 };
	options.name = "captured";
	options.capture = R_TRUE;
	options.on_failure = collect;
	snprintf(folder, sizeof(folder), "test_raven_test_captures");
#ifdef _WIN32
	_mkdir(folder);
#else
	mkdir(folder, 0777);
#endif
	snprintf(path, sizeof(path), "%s/captured-1-window0.png", folder);
	remove(path);
	set_env("RAVEN_TEST_CAPTURES", folder);
	if (!Rtest_begin(&options)) {
		printf("  (no usable Vulkan driver: the captures are not checked)\n");
		return;
	}
	Rwindow_create_from_xml(UI, strlen(UI));
	OK(!RTEST_EXPECT_TEXT("role=text", "Goodbye"));
	file = fopen(path, "rb");
	OK(file != NULL);
	if (file) {
		unsigned char signature[8] = { 0 };
		OK(fread(signature, 1, 8, file) == 8 && memcmp(signature, "\x89PNG\r\n\x1a\n", 8) == 0);
		fclose(file);
	}
	OK(Rtest_end() == 1);
}

/* A test that doesn't start has nothing to run on: every step fails and says so. */
static void test_not_started(void) {
	g_message_count = 0;
	memset(&g_rtest, 0, sizeof(g_rtest));
	g_rtest.options.on_failure = collect;
	OK(!RTEST_CLICK("role=button"));
	OK(said("Rtest_begin first"));
	OK(Rtest_end() == 1);
}

/* Raven failing to start headless is a failure, not a skip: here, because it runs already. */
static void test_start_failure_is_no_skip(void) {
	RInitDesc desc;
	RTestOptions options = { 0 };
	memset(&desc, 0, sizeof(desc));
	desc.backend = R_BACKEND_NULL;
	desc.headless = R_TRUE;
	OK(Rinit_ex(&desc));
	g_message_count = 0;
	options.name = "twice";
	options.on_failure = collect;
	OK(Rtest_begin(&options));
	OK(said("Raven didn't start headless"));
	OK(!RTEST_CLICK("role=button"));
	OK(Rtest_end() == 1);
	Rerror_set_callback(NULL);
	Rshutdown();
}

int main(void) {
	printf("test_passing_checks\n");
	test_passing_checks();
	printf("test_failing_checks\n");
	test_failing_checks();
	printf("test_captures_on_failure\n");
	test_captures_on_failure();
	printf("test_not_started\n");
	test_not_started();
	printf("test_start_failure_is_no_skip\n");
	test_start_failure_is_no_skip();
	printf("test_raven_test: %d checks, %d failures\n", g_ok + g_bad, g_bad);
	return g_bad == 0 ? 0 : 1;
}
