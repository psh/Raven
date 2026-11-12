package raven.compose.ui.input.key

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
import raven.cinterop.R_KEY_B
import raven.cinterop.R_KEY_BACKSPACE
import raven.cinterop.R_KEY_C
import raven.cinterop.R_KEY_D
import raven.cinterop.R_KEY_DELETE
import raven.cinterop.R_KEY_DOWN
import raven.cinterop.R_KEY_E
import raven.cinterop.R_KEY_END
import raven.cinterop.R_KEY_ENTER
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
import raven.cinterop.R_KEY_H
import raven.cinterop.R_KEY_HOME
import raven.cinterop.R_KEY_I
import raven.cinterop.R_KEY_INSERT
import raven.cinterop.R_KEY_J
import raven.cinterop.R_KEY_K
import raven.cinterop.R_KEY_L
import raven.cinterop.R_KEY_LEFT
import raven.cinterop.R_KEY_M
import raven.cinterop.R_KEY_N
import raven.cinterop.R_KEY_O
import raven.cinterop.R_KEY_P
import raven.cinterop.R_KEY_PAGE_DOWN
import raven.cinterop.R_KEY_PAGE_UP
import raven.cinterop.R_KEY_Q
import raven.cinterop.R_KEY_R
import raven.cinterop.R_KEY_RIGHT
import raven.cinterop.R_KEY_S
import raven.cinterop.R_KEY_SPACE
import raven.cinterop.R_KEY_T
import raven.cinterop.R_KEY_TAB
import raven.cinterop.R_KEY_U
import raven.cinterop.R_KEY_UP
import raven.cinterop.R_KEY_V
import raven.cinterop.R_KEY_W
import raven.cinterop.R_KEY_X
import raven.cinterop.R_KEY_Y
import raven.cinterop.R_KEY_Z

/**
 * A key of the keyboard, as Raven knows it: the letters, the digits, F1 to F12 and the named keys
 * a shortcut can use. The names are Compose's, so `Key.S`, `Key.Zero` and `Key.DirectionUp` read
 * as they do there.
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
}

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
