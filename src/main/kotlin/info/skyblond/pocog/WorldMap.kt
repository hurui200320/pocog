package info.skyblond.pocog

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("MemberVisibilityCanBePrivate")
class WorldMap private constructor(
    val height: Int, val width: Int,
    private val data: Array<IntArray>
) {
    init {
        require(height == data.size) { "Height doesn't match the data" }
        val wordWidth = width / Int.SIZE_BITS + if (width % Int.SIZE_BITS == 0) 0 else 1
        for (i in 1 until data.size) {
            require(data[i].size == wordWidth) { "Rows have different width" }
        }
    }

    operator fun get(x: Int, y: Int): Boolean {
        require(x in 0 until width) { "x out of bound" }
        require(y in 0 until height) { "y out of bound" }
        val wordIndex = x / Int.SIZE_BITS
        val wordOffset = x % Int.SIZE_BITS
        return data[y][wordIndex] and (1 shl wordOffset) != 0
    }

    class Builder(
        private val width: Int, private val height: Int
    ) {
        private val wordWidth = width / Int.SIZE_BITS + if (width % Int.SIZE_BITS == 0) 0 else 1
        private val data = Array(height) { IntArray(wordWidth) }
        private val locks = Array(height) { ReentrantLock() }

        operator fun set(x: Int, y: Int, newValue: Boolean) {
            require(y in 0 until height) { "y out of bound" }
            require(x in 0 until width) { "x out of bound" }
            val wordIndex = x / Int.SIZE_BITS
            val wordOffset = x % Int.SIZE_BITS
            locks[y].withLock {
                if (newValue) setTrue(wordIndex, wordOffset, y) else setFalse(wordIndex, wordOffset, y)
            }
        }

        private fun setTrue(wordIndex: Int, wordOffset: Int, y: Int) {
            val mask = 1 shl wordOffset
            data[y][wordIndex] = data[y][wordIndex] or mask
        }

        private fun setFalse(wordIndex: Int, wordOffset: Int, y: Int) {
            val mask = (1 shl wordOffset).inv()
            data[y][wordIndex] = data[y][wordIndex] and mask
        }

        fun build(): WorldMap {
            val result = Array(height) { IntArray(wordWidth) }
            for (i in data.indices) {
                locks[i].withLock {
                    System.arraycopy(data[i], 0, result[i], 0, wordWidth)
                }
            }
            return WorldMap(height, width, result)
        }
    }
}
