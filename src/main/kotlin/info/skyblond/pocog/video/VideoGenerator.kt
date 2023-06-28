package info.skyblond.pocog.video

import info.skyblond.pocog.game.ConwaysGame
import info.skyblond.pocog.game.ConwaysGameCPU
import info.skyblond.pocog.game.ConwaysGameOpenCL
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.min

class VideoGenerator(
    val videoWidth: Int,
    val videoHeight: Int,
    val cellSize: Int,
    val framerate: Double = 60.0,
    val framePerStep: Int = 6,
    val backend: ConwaysGame.Backend = ConwaysGame.Backend.OPEN_CL,
    val preFill: Int = 32,
    val showGrid: Boolean = true
) : AutoCloseable {

    private var closeGameCallback: (() -> Unit)? = null

    private val game = when (backend) {
        ConwaysGame.Backend.CPU -> ConwaysGameCPU(
            gameWidth = videoWidth / cellSize,
            gameHeight = videoHeight / cellSize
        )

        ConwaysGame.Backend.OPEN_CL -> ConwaysGameOpenCL(
            gameWidth = videoWidth / cellSize,
            gameHeight = videoHeight / cellSize
        ).also {
            closeGameCallback = fun() { it.close() }
        }
    }

    @Volatile
    private var recorder: FFmpegFrameRecorder? = null
    private val converter: Java2DFrameConverter = Java2DFrameConverter()

    fun resetGame(generator: (x: Int, y: Int) -> Boolean): Unit = game.reset(generator)


    enum class Codec(
        val codecName: String,
        val pixelFormat: Int
    ) {
        HEVC_QSV("hevc_qsv", avutil.AV_PIX_FMT_BGRA),
        HEVC_NVENC("hevc_nvenc", avutil.AV_PIX_FMT_BGRA),
        HEVC_AMF("hevc_amf", avutil.AV_PIX_FMT_YUV420P),
        HEVC_MF("hevc_mf", avutil.AV_PIX_FMT_YUV420P),
        X265("libx265", avutil.AV_PIX_FMT_YUV420P),
    }

    fun startEncoding(
        fileName: String, formatName: String, codec: Codec,
        bitrate: Int = 20_000_000 // 20Mbps
    ) {
        check(recorder == null) { "Encoding already started" }

        recorder = FFmpegFrameRecorder(fileName, videoWidth, videoHeight)
            .also {
                it.frameRate = framerate
                it.format = formatName
                it.videoCodecName = codec.codecName
                it.pixelFormat = codec.pixelFormat
                it.videoBitrate = bitrate
                it.start()
            }
    }

    fun renderNStep(
        n: Int,
        preStepCallback: (Int) -> Unit = {},
        postStepCallback: (Int) -> Unit = {}
    ) {
        require(n >= 1) { "Step n must bigger than 0" }
        require(recorder != null) { "Encoding not started" }
        val imageQueue = ConcurrentLinkedQueue<BufferedImage>()
        val t = thread(isDaemon = true) {
            for (i in 0 until n) {
                // generate image for current state
                val image = game.generateBufferedImage(
                    videoWidth, videoHeight, cellSize,
                    Color.BLACK, if (showGrid) Color.GRAY else null,
                    Color.WHITE, Color.LIGHT_GRAY, Color.DARK_GRAY
                )
                imageQueue.add(image)
                if (imageQueue.size == 1)
                // we just added to an empty queue, or there are too much
                    synchronized(imageQueue) { // wake up waiting threads
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                        (imageQueue as Object).notifyAll()
                    }
                game.swapToNextTick()
            }
        }

        println("Pre filling...")
        while (imageQueue.size <= min(preFill, n)) {
            Thread.yield()
        }

        for (i in 0 until n) {
            preStepCallback.invoke(i)
            while (imageQueue.isEmpty()) { // waiting for queue
                synchronized(imageQueue) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    (imageQueue as Object).wait()
                }
            }
            val image = imageQueue.poll()!!
            val frame = converter.getFrame(image)
            for (j in 0 until framePerStep) {
                recorder!!.record(frame, avutil.AV_PIX_FMT_ARGB)
            }
            postStepCallback.invoke(i)
        }
        t.join() // wait game thread to exit
    }


    fun stopEncoding() {
        require(recorder != null) { "Encoding not started" }
        recorder!!.stop()
        recorder!!.close()
    }

    override fun close() {
        stopEncoding()
        closeGameCallback?.invoke()
    }
}
