/**
 * Raven
 * raven_draw.h : custom-painted widgets.
 *
 * A CUSTOM widget is measured and painted by the application. Coordinates are
 * in logical units, relative to the top-left corner of the widget; Raven applies
 * the window scale, pixel snapping, clipping and opacity.
 *
 * The paint callback runs on the UI thread and may only call Rdraw_...
 * functions with the painter it received.
 */

#ifndef RAVEN_DRAW_H
#define RAVEN_DRAW_H

#include "raven_widget.h"

typedef struct RPainter RPainter;

/**
 * How a spring moves. Animations in Raven are springs, as on iOS: a value is
 * pulled toward its target and may run past it before settling, which is what
 * makes a control feel alive rather than mechanical.
 */
typedef enum RMotion{
	/** Fast, no bounce: hover, highlights. */
	R_MOTION_SNAPPY = 0x0320,
	/** Slower, no bounce: scrolling, fades, sizes. */
	R_MOTION_SMOOTH,
	/** Runs a little past the target and comes back: switches, popups, pressed buttons. */
	R_MOTION_BOUNCY,
	/** Swings several times around the target: things that fly in or get dropped. */
	R_MOTION_WOBBLY
}RMotion;

/** State of one animated value. Zero-initialize it and keep it with the widget. */
typedef struct RSpring {
	float value;
	float velocity;
	int started;
} RSpring;

