package info.skyblond.pocog

import info.skyblond.pocog.game.ConwaysGameCPU
import kotlin.random.Random
import kotlin.system.measureTimeMillis

object Validate {
    @JvmStatic
    fun main(args: Array<String>) {
        val step = 1200

        val w = 384
        val h = 216
        val random = Random(12345678)
        val initialData = BooleanArray(w * h)
        for (i in initialData.indices) {
            initialData[i] = random.nextBoolean()
        }

        val game = ConwaysGameCPU(w, h, 12)
        game.reset { x, y -> initialData[y * w + x] }

        println("-".repeat(60))
        println(
            "Width: ${game.gameWidth}, " +
                    "height: ${game.gameHeight}, " +
                    "height subdivision: ${game.heightSubdivisionSize}, " +
                    "step: $step"
        )

        val time = measureTimeMillis {
            for (i in 0 until step) {
                println("Step#$i")
                val (currentStatus, nextStatus) = game.getWorldMap()
                    .also { it.checkResult() }
                for (y in 0 until game.gameHeight) {
                    for (x in 0 until game.gameWidth) {
                        val nc = currentStatus.countNeighbors(x, y)
                        val current = currentStatus[x, y]
                        val next = nextStatus[x, y]
                        val c = if (current) {
                            if (next) "O" else "X"
                        } else {
                            if (next) "+" else " "
                        }
                        print("$c$nc,")
                    }
                    println()
                }
                game.swapToNextTick()
            }
        }
        val timePerStep = time.toDouble() / step

        println(
            "Total time: $time ms, " +
                    "${"%.6f".format(timePerStep)} ms/step"
        )
    }
}

fun Pair<WorldMap, WorldMap>.checkResult() {
    val currentMap = this.first
    val nextMap = this.second
    for (y in 0 until currentMap.height) {
        for (x in 0 until currentMap.width) {
            val current = currentMap[x, y]
            val next = nextMap[x, y]
            val nc = currentMap.countNeighbors(x, y)
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
