package info.skyblond.pocog.video

import info.skyblond.pocog.game.ConwaysGame
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object GeneratingVideo {
    @JvmStatic
    fun main(args: Array<String>) {
        val videoGenerator = VideoGenerator(
            videoWidth = 480 * 3, videoHeight = 360 * 3, cellSize = 3,
            framerate = 30.0, framePerStep = 3,
            backend = ConwaysGame.Backend.OPEN_CL,
            preFill = 160,
            showGrid = false
        )

        println("Using backend ${videoGenerator.backend}")

        val seed = Random.nextInt()
        val step = 2000
        val random = Random(seed)
        println("Resetting game...")
        videoGenerator.resetGame { _, _ -> random.nextBoolean() }
        println("Start encoding...")
        videoGenerator.startEncoding(
            "output_seed_${seed}_step_${step}.mp4",
            "mp4", VideoGenerator.Codec.HEVC_NVENC,
            bitrate = 68_000_000
        )
        val preStepMap = ConcurrentHashMap<Int, Long>()
        println("Rendering...")
        videoGenerator.renderNStep(
            step + 1,
            preStepCallback = { preStepMap[it] = System.currentTimeMillis() },
            postStepCallback = {
                val t2 = System.currentTimeMillis()
                val t1 = preStepMap[it]!!
                println("Step#$it takes ${t2 - t1} ms")
            }
        )

        println("Finishing encoding...")
        videoGenerator.stopEncoding()
        println("Closing resources")
        videoGenerator.close()
        println("Done")
    }
}
