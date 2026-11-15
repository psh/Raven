package raven.spike.maps

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// A port of Mapbox's earcut (https://github.com/mapbox/earcut, ISC licence), the polygon
// triangulation MapLibre uses: ear clipping on a linked list of the outer ring, with holes bridged
// into it first, and two fallbacks for rings that intersect themselves. Left out for the spike: the
// z-order hash that speeds ear tests up on large polygons.

/**
 * Triangulates a polygon: [coords] holds x, y pairs, the outer ring first, then each hole, which
 * starts at the vertex index in [holeIndices]. Returns the triangles as vertex indices, three each.
 */
fun earcut(coords: FloatArray, holeIndices: IntArray = IntArray(0)): IntArray {
    val outerLength = if (holeIndices.isEmpty()) coords.size else holeIndices[0] * 2
    var outer = linkedList(coords, 0, outerLength, true) ?: return IntArray(0)
    val triangles = IntList()
    if (outer.next === outer.prev) return IntArray(0)
    if (holeIndices.isNotEmpty()) outer = eliminateHoles(coords, holeIndices, outer)
    earcutLinked(outer, triangles, 0)
    return triangles.toArray()
}

private class Node(val i: Int, val x: Float, val y: Float) {
    lateinit var prev: Node
    lateinit var next: Node
    var steiner = false
}

private class IntList {
    private var data = IntArray(64)
    private var size = 0

    fun add(a: Int, b: Int, c: Int) {
        if (size + 3 > data.size) data = data.copyOf(data.size * 2)
        data[size++] = a
        data[size++] = b
        data[size++] = c
    }

    fun toArray(): IntArray = data.copyOf(size)
}

private fun linkedList(data: FloatArray, start: Int, end: Int, clockwise: Boolean): Node? {
    var last: Node? = null
    if (clockwise == (signedArea(data, start, end) > 0)) {
        var i = start
        while (i < end) {
            last = insertNode(i / 2, data[i], data[i + 1], last)
            i += 2
        }
    } else {
        var i = end - 2
        while (i >= start) {
            last = insertNode(i / 2, data[i], data[i + 1], last)
            i -= 2
        }
    }
    if (last != null && equals(last, last.next)) {
        removeNode(last)
        last = last.next
    }
    return last
}

private fun filterPoints(start: Node, endNode: Node? = null): Node {
    var end = endNode ?: start
    var p = start
    var again: Boolean
    do {
        again = false
        if (!p.steiner && (equals(p, p.next) || area(p.prev, p, p.next) == 0f)) {
            removeNode(p)
            p = p.prev
            end = p
            if (p === p.next) break
            again = true
        } else {
            p = p.next
        }
    } while (again || p !== end)
    return end
}

private fun earcutLinked(start: Node, triangles: IntList, pass: Int) {
    var ear = start
    var stop = ear
    while (ear.prev !== ear.next) {
        val prev = ear.prev
        val next = ear.next
        if (isEar(ear)) {
            triangles.add(prev.i, ear.i, next.i)
            removeNode(ear)
            ear = next.next
            stop = next.next
            continue
        }
        ear = next
        if (ear === stop) {
            when (pass) {
                0 -> earcutLinked(filterPoints(ear), triangles, 1)
                1 -> earcutLinked(cureLocalIntersections(filterPoints(ear), triangles), triangles, 2)
                else -> splitEarcut(ear, triangles)
            }
            break
        }
    }
}

private fun isEar(ear: Node): Boolean {
    val a = ear.prev
    val b = ear
    val c = ear.next
    if (area(a, b, c) >= 0) return false
    val x0 = min(a.x, min(b.x, c.x))
    val y0 = min(a.y, min(b.y, c.y))
    val x1 = max(a.x, max(b.x, c.x))
    val y1 = max(a.y, max(b.y, c.y))
    var p = c.next
    while (p !== a) {
        if (p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 &&
            pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y) && area(p.prev, p, p.next) >= 0
        ) return false
        p = p.next
    }
    return true
}

