package info.skyblond.pocog.game

import info.skyblond.pocog.WorldMap
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class ConwaysGame(
    val gameWidth: Int,
    val gameHeight: Int,
) {
    /**
     * We have different implementations.
     * */
    enum class Backend {
        CPU, OPEN_CL
    }

    /**
     * Instead of using synchronized to sync, [ReentrantLock] provides a
     * system-free, or privileged-free sync.
     * */
    protected val lock = ReentrantLock()

    /**
     * Current world status.
     * */
    @Volatile
    protected var cellStatus: WorldMap = WorldMap.Builder(gameWidth, gameHeight).build()

    /**
     * How world should be at next tick.
     * */
    @Volatile
    protected var nextCellStatus: WorldMap? = null

    /**
     * Set the initial status.
     * Synced by [lock].
     * */
    fun reset(generator: (x: Int, y: Int) -> Boolean): Unit = lock.withLock {
        val builder = WorldMap.Builder(gameWidth, gameHeight)
        for (j in 0 until gameHeight) {
            for (i in 0 until gameWidth) {
                builder[i, j] = generator(i, j)
            }
        }
        cellStatus = builder.build()
    }

    /**
     * Reset the initial status to [status].
     * */
    fun reset(status: WorldMap): Unit = lock.withLock {
        cellStatus = status
    }

    /**
     * Get world map. The first one is current one, the next one is next tick.
     * Synced by [lock].
     * */
    fun getWorldMap(): Pair<WorldMap, WorldMap> = lock.withLock {
        if (nextCellStatus == null) {
            calculateNextTick()
            check(nextCellStatus != null) { "Should have next tick status, but didn't" }
        }
        return cellStatus to nextCellStatus!!
    }

    /**
     * Move to next tick.
     * Synced by [lock].
     * */
    fun swapToNextTick(): Unit = lock.withLock {
        // done update, swap status
        if (nextCellStatus == null) calculateNextTick()
        cellStatus = nextCellStatus!!
        nextCellStatus = null
    }

    /**
     * Calculate the next tick. This will update the whole map concurrently.
     * Should be synced by [lock].
     * This method should ensure the [nextCellStatus] is not null before return.
     * */
    protected abstract fun calculateNextTick()

    fun generateBufferedImage(
        videoWidth: Int, videoHeight: Int, cellSize: Int,
        background: Color, grid: Color?,
        alive: Color, aboutToDie: Color, aboutToLive: Color
    ): BufferedImage {
        val (currentStatus, nextStatus) = getWorldMap()
        val result = BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_4BYTE_ABGR)
        for (y in 0 until videoHeight) {
            val cellY = y / cellSize
            for (x in 0 until videoWidth) {
                if (grid != null) {
                    // draw grid if and only if the cell size is big enough
                    if (x % cellSize == 0 || y % cellSize == 0) {
                        result.setRGB(x, y, grid.rgb)
                        continue // grid
                    }
                }
                val cellX = x / cellSize
                val next = nextStatus[cellX, cellY]
                if (currentStatus[cellX, cellY]) {
                    // current alive
                    if (next) result.setRGB(x, y, alive.rgb)
                    else result.setRGB(x, y, aboutToDie.rgb)
                } else {
                    // current dead
                    if (next) result.setRGB(x, y, aboutToLive.rgb)
                    else result.setRGB(x, y, background.rgb)
                }
            }
        }
        return result
    }
}
