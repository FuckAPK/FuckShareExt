package probe

class Intent
object Log { const val ERROR = 6 }
interface Chain {
    fun getArg(index: Int): Any?
    val args: List<Any?>
    val thisObject: Any?
    fun proceed(): Any?
    fun proceed(args: Array<Any?>): Any?
}
interface Hooker { fun intercept(chain: Chain): Any? }
class Record(val key: Key)
class Key(val type: Int, var requestIntent: Intent?, val packageName: String = "example")
class HookProbe(var rewritten: Intent? = Intent(), var preprocessingError: Throwable? = null) {
    companion object { const val TAG = "probe" }
    private fun log(vararg args: Any?) {}
    private fun process(intent: Intent, caller: String): Intent? {
        preprocessingError?.let { throw it }
        return rewritten
    }
    private fun getField(value: Any?, name: String): Any? = when {
        value is Record && name == "key" -> value.key
        value is Key && name == "type" -> value.type
        value is Key && name == "requestIntent" -> value.requestIntent
        value is Key && name == "packageName" -> value.packageName
        else -> null
    }
    private fun setField(value: Any?, name: String, replacement: Any?) {
        if (value is Key && name == "requestIntent") value.requestIntent = replacement as? Intent
    }
    fun invoke(index: Int, chain: Chain): Any? = when (index) {
        0 -> StartActivityForResultHooker()
        1 -> StartActivityAsUserHooker()
        2 -> StartActivityIntentSenderHooker()
        else -> PendingIntentRecordSendInnerHooker()
    }.intercept(chain)
    // PRODUCTION_INTERCEPTORS
}
