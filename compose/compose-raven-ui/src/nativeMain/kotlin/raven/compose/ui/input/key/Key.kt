package raven.compose.ui.input.key

import raven.compose.ui.InternalComposeRavenApi
import raven.cinterop.RKey
import raven.cinterop.R_KEY_0
import raven.cinterop.R_KEY_1
import raven.cinterop.R_KEY_2
import raven.cinterop.R_KEY_3
import raven.cinterop.R_KEY_4
import raven.cinterop.R_KEY_5
import raven.cinterop.R_KEY_6
import raven.cinterop.R_KEY_7
import raven.cinterop.R_KEY_8
import raven.cinterop.R_KEY_9
import raven.cinterop.R_KEY_A
import raven.cinterop.R_KEY_APOSTROPHE
import raven.cinterop.R_KEY_B
import raven.cinterop.R_KEY_BACKSLASH
import raven.cinterop.R_KEY_BACKSPACE
import raven.cinterop.R_KEY_C
import raven.cinterop.R_KEY_CAPS_LOCK
import raven.cinterop.R_KEY_COMMA
import raven.cinterop.R_KEY_D
import raven.cinterop.R_KEY_DELETE
import raven.cinterop.R_KEY_DOWN
import raven.cinterop.R_KEY_E
import raven.cinterop.R_KEY_END
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_KEY_EQUAL
import raven.cinterop.R_KEY_ESCAPE
import raven.cinterop.R_KEY_F
import raven.cinterop.R_KEY_F1
import raven.cinterop.R_KEY_F10
import raven.cinterop.R_KEY_F11
import raven.cinterop.R_KEY_F12
import raven.cinterop.R_KEY_F2
import raven.cinterop.R_KEY_F3
import raven.cinterop.R_KEY_F4
import raven.cinterop.R_KEY_F5
import raven.cinterop.R_KEY_F6
import raven.cinterop.R_KEY_F7
import raven.cinterop.R_KEY_F8
import raven.cinterop.R_KEY_F9
import raven.cinterop.R_KEY_G
import raven.cinterop.R_KEY_GRAVE
import raven.cinterop.R_KEY_H
import raven.cinterop.R_KEY_HOME
import raven.cinterop.R_KEY_I
import raven.cinterop.R_KEY_INSERT
import raven.cinterop.R_KEY_J
import raven.cinterop.R_KEY_K
import raven.cinterop.R_KEY_KP_0
import raven.cinterop.R_KEY_KP_1
import raven.cinterop.R_KEY_KP_2
import raven.cinterop.R_KEY_KP_3
import raven.cinterop.R_KEY_KP_4
import raven.cinterop.R_KEY_KP_5
import raven.cinterop.R_KEY_KP_6
import raven.cinterop.R_KEY_KP_7
import raven.cinterop.R_KEY_KP_8
import raven.cinterop.R_KEY_KP_9
import raven.cinterop.R_KEY_KP_ADD
import raven.cinterop.R_KEY_KP_DECIMAL
import raven.cinterop.R_KEY_KP_DIVIDE
import raven.cinterop.R_KEY_KP_ENTER
import raven.cinterop.R_KEY_KP_MULTIPLY
import raven.cinterop.R_KEY_KP_SUBTRACT
import raven.cinterop.R_KEY_L
import raven.cinterop.R_KEY_LEFT
import raven.cinterop.R_KEY_LEFT_ALT
import raven.cinterop.R_KEY_LEFT_BRACKET
import raven.cinterop.R_KEY_LEFT_CTRL
import raven.cinterop.R_KEY_LEFT_SHIFT
import raven.cinterop.R_KEY_LEFT_SUPER
import raven.cinterop.R_KEY_M
import raven.cinterop.R_KEY_MENU
import raven.cinterop.R_KEY_MINUS
import raven.cinterop.R_KEY_N
import raven.cinterop.R_KEY_O
import raven.cinterop.R_KEY_P
import raven.cinterop.R_KEY_PAGE_DOWN
import raven.cinterop.R_KEY_PAGE_UP
import raven.cinterop.R_KEY_PAUSE
import raven.cinterop.R_KEY_PERIOD
import raven.cinterop.R_KEY_PRINT_SCREEN
import raven.cinterop.R_KEY_Q
import raven.cinterop.R_KEY_R
import raven.cinterop.R_KEY_RIGHT
import raven.cinterop.R_KEY_RIGHT_ALT
import raven.cinterop.R_KEY_RIGHT_BRACKET
import raven.cinterop.R_KEY_RIGHT_CTRL
import raven.cinterop.R_KEY_RIGHT_SHIFT
import raven.cinterop.R_KEY_RIGHT_SUPER
import raven.cinterop.R_KEY_S
import raven.cinterop.R_KEY_SEMICOLON
import raven.cinterop.R_KEY_SLASH
import raven.cinterop.R_KEY_SPACE
import raven.cinterop.R_KEY_T
import raven.cinterop.R_KEY_TAB
import raven.cinterop.R_KEY_U
import raven.cinterop.R_KEY_UNKNOWN
import raven.cinterop.R_KEY_UP
import raven.cinterop.R_KEY_V
import raven.cinterop.R_KEY_W
import raven.cinterop.R_KEY_X
import raven.cinterop.R_KEY_Y
import raven.cinterop.R_KEY_Z

