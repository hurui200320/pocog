package info.skyblond.pocog.game

import info.skyblond.pocog.WorldMap
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.withLock

/**
 * Use "java.util.concurrent.ForkJoinPool.common.parallelism" (system property)
 * to limit the parallelism of the [java.util.concurrent.ForkJoinPool].
 * */
class ConwaysGameCPU(
    gameWidth: Int,
    gameHeight: Int,
    /**
     * The world is updated in a per-row manner.
     * This defines how many rows a single task is handled.
     * Ideally, each task updating 15K cells gives the best performance.
     * */
    val heightSubdivisionSize: Int = (15000 / gameWidth).coerceAtLeast(1)
) : ConwaysGame(gameWidth, gameHeight) {

    /**
     * Update the cell ([x], [y]) by the following rules:
     * 1. If the cell is alive, it keeps alive if there are 2 or 3 alive cells around it.
     * 2. If the cell is alive, it dies if there are less than 2 or more than 3 alive cells around it.
     * 3. If the cell is dead, it becomes alive if there are 3 alive cells around it.
     * 4. Otherwise, it keeps ding.
     * */
    private fun updateCell(builder: WorldMap.Builder, x: Int, y: Int) {
        val currentStatus = cellStatus[x, y]
        val neighbor = cellStatus.countNeighbors(x, y)
        if (currentStatus) {
            // Any live cell with two or three live neighbours lives on to the next generation.
            // Any live cell with fewer than two live neighbours dies, as if by underpopulation.
            // Any live cell with more than three live neighbours dies, as if by overpopulation.
            builder[x, y] = neighbor in 2..3
        } else {
            // Any dead cell with exactly three live neighbours becomes a live cell, as if by reproduction.
            builder[x, y] = neighbor == 3
        }
    }

    /**
     * Update multiple rows.
     * Update one row means: given a y, update all cell (x,y) where x ranged from 0 until [gameWidth].
     * Update multiple rows means: given [y1] and [y2], update row y where y ranged from [y1] until [y2].
     * */
    private fun updateRows(builder: WorldMap.Builder, y1: Int, y2: Int) {
        // p1: top left, p2: bottom right
        for (j in y1 until y2.coerceAtMost(gameHeight)) {
            for (i in 0 until gameWidth) {
                updateCell(builder, i, j)
            }
        }
    }

    override fun calculateNextTick(): Unit = lock.withLock {
        val builder = WorldMap.Builder(gameWidth, gameHeight)
        val futureList = LinkedList<CompletableFuture<Void>>()
        for (y in 0 until gameHeight step heightSubdivisionSize) {
            CompletableFuture.runAsync {
                updateRows(builder, y, y + heightSubdivisionSize)
            }.also { futureList.add(it) }
        }
        futureList.forEach { it.get() }
        nextCellStatus = builder.build()
    }
}
