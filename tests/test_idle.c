/**
 * Raven tests - idle and the window list: Ris_idle, Rwait_idle, Rwindow_get_count, Rwindow_get.
 * Time is under control where something moves: every frame advances the animations by 1/60 s.
 */

#include "test.h"

static RSpring g_spring;
static float g_target, g_value;

static void paint_spring(Rid widget, RPainter* painter, float width, float height, void* user_data) {
	(void)widget;
	(void)width;
	(void)height;
	(void)user_data;
	g_value = Rdraw_spring(painter, &g_spring, g_target, R_MOTION_SMOOTH);
}

static Rid window_from(const char* xml) {
	Rid window = Rwindow_create_from_xml(xml, strlen(xml));
	CHECK(Rwindow_is_valid(window));
	return window;
}

static void test_a_change_settles(void) {
	Rid window, label;
	CHECK(Ris_idle(R_FALSE));
	window = window_from("<Window Width=\"200\" Height=\"100\"><Label Name=\"l\" Text=\"one\"/></Window>");
	label = Rwidget_find(window, "l");
	/* a new window has to be laid out and painted */
	CHECK(!Ris_idle(R_FALSE));
	CHECK(Rwait_idle(10));
	CHECK(Ris_idle(R_FALSE));
	Rlabel_set_text(label, "two");
	CHECK(!Ris_idle(R_FALSE));
	CHECK(Rwait_idle(10));
	CHECK(Ris_idle(R_FALSE));
	Rwindow_destroy(window);
	CHECK(Rwait_idle(10));
}

static int g_task_ran;

static void task(void* user_data) {
	(void)user_data;
	g_task_ran++;
}

static void test_a_posted_task_is_pending(void) {
	g_task_ran = 0;
	Rtask_post(task, NULL);
	CHECK(!Ris_idle(R_FALSE));
	CHECK(Rwait_idle(10));
	CHECK(g_task_ran == 1);
}

static void test_not_idle_while_an_animation_runs(void) {
	Rid window = window_from("<Window Width=\"200\" Height=\"100\"><Custom Name=\"c\"/></Window>");
	Rid custom = Rwidget_find(window, "c");
	Rset_animations_enabled(R_TRUE);
	Rset_animation_time_step(1.0 / 60.0);
	Rcustom_set_paint_callback(custom, paint_spring, NULL);
	g_target = 0.0f;
	CHECK(Rwait_idle(10));

	g_target = 1.0f;
	Rwidget_invalidate(custom);
	Rdraw_frame();
	CHECK(g_value > 0.0f && g_value < 1.0f);
	CHECK(!Ris_idle(R_FALSE));
	/* a few frames are not enough to settle */
	CHECK(!Rwait_idle(3));
	CHECK(!Ris_idle(R_FALSE));
	CHECK(Rwait_idle(600));
	CHECK(g_value == 1.0f);

	Rset_animation_time_step(0.0);
	Rset_animations_enabled(R_FALSE);
	Rwindow_destroy(window);
	CHECK(Rwait_idle(10));
}

static void on_timer(Rid timer, void* user_data) {
	(void)timer;
	(void)user_data;
}

static void test_timers_count_only_when_asked(void) {
	Rid timer = Rtimer_create(10.0, R_TRUE, on_timer, NULL);
	CHECK(Ris_idle(R_FALSE));
	CHECK(!Ris_idle(R_TRUE));
	/* Rwait_idle leaves timers out: a repeating one would keep it waiting for ever */
	CHECK(Rwait_idle(10));
	Rtimer_destroy(timer);
	CHECK(Ris_idle(R_TRUE));
}

static void test_a_continuous_window_can_be_idle(void) {
	Rid window = window_from("<Window Width=\"200\" Height=\"100\"><Label Text=\"spinning\"/></Window>");
	Rwindow_set_continuous_redraw(window, R_TRUE);
	CHECK(Rwait_idle(10));
	Rwindow_destroy(window);
	CHECK(Rwait_idle(10));
}

static int g_refused;

static void wait_inside(void* user_data) {
	(void)user_data;
	g_refused = !Rwait_idle(10);
}

static void test_waiting_inside_a_callback_is_refused(void) {
	g_refused = 0;
	Rtask_post(wait_inside, NULL);
	EXPECT_ERRORS_BEGIN();
	Rdraw_frame();
	EXPECT_ERRORS_END(1);
	CHECK(g_refused);
}

static void test_the_window_list(void) {
	Rid first, second, third;
	CHECK(Rwindow_get_count() == 0);
	CHECK(Rwindow_get(0) == R_INVALID_ID);
	first = window_from("<Window Title=\"first\"/>");
	second = window_from("<Window Title=\"second\"/>");
	CHECK(Rwindow_get_count() == 2);
	CHECK(Rwindow_get(0) == first);
	CHECK(Rwindow_get(1) == second);
	CHECK(Rwindow_get(2) == R_INVALID_ID);
	CHECK(Rwindow_get(-1) == R_INVALID_ID);
	/* a hidden window is still open */
	Rwindow_hide(second);
	CHECK(Rwindow_get_count() == 2);
	Rwindow_destroy(first);
	CHECK(Rwindow_get_count() == 1);
	CHECK(Rwindow_get(0) == second);
	third = window_from("<Window Title=\"third\"/>");
	CHECK(Rwindow_get_count() == 2);
	CHECK((Rwindow_get(0) == second && Rwindow_get(1) == third) || (Rwindow_get(0) == third && Rwindow_get(1) == second));
	Rwindow_destroy(second);
	Rwindow_destroy(third);
	CHECK(Rwindow_get_count() == 0);
	Rwait_idle(10);
}

int main(void) {
	if (!test_init(0)) {
		printf("test_idle: cannot start Raven\n");
		return 1;
	}
	RUN(test_a_change_settles);
	RUN(test_a_posted_task_is_pending);
	RUN(test_not_idle_while_an_animation_runs);
	RUN(test_timers_count_only_when_asked);
	RUN(test_a_continuous_window_can_be_idle);
	RUN(test_waiting_inside_a_callback_is_refused);
	RUN(test_the_window_list);
	Rshutdown();
	return test_report("test_idle");
}
