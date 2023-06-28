package info.skyblond.pocog.game

import com.jogamp.opencl.CLBuffer
import com.jogamp.opencl.CLContext
import com.jogamp.opencl.CLMemory
import info.skyblond.pocog.WorldMap
import java.nio.IntBuffer

class ConwaysGameOpenCL(
    gameWidth: Int, gameHeight: Int,
    /**
     * How many rows are updated in one OpenCL kernel
     * */
    val rowPerKernel: Int = 16
) : ConwaysGame(gameWidth, gameHeight), AutoCloseable {

    /**
     * OpenCL context.
     * */
    private val context: CLContext = CLContext.create()

    /**
     * OpenCL device, use the most powerful one.
     * */
    private val device = context.maxFlopsDevice

    /**
     * Command queue, or execution queue.
     * */
    private val queue = device.createCommandQueue()

    /**
     * The local work size, mostly defined by the hardware.
     * Clap it to 256.
     * */
    private val localWorkSize: Int = device.maxWorkGroupSize.coerceAtMost(256)

    /**
     * The global work size, defines how many task we have in total.
     * Must be the multiple of [localWorkSize].
     * Here we define each task handle a word, aka 32 cells.
     * */
    private val globalWorkSize: Int = calculateGlobalWorkSize(localWorkSize, cellState.wordWidth)

    /**
     * Each time we update [rowPerKernel] rows per kernel
     * */
    private val inputBuffers = Array(rowPerKernel + 2) {
        context.createIntBuffer(cellState.wordWidth, CLMemory.Mem.READ_ONLY)
    }
    private val outputBuffers = Array(rowPerKernel) {
        context.createIntBuffer(cellState.wordWidth, CLMemory.Mem.WRITE_ONLY)
    }

    /**
     * The compiled OpenCL program.
     * */
    private val program = context.createProgram(
        this::class.java.getResourceAsStream("/cl_kernels/conways_game.cl")!!
            .bufferedReader().use { it.readText() }
            .replace(
                "%GENERATED_INPUT%",
                (1..inputBuffers.size).joinToString("\n    ") {
                    "__global const int *row${it},"
                }
            )
            .replace(
                "%GENERATED_OUTPUT%",
                (1..outputBuffers.size).joinToString("\n    ") {
                    "__global int *out${it},"
                }
            )
            .replace("%GENERATED_CALCULATION%",
                (1..outputBuffers.size).joinToString("\n    ") {
                    "calculateRow(row${it}, row${it + 1}, row${it + 2}, out${it}, idx, gameWidth);"
                }
            )
//            .also { println("Generated OpenCL kernel code: \n$it") }
    ).build()

    private val kernel = program.createCLKernel("calculateNextTick")
        .also {
            it.putArgs(*inputBuffers, *outputBuffers)
                .putArg(gameWidth)
        }

    private fun CLBuffer<IntBuffer>.fillBuffer(y: Int) {
        this.buffer.let {
            it.clear() // make sure it's ready to write
            cellState.writeRowToIntBuffer(y, it)
        }
    }

    /**
     * Update 8 row of cell, return the nano time used in OpenCL.
     * Ranging from [y] to [y]+7
     * */
    private fun calculateRows(builder: WorldMap.Builder, y: Int): Long {
        for (i in inputBuffers.indices) {
            inputBuffers[i].fillBuffer(y - 1 + i) // ranged from y-1 to y+8
        }
        for (i in outputBuffers.indices) {
            outputBuffers[i].buffer.clear()
        }
        var time: Long = System.nanoTime()
        queue // by using blockingRead = false, we can copy multiple buffers async
            .also {
                for (i in inputBuffers.indices) {
                    it.putWriteBuffer(inputBuffers[i], false)
                }
            }
            // then start the computing
            .put1DRangeKernel(kernel, 0, globalWorkSize.toLong(), localWorkSize.toLong())
            // finally, by using blockingRead = true, we have to wait until:
            // a) the program is finished, and
            // b) the buffer is fully loaded before return
            // otherwise we need to call queue.finish() to wait.
            .also {
                for (i in outputBuffers.indices) {
                    it.putReadBuffer(outputBuffers[i], true)
                }
            }
        time = System.nanoTime() - time

        for (i in outputBuffers.indices) {
            builder.readRowFromIntBuffer(y + i, outputBuffers[i].buffer)
        }

        return time
    }

    var lastStepOpenCLTime: Long = 0
        private set

    override fun calculateNextTick() {
        val builder = WorldMap.Builder(gameWidth, gameHeight)
        lastStepOpenCLTime = (0 until gameHeight step rowPerKernel).map { y ->
            calculateRows(builder, y)
        }.sumOf { it / 1000.0 }.toLong() / 1000 // to ms
        nextCellState = builder.build()
    }

    /**
     * The returned value will be the nearest value that:
     * a) bigger than [globalSize], and
     * b) is the multiple of the [groupSize]
     * */
    private fun calculateGlobalWorkSize(groupSize: Int, globalSize: Int): Int =
        (globalSize % groupSize).let { r ->
            if (r == 0) {
                globalSize
            } else {
                globalSize + groupSize - r
            }
        }

    override fun close() {
        context.release()
    }
}
