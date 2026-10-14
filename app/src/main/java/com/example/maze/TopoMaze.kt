package com.example.maze

import java.util.ArrayDeque
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

data class PathPoint(val x: Float, val y: Float)

class TopoMaze(val dim: Int = 13) {

    val grid: Array<IntArray> = Array(dim) { IntArray(dim) { 1 } }
    var exitPos: Pair<Int, Int> = Pair(1, 1)
        private set
    var optimalDistance: Int = 0
        private set

    val distanceField: Array<FloatArray> = Array(dim) { FloatArray(dim) { 9999f } }

    init {
        generateHardestMaze()
    }

    private fun generateHardestMaze() {
        var attempts = 0
        var isValid = false
        var bestExit: BfsNode? = null
        var bestGrid: Array<IntArray>? = null

        val minTargetDist = when {
            dim >= 17 -> 24
            dim >= 13 -> 18
            else -> 10
        }
        val minTargetTurns = when {
            dim >= 17 -> 6
            dim >= 13 -> 4
            else -> 3
        }

        while (!isValid && attempts < 200) {
            attempts++
            for (y in 0 until dim) {
                for (x in 0 until dim) {
                    grid[y][x] = 1
                }
            }
            carveDfs(1, 1)

            val exitCandidate = findHardestExit()
            if (bestExit == null || (exitCandidate.dist + exitCandidate.turns * 3 > bestExit.dist + bestExit.turns * 3)) {
                bestExit = exitCandidate
                bestGrid = Array(dim) { y -> grid[y].clone() }
            }

            if (exitCandidate.dist >= minTargetDist && exitCandidate.turns >= minTargetTurns) {
                isValid = true
            }
        }

        if (bestGrid != null && bestExit != null) {
            for (y in 0 until dim) {
                for (x in 0 until dim) {
                    grid[y][x] = bestGrid[y][x]
                }
            }
            exitPos = Pair(bestExit.x, bestExit.y)
            optimalDistance = bestExit.dist
            grid[exitPos.second][exitPos.first] = 2 // 2 represents Exit
        }

        buildDistanceField()
    }

    private fun carveDfs(x: Int, y: Int) {
        grid[y][x] = 0
        val dirs = arrayOf(
            Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0)
        ).also { it.shuffle(Random.Default) }

        for ((dx, dy) in dirs) {
            val nx = x + dx * 2
            val ny = y + dy * 2
            if (nx in 1 until (dim - 1) && ny in 1 until (dim - 1) && grid[ny][nx] == 1) {
                grid[y + dy][x + dx] = 0
                carveDfs(nx, ny)
            }
        }
    }

    private data class BfsNode(
        val x: Int,
        val y: Int,
        val dist: Int,
        val dx: Int,
        val dy: Int,
        val turns: Int
    )

    private fun findHardestExit(): BfsNode {
        val queue = ArrayDeque<BfsNode>()
        val visited = Array(dim) { BooleanArray(dim) }
        queue.add(BfsNode(1, 1, 0, 0, 0, 0))
        visited[1][1] = true

        var best = BfsNode(1, 1, 0, 0, 0, 0)
        var maxScore = 0

        val directions = arrayOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))

        while (queue.isNotEmpty()) {
            val curr = queue.poll() ?: break
            val score = curr.dist + curr.turns * 3
            if (score > maxScore && (curr.x != 1 || curr.y != 1)) {
                maxScore = score
                best = curr
            }

            for ((dx, dy) in directions) {
                val nx = curr.x + dx
                val ny = curr.y + dy
                if (nx in 1 until (dim - 1) && ny in 1 until (dim - 1)) {
                    if (!visited[ny][nx] && grid[ny][nx] == 0) {
                        visited[ny][nx] = true
                        val isTurn = (curr.dx != 0 || curr.dy != 0) && (curr.dx != dx || curr.dy != dy)
                        queue.add(
                            BfsNode(
                                x = nx,
                                y = ny,
                                dist = curr.dist + 1,
                                dx = dx,
                                dy = dy,
                                turns = curr.turns + if (isTurn) 1 else 0
                            )
                        )
                    }
                }
            }
        }
        return best
    }

    private fun buildDistanceField() {
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                distanceField[y][x] = 9999f
            }
        }
        val queue = ArrayDeque<Pair<Int, Int>>()
        distanceField[exitPos.second][exitPos.first] = 0f
        queue.add(exitPos)

        val directions = arrayOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.poll() ?: break
            val curDist = distanceField[cy][cx]

            for ((dx, dy) in directions) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until dim && ny in 0 until dim) {
                    if (grid[ny][nx] != 1 && distanceField[ny][nx] > curDist + 1f) {
                        distanceField[ny][nx] = curDist + 1f
                        queue.add(Pair(nx, ny))
                    }
                }
            }
        }
    }

    fun getContinuous360Gradient(
        avatarX: Float,
        avatarY: Float,
        lookaheadDist: Float = 2.2f
    ): Triple<Float, Float, List<PathPoint>> {
        var tx = avatarX.toInt()
        var ty = avatarY.toInt()
        val path = mutableListOf(PathPoint(avatarX, avatarY))

        val directions = arrayOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))

        for (step in 0 until 18) {
            if (tx == exitPos.first && ty == exitPos.second) {
                path.add(PathPoint(tx + 0.5f, ty + 0.5f))
                break
            }
            var bestD = if (tx in 0 until dim && ty in 0 until dim) distanceField[ty][tx] else 9999f
            var nextStep: Pair<Int, Int>? = null

            for ((dx, dy) in directions) {
                val nx = tx + dx
                val ny = ty + dy
                if (nx in 0 until dim && ny in 0 until dim && grid[ny][nx] != 1) {
                    val d = distanceField[ny][nx]
                    if (d < bestD) {
                        bestD = d
                        nextStep = Pair(nx, ny)
                    }
                }
            }
            if (nextStep == null) break
            tx = nextStep.first
            ty = nextStep.second
            path.add(PathPoint(tx + 0.5f, ty + 0.5f))
        }

        var accum = 0.0
        var targetPt = path.last()

        for (i in 0 until (path.size - 1)) {
            val p0 = path[i]
            val p1 = path[i + 1]
            val segLen = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble())
            if (accum + segLen >= lookaheadDist) {
                val ratio = (lookaheadDist - accum) / (segLen + 1e-6)
                targetPt = PathPoint(
                    (p0.x + ratio * (p1.x - p0.x)).toFloat(),
                    (p0.y + ratio * (p1.y - p0.y)).toFloat()
                )
                break
            }
            accum += segLen
        }

        val dxGrid = targetPt.x - avatarX
        val dyGrid = targetPt.y - avatarY
        val d = hypot(dxGrid.toDouble(), dyGrid.toDouble()).toFloat() + 1e-6f

        return Triple(dxGrid / d, dyGrid / d, path)
    }

    fun isWall(gx: Float, gy: Float): Boolean {
        val ix = floor(gx).toInt()
        val iy = floor(gy).toInt()
        if (ix !in 0 until dim || iy !in 0 until dim) return true
        return grid[iy][ix] == 1
    }
}
