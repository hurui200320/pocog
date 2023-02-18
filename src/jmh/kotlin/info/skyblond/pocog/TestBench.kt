package info.skyblond.pocog

import org.openjdk.jmh.annotations.*
import kotlin.random.Random


open class TestBench {

    @State(Scope.Benchmark)
    open class ExecutionPlan {

        @Param("25", "33", "42", "50")
        open var heightSubdivisionSize = 0

        @Param("12")
        open var parallelism = 0

        private val random = Random(1234)

        @Volatile
        lateinit var game: ConwaysGame

        @Setup
        fun setUp() {
            game = ConwaysGame(
                600, 600,
                parallelism, heightSubdivisionSize
            )
            game.reset { _, _ -> random.nextBoolean() }
        }

        @TearDown
        fun tearDown() {
            game.close()
        }
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 5)
    @BenchmarkMode(Mode.Throughput)
    fun conway(plan: ExecutionPlan) {
        plan.game.calculateNextTick()
        plan.game.swapToNextTick()
    }
}