#ifdef __cplusplus
extern "C" {
#endif

	typedef void (*RPaintCallback)(Rid widget, RPainter* painter, float width, float height, void* user_data);
	typedef void (*RMeasureCallback)(Rid widget, float* width, float* height, void* user_data);

	RAVEN_API void Rcustom_set_paint_callback(Rid widget, RPaintCallback callback, void* user_data);
	/** @brief Reports the desired size of the widget. Without it the widget asks for 0 x 0. */
	RAVEN_API void Rcustom_set_measure_callback(Rid widget, RMeasureCallback callback, void* user_data);

	/** @brief Filled rectangle with rounded corners (radius 0 = sharp). */
	RAVEN_API void Rdraw_rect(RPainter* painter, float x, float y, float width, float height,
		float radius, float r, float g, float b, float a);

	/** @brief Outline of a rounded rectangle, drawn inside its bounds. */
	RAVEN_API void Rdraw_rect_outline(RPainter* painter, float x, float y, float width, float height,
		float radius, float thickness, float r, float g, float b, float a);

	RAVEN_API void Rdraw_circle(RPainter* painter, float center_x, float center_y, float radius,
		float r, float g, float b, float a);

	/** @brief Anti-aliased line with round caps. */
	RAVEN_API void Rdraw_line(RPainter* painter, float x0, float y0, float x1, float y1,
		float thickness, float r, float g, float b, float a);

	/**
	 * @brief Lines through many points, of one color and thickness, in one call: anti-aliased
	 * segments with round caps, as Rdraw_line draws them.
	 *
	 * @p xy holds @p point_count points (x, y), each drawn at (offset_x + x * scale,
	 * offset_y + y * scale). A point whose x is NaN ends the line: the next point starts another,
	 * so many lines go in one call.
	 */
	RAVEN_API void Rdraw_polyline(RPainter* painter, const float* xy, int point_count,
		float offset_x, float offset_y, float scale, float thickness, float r, float g, float b, float a);

	/** @brief Anti-aliased cubic Bezier curve from (x0, y0) to (x1, y1), pulled by two control points. */
	RAVEN_API void Rdraw_curve(RPainter* painter, float x0, float y0, float cx0, float cy0, float cx1, float cy1, float x1, float y1,
		float thickness, float r, float g, float b, float a);

	RAVEN_API void Rdraw_triangle(RPainter* painter, float x0, float y0, float x1, float y1,
		float x2, float y2, float r, float g, float b, float a);

	/**
	 * @brief Many filled triangles of one color, in one call: a mesh, such as a triangulated polygon.
	 *
	 * @p xy holds @p count triangles, six floats each (x0, y0, x1, y1, x2, y2), in any unit: each
	 * point is drawn at (offset_x + x * scale, offset_y + y * scale). The triangles are filled
	 * without anti-aliasing, as a rasterizer fills them: a pixel along an edge two triangles share
	 * belongs to exactly one, so a mesh has no seam, and a translucent one covers no pixel twice.
	 * Its outer edges are aliased too; draw an outline over them to smooth them.
	 */
	RAVEN_API void Rdraw_triangles(RPainter* painter, const float* xy, int count,
		float offset_x, float offset_y, float scale, float r, float g, float b, float a);

	/** @brief Texture stretched to a rectangle and multiplied by a tint color. */
	RAVEN_API void Rdraw_image(RPainter* painter, Rid texture, float x, float y, float width, float height,
		float r, float g, float b, float a);

	/**
	 * @brief Part of a texture stretched to a rectangle and multiplied by a tint color: the part from
	 * (u0, v0) to (u1, v1), in texture coordinates (0 to 1 across the texture), such as one icon
	 * of a sprite sheet.
	 */
	RAVEN_API void Rdraw_image_region(RPainter* painter, Rid texture, float x, float y, float width, float height,
		float u0, float v0, float u1, float v1, float r, float g, float b, float a);

	/**
	 * @brief Part of a texture, as Rdraw_image_region draws it, turned by @p angle (radians,
	 * clockwise on screen) about the rectangle's centre: an icon that follows a road. Its edges are
	 * the texture's own, with no anti-aliasing of the quad: a texture with a transparent margin keeps
	 * them smooth.
	 */
	RAVEN_API void Rdraw_image_rotated(RPainter* painter, Rid texture, float x, float y, float width, float height,
		float u0, float v0, float u1, float v1, float angle, float r, float g, float b, float a);

	/**
	 * @brief Draws UTF-8 text; (x, y) is the top-left corner of the first line.
	 * @param font R_INVALID_ID uses the theme font.
	 * @param size 0 uses R_METRIC_FONT_SIZE.
	 */
	RAVEN_API void Rdraw_text(RPainter* painter, Rid font, float size, float x, float y,
		const char* text, float r, float g, float b, float a);

	/**
	 * @brief Text turned by @p angle (radians, clockwise on screen) about (x, y), its first line's
	 * top left corner, as Rdraw_text draws it unturned: lines go down the turned text.
	 */
	RAVEN_API void Rdraw_text_rotated(RPainter* painter, Rid font, float size, float x, float y, float angle,
		const char* text, float r, float g, float b, float a);

	/**
	 * @brief Rounded rectangle of glass. It shows what was drawn before it,
	 * frosted and bent along its rim, under a tint: (r, g, b) is the color of
	 * the tint and `a` its strength (0 = clear glass, 1 = opaque color).
	 * Works with any theme. Needs a backend that implements glass (the Vulkan
	 * backend does).
	 */
	RAVEN_API void Rdraw_glass(RPainter* painter, float x, float y, float width, float height,
		float radius, float r, float g, float b, float a);

	/**
	 * @brief Animates a value from the paint callback of a custom widget.
	 * Moves the spring toward `target` by the time elapsed since the last
	 * frame and returns its value. While it moves, the window keeps drawing
	 * frames by itself. The first call puts the spring on its target.
	 *
	 *     static RSpring size;                                  (zeroed)
	 *     float s = Rdraw_spring(painter, &size, hovered ? 1.0f : 0.0f, R_MOTION_BOUNCY);
	 *     Rdraw_circle(painter, cx, cy, 20.0f + 8.0f * s, ...);
	 */
	RAVEN_API float Rdraw_spring(RPainter* painter, RSpring* spring, float target, RMotion motion);
	/** @brief Pushes a spring away from its target (units per second): it bounces back. */
	RAVEN_API void Rspring_kick(RSpring* spring, float velocity);
	/** @brief Puts a spring at rest on a value, without animation. */
	RAVEN_API void Rspring_set(RSpring* spring, float value);

	/** @brief Restricts the following draws to a rectangle, intersected with the current one. */
	RAVEN_API void Rdraw_push_clip_rect(RPainter* painter, float x, float y, float width, float height);
	RAVEN_API void Rdraw_pop_clip_rect(RPainter* painter);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* RAVEN_DRAW_H */
