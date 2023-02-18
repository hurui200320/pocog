package info.skyblond.pocog

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

        val game = ConwaysGame(w, h, 12)
        game.reset { x, y -> initialData[y * w + x] }

        println("-".repeat(60))
        println(
            "Width: ${game.gameWidth}, " +
                    "height: ${game.gameHeight}, " +
                    "height subdivision: ${game.heightSubdivisionSize}, " +
                    "parallelism: ${game.parallelism}, " +
                    "step: $step"
        )

        val time = measureTimeMillis {
            for (i in 0 until step) {
//            println("Step#$i")
                game.calculateNextTick()
                game.useCellStatus {
                    for (y in 0 until game.gameHeight) {
                        for (x in 0 until game.gameWidth) {
                            val nc = game.countNeighbors(x, y)
                            val current = it(x, y, false)
                            val next = it(x, y, true)
                            val c = if (current) {
                                if (next) "O" else "X"
                            } else {
                                if (next) "+" else " "
                            }
                            print("$c$nc,")
                        }
                        println()
                    }
                }
                game.checkResult()
                game.swapToNextTick()
            }
        }
        game.close()
        val timePerStep = time.toDouble() / step
        val cellPerMs = game.gameSize.toDouble() / time

        println(
            "Total time: $time ms, " +
                    "${"%.6f".format(timePerStep)} ms/step, " +
                    "${"%.6f".format(cellPerMs)} cell/ms"
        )
    }
}
