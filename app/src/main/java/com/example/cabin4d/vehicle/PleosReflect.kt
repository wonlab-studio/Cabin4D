package com.example.cabin4d.vehicle

import android.util.Log
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation

/**
 * Vehicle SDK 도메인 API를 이름으로 호출하는 도우미.
 *
 * 왜 리플렉션인가: 공개 문서에서 setHvacFanSpeed 등의 정확한 파라미터 시그니처를 확인할 수 없어서,
 * 공식 샘플(pleos-sample-vehicle-app)의 API 탐색기와 같은 방식으로 런타임에 파라미터를 맞춰 부른다.
 * 첫 실행 때 Logcat(태그 Cabin4D)에 실제 시그니처가 찍히므로, 확인 후 PleosActuator를 타입 호출로
 * 바꾸는 것을 권장한다(컴파일 타임 검사 + 난독화 가능).
 */
internal object PleosReflect {
    const val TAG = "Cabin4D"

    /** Enum이면 names 중 일치하는 상수를, Int면 intValue를 넣는다. 송풍 방향처럼 표현이 갈릴 수 있는 값용. */
    data class EnumOrInt(val names: List<String>, val intValue: Int)

    fun dumpSignatures(apiClass: KClass<*>, keywords: List<String>) {
        apiClass.declaredMemberFunctions
            .filter { f -> keywords.any { f.name.contains(it, ignoreCase = true) } }
            .sortedBy { it.name }
            .forEach { Log.i(TAG, "SDK ${apiClass.simpleName}.${signature(it)}") }
    }

    /**
     * @param hints 파라미터 이름(또는 이름 조각, 소문자 비교) → 넣을 값. 정확히 같은 이름을 먼저 찾고, 없으면 포함 관계로 찾는다.
     * @return SDK 함수를 실제로 호출했으면 true. 결과는 onComplete / onFailed로 비동기 전달.
     */
    fun call(
        apiClass: KClass<*>,
        target: Any,
        name: String,
        hints: Map<String, Any?> = emptyMap(),
        onComplete: (List<Any?>) -> Unit = {},
        onFailed: (Throwable) -> Unit = { Log.w(TAG, "$name 실패: ${it.message}") }
    ): Boolean {
        // 같은 이름의 오버로드가 여럿이면(예: checkHvacFanSpeedCapabilities) deprecated가 아닌 것,
        // 그중에서도 영역별 Map/List를 돌려주는 새 형식을 먼저 시도한다(SDK 2.3.1에서 형식이 바뀜).
        val candidates = apiClass.declaredMemberFunctions
            .filter { it.name == name }
            .sortedWith(
                compareBy<KFunction<*>> { fn -> if (fn.findAnnotation<Deprecated>() != null) 1 else 0 }
                    .thenBy { fn -> if (fn.parameters.any { p -> p.type.toString().let { "Map<" in it || "List<" in it } }) 0 else 1 }
            )
        if (candidates.isEmpty()) {
            Log.w(TAG, "${apiClass.simpleName}.$name 함수가 이 SDK 버전에 없음")
            return false
        }
        for (fn in candidates) {
            val args = buildArgs(fn, target, hints, onComplete, onFailed) ?: continue
            try {
                fn.callBy(args)
            } catch (e: Exception) {
                onFailed((e as? InvocationTargetException)?.targetException ?: e)
            }
            return true
        }
        Log.w(
            TAG,
            "${apiClass.simpleName}.$name 인자를 맞추지 못함. hints=${hints.keys}, 실제 시그니처: " +
                candidates.joinToString(" | ") { signature(it) }
        )
        return false
    }

    private fun buildArgs(
        fn: KFunction<*>,
        target: Any,
        hints: Map<String, Any?>,
        onComplete: (List<Any?>) -> Unit,
        onFailed: (Throwable) -> Unit
    ): Map<KParameter, Any?>? {
        val args = mutableMapOf<KParameter, Any?>()
        for (p in fn.parameters) {
            if (p.kind == KParameter.Kind.INSTANCE) {
                args[p] = target
                continue
            }
            val cls = p.type.classifier as? KClass<*> ?: return null
            val pname = p.name.orEmpty().lowercase()

            val callback = callbackFor(cls, isFailure = pname.contains("fail"), onComplete, onFailed)
            if (callback != null) {
                args[p] = callback
                continue
            }

            val matching = hints.entries.filter { it.key.lowercase() == pname } +
                hints.entries.filter { it.key.lowercase() != pname && pname.contains(it.key.lowercase()) }
            val converted = matching.firstNotNullOfOrNull { convert(it.value, cls) }
            when {
                converted != null -> args[p] = converted
                p.isOptional -> Unit // 기본값 사용
                else -> return null
            }
        }
        return args
    }

    private fun callbackFor(
        cls: KClass<*>,
        isFailure: Boolean,
        onComplete: (List<Any?>) -> Unit,
        onFailed: (Throwable) -> Unit
    ): Any? {
        fun deliver(values: List<Any?>) {
            if (isFailure) {
                onFailed(values.firstOrNull() as? Throwable ?: IllegalStateException(values.toString()))
            } else {
                onComplete(values)
            }
        }
        // 서로 다른 함수 타입을 한 when에서 바로 반환하면 컴파일되지 않으므로 타입을 지정한 지역 변수로 받는다.
        return when (cls) {
            Function0::class -> { val f: () -> Unit = { deliver(emptyList()) }; f }
            Function1::class -> { val f: (Any?) -> Unit = { a -> deliver(listOf(a)) }; f }
            Function2::class -> { val f: (Any?, Any?) -> Unit = { a, b -> deliver(listOf(a, b)) }; f }
            Function3::class -> { val f: (Any?, Any?, Any?) -> Unit = { a, b, c -> deliver(listOf(a, b, c)) }; f }
            else -> null
        }
    }

    private fun convert(value: Any?, cls: KClass<*>): Any? {
        if (value == null) return null
        if (value is EnumOrInt) {
            return when {
                cls == Int::class -> value.intValue
                cls.java.isEnum -> cls.java.enumConstants?.firstOrNull { c ->
                    value.names.any { (c as Enum<*>).name.equals(it, ignoreCase = true) }
                }
                else -> null
            }
        }
        if (cls.isInstance(value)) return value
        return when {
            cls == Int::class && value is Number -> value.toInt()
            cls == Float::class && value is Number -> value.toFloat()
            cls == Long::class && value is Number -> value.toLong()
            cls == Double::class && value is Number -> value.toDouble()
            cls.java.isEnum && value is String -> cls.java.enumConstants?.firstOrNull {
                (it as Enum<*>).name.equals(value, ignoreCase = true)
            }
            else -> null
        }
    }

    private fun signature(fn: KFunction<*>): String =
        fn.name + fn.parameters
            .filter { it.kind == KParameter.Kind.VALUE }
            .joinToString(prefix = "(", postfix = ")") { "${it.name}: ${it.type}" }
}
