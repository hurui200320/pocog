package info.skyblond.pocog

import io.humble.video.*
import io.humble.video.awt.MediaPictureConverterFactory
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.random.Random


fun main() {
    val videoWidth = 1920
    val videoHeight = 1080
    val cellSize = 10
    val framerate = 60 // 60 fps
    val framePerStep = 12 // 200ms
    val seed = 12345678
    val step = 2000

    val random = Random(seed)

    val images = sequence {
        val game = ConwaysGame(
            videoWidth / cellSize,
            videoHeight / cellSize,
            12
        )
        game.reset { _, _ -> random.nextBoolean() }
        val startTime = System.currentTimeMillis()
        var lastTickTime = startTime
        for (i in 0..step) {
            val currentTime = System.currentTimeMillis()
            println(
                "Generating step#$i, " +
                        "duration: ${(currentTime - startTime) / 1000.0}s, " +
                        "speed: ${currentTime - lastTickTime}ms/step"
            )
            lastTickTime = currentTime
            // simulate game
            game.calculateNextTick()
            // make sure we're correct
            game.checkResult()
            // generate image for current status
            val image = game.generateBufferedImage(
                videoWidth, videoHeight, cellSize,
                Color.BLACK, Color.GRAY,
                Color.WHITE, Color.LIGHT_GRAY, Color.DARK_GRAY
            )
            val convertedImage = MediaPictureConverterFactory.convertToType(image, BufferedImage.TYPE_3BYTE_BGR)
            repeat(framePerStep) { yield(convertedImage) }
            game.swapToNextTick()
        }
        game.close()
    }

    encodeToVideo(
        "output_seed_${seed}_step_${step}.mp4", "mp4",
        videoWidth, videoHeight, framerate,
        images
    )
}

fun ConwaysGame.generateBufferedImage(
    videoWidth: Int, videoHeight: Int, cellSize: Int,
    background: Color, grid: Color,
    alive: Color, aboutToDie: Color, aboutToLive: Color
): BufferedImage {
    val result = BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_INT_ARGB)
    this.useCellStatus { query ->
        for (y in 0 until videoHeight) {
            val cellY = y / cellSize
            for (x in 0 until videoWidth) {
                if (x % cellSize == 0 || y % cellSize == 0) {
                    result.setRGB(x, y, grid.rgb)
                    continue // grid
                }
                val cellX = x / cellSize
                val currentStatus = query(cellX, cellY, false)
                val nextStatus = query(cellX, cellY, true)
                if (currentStatus) {
                    // current alive
                    if (nextStatus) result.setRGB(x, y, alive.rgb)
                    else result.setRGB(x, y, aboutToDie.rgb)
                } else {
                    // current dead
                    if (nextStatus) result.setRGB(x, y, aboutToLive.rgb)
                    else result.setRGB(x, y, background.rgb)
                }
            }
        }
    }
    // TODO add text "Step 0001" at the bottom left?
    return result
}

fun get3ByteBGRDescriptor(): String {
    for (converterType in MediaPictureConverterFactory.getRegisteredConverters())
        if (converterType.imageType == BufferedImage.TYPE_3BYTE_BGR)
            return converterType.descriptor
    error("Descriptor for TYPE_3BYTE_BGR not found")
}

fun encodeToVideo(
    filename: String, formatName: String,
    videoWidth: Int, videoHeight: Int, framerate: Int,
    images: Sequence<BufferedImage>
) {
// the object for writing to a file
    val muxer = Muxer.make(filename, null, formatName)
    // get the format and codec from C world
    val format = muxer.format
    val codec = Codec.findEncodingCodec(format.defaultVideoCodecId)
    // create a encoder
    val encoder: Encoder = Encoder.make(codec)
    // set resolution
    encoder.width = videoWidth
    encoder.height = videoHeight
    // set pixel format, most common one
    encoder.pixelFormat = PixelFormat.Type.PIX_FMT_YUV420P
    // 30fps
    encoder.timeBase = Rational.make(1, framerate)
    // Some formats need global (rather than per-stream) headers,
    // and in that case you have to tell the encoder. And since Encoders
    // are decoupled from Muxers, there is no easy way to know this beyond
    if (format.getFlag(ContainerFormat.Flag.GLOBAL_HEADER))
        encoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true)
    // open the encoder
    encoder.open(null, null)
    // add this stream to the muxer
    muxer.addNewStream(encoder)
    // finally, open the muxer
    muxer.open(null, null)
    // make a convertor that convert our source to the right MediaPicture format
    // objects to encode data with.
    // Java's BufferedImage use some variant of Red-Green-Blue image encoding
    // (a.k.a. RGB or BGR). Most video codecs use some variant of YCrCb formatting.
    // So we're going to have to convert.
    // this picture is our target format
    val picture = MediaPicture.make(
        encoder.width, encoder.height, encoder.pixelFormat
    )
    picture.timeBase = encoder.timeBase
    // we create converter using a sample image
    val converter = MediaPictureConverterFactory.createConverter(get3ByteBGRDescriptor(), picture)
    // now we can start looping and generate video,
    // by encoding and writing those packets
    val packet = MediaPacket.make()
    var timestamp = 0L
    images.forEach { image ->
        converter.toPicture(picture, image, timestamp++)
        do {// encoder sometimes has buffer too, so we need to see if it's complete
            // or waiting for more pictures
            encoder.encode(packet, picture)
            if (packet.isComplete) muxer.write(packet, false)
        } while (packet.isComplete)
        // time to move to next frame
    }
    // encode the last bit
    do {
        encoder.encode(packet, null) // let encoder know this is the end
        if (packet.isComplete) muxer.write(packet, false)
    } while (packet.isComplete)
    // close the muxer
    muxer.close()
}
