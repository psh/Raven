/**
 * raven_preview's scripts: the driver's calls (raven_driver.h) written down,
 * separated by ';', run before a preview is captured.
 *
 *   click <selector>                     Raccessible_click
 *   type [<selector>] "<text>"           Raccessible_type (the focused one without a selector)
 *   key [<selector>] <key>               Raccessible_press_key: Enter, Ctrl+S, Shift+Tab, a, F5...
 *                                        (the window, to what has the focus, without a selector)
 *   act <expand|collapse|select|focus> <selector>
 *                                        Raccessible_act
 *   wait-idle                            Rwait_idle
 *
 * Selectors are the driver's (role=button name="Add" [1]). A bare quoted word is
 * short for name="...", so `click "Add"` clicks what is named Add. Every step waits
 * until the app is idle after it.
 */
#ifndef RAVEN_PREVIEW_SCRIPT_H
#define RAVEN_PREVIEW_SCRIPT_H
#include <Raven.h>
#include <stddef.h>

/**
 * Runs a script on the windows that are open, the first of which is `window`.
 * @return R_FALSE at the first step that fails, with why in `error`
 * ("step 2, click "Save": no accessible matches name=\"Save\"").
 */
int preview_run_script(Rid window, const char* script, char* error, size_t error_size);

#endif
