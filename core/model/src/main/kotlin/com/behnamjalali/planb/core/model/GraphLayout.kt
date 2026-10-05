package com.behnamjalali.planb.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Force-directed layout for the note graph (Plan-B Pro #21): Fruchterman–Reingold with
 * Barnes–Hut repulsion (a quadtree, θ = [THETA]), so one iteration is O(n log n) and a graph of
 * [MAX_NODES] notes lays out in well under a second. A weak pull towards the middle keeps
 * unconnected notes and separate clusters on screen.
 *
 * The result depends only on the input and [seed] (no clock, no hash order), so the same notes
 * always get the same picture. Coordinates are centred and scaled into [-1, 1].
 */
object GraphLayout {
    const val MAX_NODES = 2_000
    const val THETA = 0.8f

    class Positions(val x: FloatArray, val y: FloatArray) {
        val size: Int get() = x.size
    }

    /** Fewer iterations for big graphs: each one costs more and they settle sooner per node. */
    fun defaultIterations(nodes: Int): Int = when {
        nodes <= 100 -> 300
        nodes <= 500 -> 200
        else -> 120
    }

    /**
     * Lays out [nodeCount] nodes joined by [edges] (index pairs; out-of-range pairs and self
     * loops are ignored). [nodeCount] above [MAX_NODES] is refused; callers keep the best-connected
     * nodes first.
     */
    fun layout(nodeCount: Int, edges: List<Pair<Int, Int>>, seed: Long = 1L, iterations: Int = defaultIterations(nodeCount)): Positions {
        require(nodeCount in 0..MAX_NODES) { "At most $MAX_NODES nodes" }
        val n = nodeCount
        if (n == 0) return Positions(FloatArray(0), FloatArray(0))
        if (n == 1) return Positions(floatArrayOf(0f), floatArrayOf(0f))
        val valid = edges.filter { (a, b) -> a != b && a in 0 until n && b in 0 until n }
        val side = sqrt(n.toFloat()) * SPACING
        val k = SPACING
        val k2 = k * k
        val random = Random(seed)
        val x = FloatArray(n) { (random.nextFloat() - 0.5f) * side }
        val y = FloatArray(n) { (random.nextFloat() - 0.5f) * side }
        val dx = FloatArray(n)
        val dy = FloatArray(n)
        var temperature = side / 8f
        val cooling = temperature / (iterations + 1)
        val tree = QuadTree(n)
        repeat(iterations) {
            dx.fill(0f)
            dy.fill(0f)
            tree.build(x, y)
            for (i in 0 until n) tree.repulse(i, x[i], y[i], k2, dx, dy)
            for ((a, b) in valid) {
                var ex = x[a] - x[b]
                var ey = y[a] - y[b]
                var d = sqrt(ex * ex + ey * ey)
                if (d < MIN_DISTANCE) {
                    ex = MIN_DISTANCE
                    ey = 0f
                    d = MIN_DISTANCE
                }
                // Attraction d² / k along the unit vector (ex, ey) / d.
                val force = d / k
                dx[a] -= ex * force
                dy[a] -= ey * force
                dx[b] += ex * force
                dy[b] += ey * force
            }
            for (i in 0 until n) {
                // Gravity towards the middle, proportional to the distance.
                dx[i] -= x[i] * GRAVITY
                dy[i] -= y[i] * GRAVITY
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (len > 0f) {
                    val step = min(len, temperature)
                    x[i] += dx[i] / len * step
                    y[i] += dy[i] / len * step
                }
            }
            temperature = max(temperature - cooling, side / 400f)
        }
        return normalize(x, y)
    }

    private fun normalize(x: FloatArray, y: FloatArray): Positions {
        val minX = x.min()
        val maxX = x.max()
        val minY = y.min()
        val maxY = y.max()
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val half = max(max(maxX - minX, maxY - minY) / 2f, 1e-3f)
        return Positions(FloatArray(x.size) { (x[it] - cx) / half }, FloatArray(y.size) { (y[it] - cy) / half })
    }

    private const val SPACING = 10f
    private const val MIN_DISTANCE = 0.01f
    private const val GRAVITY = 0.02f

    /**
     * Barnes–Hut quadtree stored in flat arrays (rebuilt each iteration, no allocation after
     * the first). A leaf holds one body; bodies closer than the deepest cell share a leaf by mass.
     */
    private class QuadTree(private val bodies: Int) {
        private var capacity = bodies * 4 + 16
        private var cx = FloatArray(capacity) // centre of mass
        private var cy = FloatArray(capacity)
        private var mass = FloatArray(capacity)
        private var minX = FloatArray(capacity)
        private var minY = FloatArray(capacity)
        private var size = FloatArray(capacity)
        private var child = IntArray(capacity * 4) // -1 = none
        private var body = IntArray(capacity) // the body of a leaf, -1 otherwise
        private var count = 0
        private var stack = IntArray(256)
        private var xs = FloatArray(0)
        private var ys = FloatArray(0)

        fun build(x: FloatArray, y: FloatArray) {
            xs = x
            ys = y
            var lx = x[0]
            var hx = x[0]
            var ly = y[0]
            var hy = y[0]
            for (i in 1 until bodies) {
                lx = min(lx, x[i])
                hx = max(hx, x[i])
                ly = min(ly, y[i])
                hy = max(hy, y[i])
            }
            count = 0
            val root = newNode(lx - 0.5f, ly - 0.5f, max(hx - lx, hy - ly) + 1f)
            for (i in 0 until bodies) insert(root, i, 0)
        }

        private fun newNode(x0: Float, y0: Float, s: Float): Int {
            if (count == capacity) grow()
            val id = count++
            minX[id] = x0
            minY[id] = y0
            size[id] = s
            mass[id] = 0f
            cx[id] = 0f
            cy[id] = 0f
            body[id] = -1
            for (q in 0 until 4) child[id * 4 + q] = -1
            return id
        }

        private fun grow() {
            capacity *= 2
            cx = cx.copyOf(capacity)
            cy = cy.copyOf(capacity)
            mass = mass.copyOf(capacity)
            minX = minX.copyOf(capacity)
            minY = minY.copyOf(capacity)
            size = size.copyOf(capacity)
            child = child.copyOf(capacity * 4)
            body = body.copyOf(capacity)
        }

        private fun isLeaf(node: Int): Boolean =
            child[node * 4] < 0 && child[node * 4 + 1] < 0 && child[node * 4 + 2] < 0 && child[node * 4 + 3] < 0

        private fun childFor(node: Int, i: Int): Int {
            val half = size[node] / 2f
            val q = (if (xs[i] >= minX[node] + half) 1 else 0) + (if (ys[i] >= minY[node] + half) 2 else 0)
            val existing = child[node * 4 + q]
            if (existing >= 0) return existing
            val created = newNode(minX[node] + (q and 1) * half, minY[node] + (q shr 1) * half, half)
            child[node * 4 + q] = created
            return created
        }

        private fun insert(start: Int, i: Int, startDepth: Int) {
            var node = start
            var depth = startDepth
            while (true) {
                val m = mass[node]
                cx[node] = (cx[node] * m + xs[i]) / (m + 1f)
                cy[node] = (cy[node] * m + ys[i]) / (m + 1f)
                mass[node] = m + 1f
                if (m == 0f) {
                    body[node] = i
                    return
                }
                if (isLeaf(node)) {
                    if (depth >= MAX_DEPTH) return // (almost) coincident bodies stay together by mass
                    val previous = body[node]
                    body[node] = -1
                    if (previous >= 0) {
                        // The previous body moves one level down; its mass is already counted here.
                        val below = childFor(node, previous)
                        cx[below] = xs[previous]
                        cy[below] = ys[previous]
                        mass[below] = 1f
                        body[below] = previous
                    }
                }
                node = childFor(node, i)
                depth++
            }
        }

        fun repulse(i: Int, px: Float, py: Float, k2: Float, dx: FloatArray, dy: FloatArray) {
            var top = 0
            stack[top++] = 0
            while (top > 0) {
                val node = stack[--top]
                val m = mass[node]
                if (m == 0f || (body[node] == i && m == 1f)) continue
                val leaf = isLeaf(node)
                var ex = px - cx[node]
                var ey = py - cy[node]
                var d2 = ex * ex + ey * ey
                if (leaf || size[node] * size[node] < THETA * THETA * d2) {
                    // A deep leaf may hold this body together with others: leave its own share out.
                    val others = if (leaf && body[node] == i) m - 1f else m
                    if (others <= 0f) continue
                    if (d2 < MIN_DISTANCE * MIN_DISTANCE) {
                        // Coincident: push apart in a fixed, index-dependent direction.
                        ex = if (i % 2 == 0) MIN_DISTANCE else -MIN_DISTANCE
                        ey = if (i % 3 == 0) MIN_DISTANCE else -MIN_DISTANCE
                        d2 = ex * ex + ey * ey
                    }
                    // Repulsion k² / d along the unit vector (ex, ey) / d.
                    val force = k2 * others / d2
                    dx[i] += ex * force
                    dy[i] += ey * force
                } else {
                    for (q in 0 until 4) {
                        val c = child[node * 4 + q]
                        if (c >= 0) {
                            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                            stack[top++] = c
                        }
                    }
                }
            }
        }

        private companion object {
            const val MAX_DEPTH = 40
        }
    }
}
