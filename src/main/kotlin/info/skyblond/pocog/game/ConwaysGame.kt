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
     * Current world state.
     * */
    @Volatile
    protected var cellState: WorldMap = WorldMap.Builder(gameWidth, gameHeight).build()

    /**
     * How world should be at next tick.
     * */
    @Volatile
    protected var nextCellState: WorldMap? = null

    /**
     * Set the initial state.
     * Synced by [lock].
     * */
    fun reset(generator: (x: Int, y: Int) -> Boolean): Unit = lock.withLock {
        val builder = WorldMap.Builder(gameWidth, gameHeight)
        for (j in 0 until gameHeight) {
            for (i in 0 until gameWidth) {
                builder[i, j] = generator(i, j)
            }
        }
        cellState = builder.build()
        nextCellState = null
    }

    /**
     * Reset the initial state to [state].
     * */
    fun reset(state: WorldMap): Unit = lock.withLock {
        cellState = state
        nextCellState = null
    }

    /**
     * Get world map. The first one is current one, the next one is next tick.
     * Synced by [lock].
     * */
    fun getWorldMap(): Pair<WorldMap, WorldMap> = lock.withLock {
        if (nextCellState == null) {
            calculateNextTick()
            check(nextCellState != null) { "Should have next tick state, but didn't" }
        }
        return cellState to nextCellState!!
    }

    /**
     * Move to next tick.
     * Synced by [lock].
     * */
    fun swapToNextTick(): Unit = lock.withLock {
        // done update, swap state
        if (nextCellState == null) calculateNextTick()
        cellState = nextCellState!!
        nextCellState = null
    }

    /**
     * Calculate the next tick. This will update the whole map concurrently.
     * Should be synced by [lock].
     * This method should ensure the [nextCellState] is not null before return.
     * */
    protected abstract fun calculateNextTick()

    fun generateBufferedImage(
        videoWidth: Int, videoHeight: Int, cellSize: Int,
        background: Color, grid: Color?,
        alive: Color, aboutToDie: Color, aboutToLive: Color
    ): BufferedImage {
        val (currentState, nextState) = getWorldMap()
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
                val next = nextState[cellX, cellY]
                if (currentState[cellX, cellY]) {
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
