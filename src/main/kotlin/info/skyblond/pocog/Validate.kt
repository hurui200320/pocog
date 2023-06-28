package info.skyblond.pocog

import info.skyblond.pocog.dl4j.checkResult
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
                val (currentState, nextState) = game.getWorldMap()
                    .also { it.checkResult() }
                for (y in 0 until game.gameHeight) {
                    for (x in 0 until game.gameWidth) {
                        val nc = currentState.countNeighbors(x, y)
                        val current = currentState[x, y]
                        val next = nextState[x, y]
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


