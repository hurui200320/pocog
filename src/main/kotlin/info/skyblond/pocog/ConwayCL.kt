package info.skyblond.pocog

import info.skyblond.pocog.game.ConwaysGame
import info.skyblond.pocog.game.ConwaysGameCPU
import info.skyblond.pocog.game.ConwaysGameOpenCL
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.measureTimeMillis

fun main() {
    val gameWidth = 10000
    val gameHeight = 10000
    val step = 10
    val seed = 1234

    println("Preparing initial status...")
    val initialStatus = WorldMap.Builder(gameWidth, gameHeight)
        .also { builder ->
            val random = Random(seed)
            for (y in 0 until gameHeight) {
                for (idx in 0 until builder.wordWidth) {
                    builder.setBlock(y, idx, random.nextInt())
                }
            }
        }
        .build()

    ConwaysGameOpenCL(gameWidth, gameHeight, rowPerKernel = 32).use { game ->
        game.reset(initialStatus)
        println("GPU test started...")
        repeat(step) { i ->
            val ms = game.measureStep()
            println("OpenCL step $i use $ms ms, GPU take ${game.lastStepOpenCLTime} ms")
        }
        println("GPU test done...")
    }


    ConwaysGameCPU(gameWidth, gameHeight).let { game ->
        game.reset(initialStatus)
        println("CPU test started...")
        repeat(step) { i ->
            val ms = game.measureStep()
            println("CPU step $i use $ms ms")
        }
        println("CPU test done...")
    }
}

private fun ConwaysGame.measureStep(): Long {
    var currentMap: WorldMap
    var nextMap: WorldMap
    val ms = measureTimeMillis {
        this.getWorldMap().also { currentMap = it.first; nextMap = it.second }
    }
    thread { (currentMap to nextMap).checkResult() }
    this.swapToNextTick()
    return ms
}