/**
 * A key of the keyboard, as Raven knows it: the letters, the digits, F1 to F12, the named keys,
 * the punctuation, the number pad and the modifier keys themselves. The names are Compose's, so
 * `Key.S`, `Key.Zero` and `Key.DirectionUp` read as they do there. [Unknown] is a key Raven has no
 * name for.
 */
public enum class Key(internal val code: RKey) {
    A(R_KEY_A), B(R_KEY_B), C(R_KEY_C), D(R_KEY_D), E(R_KEY_E), F(R_KEY_F), G(R_KEY_G),
    H(R_KEY_H), I(R_KEY_I), J(R_KEY_J), K(R_KEY_K), L(R_KEY_L), M(R_KEY_M), N(R_KEY_N),
    O(R_KEY_O), P(R_KEY_P), Q(R_KEY_Q), R(R_KEY_R), S(R_KEY_S), T(R_KEY_T), U(R_KEY_U),
    V(R_KEY_V), W(R_KEY_W), X(R_KEY_X), Y(R_KEY_Y), Z(R_KEY_Z),

    Zero(R_KEY_0), One(R_KEY_1), Two(R_KEY_2), Three(R_KEY_3), Four(R_KEY_4),
    Five(R_KEY_5), Six(R_KEY_6), Seven(R_KEY_7), Eight(R_KEY_8), Nine(R_KEY_9),

    F1(R_KEY_F1), F2(R_KEY_F2), F3(R_KEY_F3), F4(R_KEY_F4), F5(R_KEY_F5), F6(R_KEY_F6),
    F7(R_KEY_F7), F8(R_KEY_F8), F9(R_KEY_F9), F10(R_KEY_F10), F11(R_KEY_F11), F12(R_KEY_F12),

    Spacebar(R_KEY_SPACE),
    Escape(R_KEY_ESCAPE),
    Enter(R_KEY_ENTER),
    Tab(R_KEY_TAB),
    Backspace(R_KEY_BACKSPACE),
    Insert(R_KEY_INSERT),
    Delete(R_KEY_DELETE),
    DirectionLeft(R_KEY_LEFT),
    DirectionRight(R_KEY_RIGHT),
    DirectionUp(R_KEY_UP),
    DirectionDown(R_KEY_DOWN),
    PageUp(R_KEY_PAGE_UP),
    PageDown(R_KEY_PAGE_DOWN),
    MoveHome(R_KEY_HOME),
    MoveEnd(R_KEY_END),

    Apostrophe(R_KEY_APOSTROPHE), Comma(R_KEY_COMMA), Minus(R_KEY_MINUS), Period(R_KEY_PERIOD),
    Slash(R_KEY_SLASH), Semicolon(R_KEY_SEMICOLON), Equals(R_KEY_EQUAL), LeftBracket(R_KEY_LEFT_BRACKET),
    Backslash(R_KEY_BACKSLASH), RightBracket(R_KEY_RIGHT_BRACKET), Grave(R_KEY_GRAVE),

    CapsLock(R_KEY_CAPS_LOCK),
    PrintScreen(R_KEY_PRINT_SCREEN),
    Break(R_KEY_PAUSE),
    Menu(R_KEY_MENU),

    NumPad0(R_KEY_KP_0), NumPad1(R_KEY_KP_1), NumPad2(R_KEY_KP_2), NumPad3(R_KEY_KP_3), NumPad4(R_KEY_KP_4),
    NumPad5(R_KEY_KP_5), NumPad6(R_KEY_KP_6), NumPad7(R_KEY_KP_7), NumPad8(R_KEY_KP_8), NumPad9(R_KEY_KP_9),
    NumPadDot(R_KEY_KP_DECIMAL), NumPadDivide(R_KEY_KP_DIVIDE), NumPadMultiply(R_KEY_KP_MULTIPLY),
    NumPadSubtract(R_KEY_KP_SUBTRACT), NumPadAdd(R_KEY_KP_ADD), NumPadEnter(R_KEY_KP_ENTER),

    ShiftLeft(R_KEY_LEFT_SHIFT), CtrlLeft(R_KEY_LEFT_CTRL), AltLeft(R_KEY_LEFT_ALT), MetaLeft(R_KEY_LEFT_SUPER),
    ShiftRight(R_KEY_RIGHT_SHIFT), CtrlRight(R_KEY_RIGHT_CTRL), AltRight(R_KEY_RIGHT_ALT), MetaRight(R_KEY_RIGHT_SUPER),

    Unknown(R_KEY_UNKNOWN),
    ;

    internal companion object {
        private val byCode = entries.associateBy { it.code }

        /** The key Raven calls [code], or [Unknown]. */
        fun of(code: RKey): Key = byCode[code] ?: Unknown
    }
}

/** Raven's code for the key, for the test harness's key presses. */
@InternalComposeRavenApi
public val Key.ravenKey: RKey get() = code

/**
 * A [key] pressed with modifiers, which a menu item shows and answers to. [meta] is the Super
 * key: Cmd on macOS, the Windows key elsewhere. Nothing maps Ctrl to Cmd, so an app that wants
 * Cmd+S on macOS asks for `meta = true` there, as in Compose Desktop.
 */
public data class KeyShortcut(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
)
