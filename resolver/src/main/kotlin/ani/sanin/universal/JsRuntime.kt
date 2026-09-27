package ani.sanin.universal

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

object JsRuntime {
 fun evaluate(expression:String):String { require(expression.length<=32_000); val cx=Context.enter(); return try { cx.optimizationLevel=-1; cx.instructionObserverThreshold=100_000; val scope=cx.initSafeStandardObjects(); ScriptableObject.deleteProperty(scope,"Packages"); ScriptableObject.deleteProperty(scope,"java"); Context.toString(cx.evaluateString(scope,expression,"sanin-resolver",1,null)) } finally { Context.exit() } }
}
