/**
 * Raven
 * raven_driver.h : finding what is on screen and using it as a user would.
 *
 * For tests in every language, scripts and agents. The driver works on the
 * tree that screen readers see (raven_accessibility.h), whose things are
 * accessibles: a window, a widget that means something (not one that only
 * lays others out), or an item of a widget (a row of a list or a tree, a
 * tab). What a test or an agent finds is what the user perceives, and a row
 * is as easy to click as a button.
 *
 * Selectors. One string says which accessibles to find: words separated by
 * spaces, every one of which must hold.
 *
 *   role=button              the role (RAccessibleRole in kebab case:
 *                            window group text heading link button
 *                            toggle-button checkbox radio-button slider
 *                            progress-bar spin-button text-field document
 *                            combo-box list list-item tree tree-item table
 *                            menu-bar menu menu-item tab-list tab tab-panel
 *                            toolbar status-bar dialog alert image canvas
 *                            separator scroll-bar pane)
 *   name="Save"              the accessible name, exactly
 *   name~="sav"              the name contains it, whatever the case
 *   text="buy milk"          the text it shows: a text field's value, else its name
 *   widget=save_button       the widget's Name in XML; with an item role
 *                            (role=list-item), the items of that widget
 *   checked selected expanded focused disabled enabled
 *                            states
 *   [2]                      the third of those that match (from 0)
 *
 * A value is quoted when it holds spaces ("buy milk", with \" and \\ inside).
 * Accessibles are searched in every open window, its content, island, popups
 * and dialogs, in the order they are shown, top to bottom.
 *
 * Failures are messages. Finding nothing is a normal result, not an error,
 * since tests also check that something is gone. Acting on something missing,
 * ambiguous or disabled returns R_FALSE, and Raccessible_get_last_error says
 * why. The error callback is kept for misuse: the wrong thread, a selector
 * that does not parse.
 *
 * Everything here is UI-thread only, like the rest of Raven. After an action,
 * Rwait_idle (or Ris_idle once a frame) waits for the app to settle.
 */

#ifndef RAVEN_DRIVER_H
#define RAVEN_DRIVER_H

#include "raven_core.h"
#include "raven_window.h"
#include "raven_accessibility.h"