private fun cureLocalIntersections(begin: Node, triangles: IntList): Node {
    var start = begin
    var p = start
    do {
        val a = p.prev
        val b = p.next.next
        if (!equals(a, b) && intersects(a, p, p.next, b) && locallyInside(a, b) && locallyInside(b, a)) {
            triangles.add(a.i, p.i, b.i)
            removeNode(p)
            removeNode(p.next)
            p = b
            start = b
        }
        p = p.next
    } while (p !== start)
    return filterPoints(p)
}

private fun splitEarcut(start: Node, triangles: IntList) {
    var a = start
    do {
        var b = a.next.next
        while (b !== a.prev) {
            if (a.i != b.i && isValidDiagonal(a, b)) {
                var c = splitPolygon(a, b)
                a = filterPoints(a, a.next)
                c = filterPoints(c, c.next)
                earcutLinked(a, triangles, 0)
                earcutLinked(c, triangles, 0)
                return
            }
            b = b.next
        }
        a = a.next
    } while (a !== start)
}

private fun eliminateHoles(data: FloatArray, holeIndices: IntArray, outerNode: Node): Node {
    val queue = ArrayList<Node>()
    for (i in holeIndices.indices) {
        val start = holeIndices[i] * 2
        val end = if (i < holeIndices.size - 1) holeIndices[i + 1] * 2 else data.size
        val list = linkedList(data, start, end, false) ?: continue
        if (list === list.next) list.steiner = true
        queue += leftmost(list)
    }
    queue.sortWith(compareBy<Node> { it.x }.thenBy { it.y })
    var outer = outerNode
    for (hole in queue) outer = eliminateHole(hole, outer)
    return outer
}

private fun eliminateHole(hole: Node, outerNode: Node): Node {
    val bridge = findHoleBridge(hole, outerNode) ?: return outerNode
    val bridgeReverse = splitPolygon(bridge, hole)
    filterPoints(bridgeReverse, bridgeReverse.next)
    return filterPoints(bridge, bridge.next)
}

private fun findHoleBridge(hole: Node, outerNode: Node): Node? {
    var p = outerNode
    val hx = hole.x
    val hy = hole.y
    var qx = Float.NEGATIVE_INFINITY
    var m: Node? = null
    // The segment left of the hole's leftmost point, at its height, nearest to it.
    do {
        if (hy <= p.y && hy >= p.next.y && p.next.y != p.y) {
            val x = p.x + (hy - p.y) * (p.next.x - p.x) / (p.next.y - p.y)
            if (x <= hx && x > qx) {
                qx = x
                m = if (p.x < p.next.x) p else p.next
                if (x == hx) return m
            }
        }
        p = p.next
    } while (p !== outerNode)
    if (m == null) return null
    // Of the points inside the triangle between the hole and the segment, the one with the
    // smallest angle to the hole, so the bridge crosses nothing.
    val stop: Node = m
    val mx = m.x
    val my = m.y
    var tanMin = Float.POSITIVE_INFINITY
    p = m
    do {
        if (hx >= p.x && p.x >= mx && hx != p.x &&
            pointInTriangle(if (hy < my) hx else qx, hy, mx, my, if (hy < my) qx else hx, hy, p.x, p.y)
        ) {
            val tan = abs(hy - p.y) / (hx - p.x)
            val best = m!!
            if (locallyInside(p, hole) &&
                (tan < tanMin || (tan == tanMin && (p.x > best.x || (p.x == best.x && sectorContainsSector(best, p)))))
            ) {
                m = p
                tanMin = tan
            }
        }
        p = p.next
    } while (p !== stop)
    return m
}

private fun sectorContainsSector(m: Node, p: Node): Boolean = area(m.prev, m, p.prev) < 0 && area(p.next, m, m.next) < 0

