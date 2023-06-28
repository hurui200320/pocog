package info.skyblond.pocog

import info.skyblond.pocog.game.ConwaysGameCPU
import org.openjdk.jmh.annotations.*
import kotlin.random.Random


open class TestBench {

    @State(Scope.Benchmark)
    open class ExecutionPlan {

        @Param("25", "33", "42", "50")
        open var heightSubdivisionSize = 0

        private val random = Random(1234)

        @Volatile
        lateinit var game: ConwaysGameCPU

        @Setup
        fun setUp() {
            game = ConwaysGameCPU(
                600, 600,
                heightSubdivisionSize
            )
            game.reset { _, _ -> random.nextBoolean() }
        }

        @TearDown
        fun tearDown() {
//            game.close()
        }
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 5)
    @BenchmarkMode(Mode.Throughput)
    fun conway(plan: ExecutionPlan) {
        plan.game.swapToNextTick()
    }
}