#ifdef __cplusplus
extern "C" {
#endif

	/** An accessible: a window, a widget, or an item of a widget. 0: none. Valid while it is shown. */
	typedef uint64_t RAccessible;

	/** What an accessible is, now (Raccessible_describe). */
	typedef enum RAccessibleState {
		R_ACCESSIBLE_FOCUSABLE  = 1 << 0,
		R_ACCESSIBLE_FOCUSED    = 1 << 1,
		R_ACCESSIBLE_DISABLED   = 1 << 2,
		R_ACCESSIBLE_CHECKED    = 1 << 3,
		R_ACCESSIBLE_CHECKABLE  = 1 << 4,
		R_ACCESSIBLE_EXPANDED   = 1 << 5,
		R_ACCESSIBLE_EXPANDABLE = 1 << 6,
		R_ACCESSIBLE_SELECTED   = 1 << 7,
		R_ACCESSIBLE_SELECTABLE = 1 << 8,
		R_ACCESSIBLE_MULTILINE  = 1 << 9,
		R_ACCESSIBLE_READONLY   = 1 << 10,
		R_ACCESSIBLE_PASSWORD   = 1 << 11,
		/** number, minimum and maximum hold a range (a slider, a spin box). */
		R_ACCESSIBLE_HAS_RANGE  = 1 << 12,
		/** The value is a text that can be typed. */
		R_ACCESSIBLE_EDITABLE   = 1 << 13,
		/** It can be pressed. */
		R_ACCESSIBLE_INVOKABLE  = 1 << 14,
		/** Scrolled out of view, or in a menu that is closed. */
		R_ACCESSIBLE_OFFSCREEN  = 1 << 15,
		R_ACCESSIBLE_MODAL      = 1 << 16
	} RAccessibleState;

	/** An accessible, read (Raccessible_describe). The texts live until the next Raccessible_ call. */
	typedef struct RAccessibleInfo {
		RAccessibleRole role;
		const char* name;
		const char* description;
		/** A text field's text, the selected entry of a list, a slider's number as text; or "". */
		const char* value;
		double number, minimum, maximum;
		/** RAccessibleState values, or-ed. */
		Ruint states;
		/** In its window, logical units. */
		float x, y, width, height;
		Rid window;
		/** The widget it is, or the one whose item it is; R_INVALID_ID for a window. */
		Rid widget;
		/** Its index among its widget's items, or -1 when it is not an item. */
		int item;
	} RAccessibleInfo;

	/** What Raccessible_act does, for what input can't reach reliably. */
	typedef enum RAccessibleAction {
		R_ACCESSIBLE_EXPAND = 0x07C0,
		R_ACCESSIBLE_COLLAPSE,
		R_ACCESSIBLE_SELECT,
		R_ACCESSIBLE_FOCUS
	} RAccessibleAction;


	/* ============================================================================
	 *  Finding
	 * ============================================================================ */

	/**
	 * @brief How many accessibles match a selector.
	 * @param window R_INVALID_ID or 0: every open window.
	 * @return The number found, 0 when none (or when the selector does not parse, which is reported).
	 */
	RAVEN_API int Raccessible_count(Rid window, const char* selector);

	/** @return The accessible a selector names, when exactly one matches; else 0 (Raccessible_get_last_error says why). */
	RAVEN_API RAccessible Raccessible_find(Rid window, const char* selector);

	/** @return The index-th accessible that matches (from 0), in the order they are shown; 0 when there is none. */
	RAVEN_API RAccessible Raccessible_get(Rid window, const char* selector, int index);

	/** @return The accessible of a window: the top of its tree. */
	RAVEN_API RAccessible Raccessible_get_root(Rid window);
	/** @return The accessible that holds this one; 0 for a window. */
	RAVEN_API RAccessible Raccessible_get_parent(RAccessible accessible);
	RAVEN_API int Raccessible_get_child_count(RAccessible accessible);
	RAVEN_API RAccessible Raccessible_get_child(RAccessible accessible, int index);


	/* ============================================================================
	 *  Reading
	 * ============================================================================ */

	/** @brief Reads an accessible: role, name, description, value, number and range, states, rect. R_FALSE when it is gone. */
	RAVEN_API int Raccessible_describe(RAccessible accessible, RAccessibleInfo* info);

	/** @return The role's name in a selector ("list-item"), or "" for one that has none. */
	RAVEN_API const char* Raccessible_get_role_name(RAccessibleRole role);


	/* ============================================================================
	 *  Acting, as a user would
	 * ============================================================================ */

	/**
	 * @brief Clicks the middle of an accessible with the mouse: real input, so
	 * a disabled one can't be clicked. A row is scrolled into view first, and a
	 * menu item in a closed menu is reached through its menus, opened the way a
	 * user opens them.
	 */
	RAVEN_API int Raccessible_click(RAccessible accessible);

	/** @brief Gives an accessible the keyboard focus, then types a text into it, as the keyboard would. */
	RAVEN_API int Raccessible_type(RAccessible accessible, const char* text);

	/** @brief Gives an accessible the keyboard focus, then presses and releases a key (with RModifier values). */
	RAVEN_API int Raccessible_press_key(RAccessible accessible, RKey key, Ruint modifiers);

	/** @brief Expands, collapses, selects or focuses an accessible directly, without input. */
	RAVEN_API int Raccessible_act(RAccessible accessible, RAccessibleAction action);

	/**
	 * @return Why the last find or action failed, for a test to report and an
	 * agent to read ("no accessible with role=button name=\"Save\"", "2
	 * accessibles with role=list-item, expected one"); "" after one that worked.
	 */
	RAVEN_API const char* Raccessible_get_last_error(void);

#ifdef __cplusplus
}
#endif

#endif /* RAVEN_DRIVER_H */
