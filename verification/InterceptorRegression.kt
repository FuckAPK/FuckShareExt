import probe.*

fun main() {
    var scenarios = 0
    for (index in 0..3) for (mode in 0..3) {
        val key = Key(2, Intent())
        val args: List<Any?> = when (index) {
            0 -> listOf(Intent())
            1 -> listOf(null, "example", null, Intent())
            2 -> listOf(null, Record(key), null, null)
            else -> listOf()
        }
        val originalError = IllegalStateException("original")
        var calls = 0
        var passed: Array<Any?>? = null
        val chain = object : Chain {
            override val args = args
            override val thisObject = Record(key)
            override fun getArg(index: Int): Any? = args[index]
            override fun proceed(): Any? { calls++; if (mode == 3) throw originalError; return "ok" }
            override fun proceed(args: Array<Any?>): Any? { passed = args; return proceed() }
        }
        val probe = HookProbe()
        if (mode == 1) probe.rewritten = null
        if (mode == 2) probe.preprocessingError = IllegalArgumentException("preprocessing")
        val result = runCatching { probe.invoke(index, chain) }
        check(calls == 1) { "interceptor $index mode $mode called original $calls times" }
        if (mode == 3) check(result.exceptionOrNull() === originalError) else check(result.getOrThrow() == "ok")
        if (mode == 1 || mode == 2) check(passed == null)
        if (mode == 0 && index < 3) check(passed!![if (index == 0) 0 else 3] === probe.rewritten)
        scenarios++
    }
    println("Interceptor regressions: $scenarios scenarios, exactly one original call and exception identity preserved")
}
