package info.skyblond.pocog

import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConwaysGame(
    val gameWidth: Int,
    val gameHeight: Int,
    val parallelism: Int,
    val heightSubdivisionSize: Int = (15000 / gameWidth).coerceAtLeast(1)
) : AutoCloseable {
    private val lock = ReentrantLock()
    private val threadPool = ForkJoinPool(parallelism)
    val gameSize = gameWidth.toLong() * gameHeight.toLong()

    @Volatile
    private lateinit var cellStatus: WorldMap

    @Volatile
    private var nextCellStatus: WorldMap? = null

    fun reset(generator: (x: Int, y: Int) -> Boolean) {
        val builder = WorldMap.Builder(gameWidth, gameHeight)
        for (i in 0 until gameWidth) {
            for (j in 0 until gameHeight) {
                builder[i, j] = generator(i, j)
            }
        }
        cellStatus = builder.build()
    }

    fun countNeighbors(x: Int, y: Int): Int {
        var counter = 0
        for (i in x - 1..x + 1) {
            for (j in y - 1..y + 1) {
                if (i == x && j == y) continue
                // count if in range
                if (i in 0 until gameWidth && j in 0 until gameHeight) {
                    if (cellStatus[i, j]) counter++
                }
            }
        }
        return counter
    }

    private fun updateCell(builder: WorldMap.Builder, x: Int, y: Int) {
        val currentStatus = cellStatus[x, y]
        val neighbor = countNeighbors(x, y)
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

    private fun updateLine(builder: WorldMap.Builder, y1: Int, y2: Int) {
        // p1: top left, p2: bottom right
        for (j in y1 until y2.coerceAtMost(gameHeight)) {
            for (i in 0 until gameWidth) {
                updateCell(builder, i, j)
            }
        }
    }

    fun calculateNextTick(): Unit = lock.withLock {
        val builder = WorldMap.Builder(gameWidth, gameHeight)
        val futureList = LinkedList<CompletableFuture<Void>>()
        for (y in 0 until gameHeight step heightSubdivisionSize) {
            CompletableFuture.runAsync({
                updateLine(builder, y, y + heightSubdivisionSize)
            }, threadPool).also { futureList.add(it) }
        }
        futureList.forEach { it.get() }
        nextCellStatus = builder.build()
    }

    fun useCellStatus(
        block: ((x: Int, y: Int, isNext: Boolean) -> Boolean) -> Unit
    ): Unit = lock.withLock {
        if (nextCellStatus == null) calculateNextTick()
        val func = fun(x: Int, y: Int, isNext: Boolean): Boolean =
            if (isNext) nextCellStatus!![x, y] else cellStatus[x, y]
        block.invoke(func)
    }

    /**
     * For debug only, make sure each cell is updated correctly.
     * */
    fun checkResult() {
        useCellStatus { query -> // check result
            for (y in 0 until gameHeight) {
                for (x in 0 until gameWidth) {
                    val current = query(x, y, false)
                    val next = query(x, y, true)
                    val nc = countNeighbors(x, y)
                    if (current) { // current alive
                        if (next) check(nc == 2 || nc == 3) { "($x, $y) should die, but alive, nc: $nc" } // still alive
                        else check(nc < 2 || nc > 3) { "($x, $y) should alive, but die, nc: $nc" }  // die next tick
                    } else { // current die
                        if (next) check(nc == 3) { "($x, $y) shouldn't alive, but did, nc: $nc" }  // alive next tick
                        else check(nc != 3) { "($x, $y) should alive, but didn't, nc: $nc" }  // still die
                    }
                }
            }
        }
    }

    fun swapToNextTick(): Unit = lock.withLock {
        // done update, swap status
        if (nextCellStatus == null) calculateNextTick()
        cellStatus = nextCellStatus!!
        nextCellStatus = null
    }

    override fun close(): Unit = lock.withLock {
        threadPool.shutdown()
    }

}