private fun leftmost(start: Node): Node {
    var p = start
    var leftmost = start
    do {
        if (p.x < leftmost.x || (p.x == leftmost.x && p.y < leftmost.y)) leftmost = p
        p = p.next
    } while (p !== start)
    return leftmost
}

private fun pointInTriangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, px: Float, py: Float): Boolean =
    (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
        (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
        (bx - px) * (cy - py) >= (cx - px) * (by - py)

private fun isValidDiagonal(a: Node, b: Node): Boolean =
    a.next.i != b.i && a.prev.i != b.i && !intersectsPolygon(a, b) &&
        (
            locallyInside(a, b) && locallyInside(b, a) && middleInside(a, b) &&
                (area(a.prev, a, b.prev) != 0f || area(a, b.prev, b) != 0f) ||
                equals(a, b) && area(a.prev, a, a.next) > 0 && area(b.prev, b, b.next) > 0
            )

private fun area(p: Node, q: Node, r: Node): Float = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

private fun equals(p1: Node, p2: Node): Boolean = p1.x == p2.x && p1.y == p2.y

private fun intersects(p1: Node, q1: Node, p2: Node, q2: Node): Boolean {
    val o1 = sign(area(p1, q1, p2))
    val o2 = sign(area(p1, q1, q2))
    val o3 = sign(area(p2, q2, p1))
    val o4 = sign(area(p2, q2, q1))
    if (o1 != o2 && o3 != o4) return true
    if (o1 == 0 && onSegment(p1, p2, q1)) return true
    if (o2 == 0 && onSegment(p1, q2, q1)) return true
    if (o3 == 0 && onSegment(p2, p1, q2)) return true
    if (o4 == 0 && onSegment(p2, q1, q2)) return true
    return false
}

private fun onSegment(p: Node, q: Node, r: Node): Boolean =
    q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) && q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)

private fun sign(n: Float): Int = if (n > 0) 1 else if (n < 0) -1 else 0

private fun intersectsPolygon(a: Node, b: Node): Boolean {
    var p = a
    do {
        if (p.i != a.i && p.next.i != a.i && p.i != b.i && p.next.i != b.i && intersects(p, p.next, a, b)) return true
        p = p.next
    } while (p !== a)
    return false
}

private fun locallyInside(a: Node, b: Node): Boolean =
    if (area(a.prev, a, a.next) < 0) {
        area(a, b, a.next) >= 0 && area(a, a.prev, b) >= 0
    } else {
        area(a, b, a.prev) < 0 || area(a, a.next, b) < 0
    }

private fun middleInside(a: Node, b: Node): Boolean {
    var p = a
    var inside = false
    val px = (a.x + b.x) / 2
    val py = (a.y + b.y) / 2
    do {
        if (((p.y > py) != (p.next.y > py)) && p.next.y != p.y &&
            (px < (p.next.x - p.x) * (py - p.y) / (p.next.y - p.y) + p.x)
        ) inside = !inside
        p = p.next
    } while (p !== a)
    return inside
}

private fun splitPolygon(a: Node, b: Node): Node {
    val a2 = Node(a.i, a.x, a.y)
    val b2 = Node(b.i, b.x, b.y)
    val an = a.next
    val bp = b.prev
    a.next = b
    b.prev = a
    a2.next = an
    an.prev = a2
    b2.next = a2
    a2.prev = b2
    bp.next = b2
    b2.prev = bp
    return b2
}

private fun insertNode(i: Int, x: Float, y: Float, last: Node?): Node {
    val p = Node(i, x, y)
    if (last == null) {
        p.prev = p
        p.next = p
    } else {
        p.next = last.next
        p.prev = last
        last.next.prev = p
        last.next = p
    }
    return p
}

private fun removeNode(p: Node) {
    p.next.prev = p.prev
    p.prev.next = p.next
}

private fun signedArea(data: FloatArray, start: Int, end: Int): Float {
    var sum = 0f
    var j = end - 2
    var i = start
    while (i < end) {
        sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1])
        j = i
        i += 2
    }
    return sum
}
