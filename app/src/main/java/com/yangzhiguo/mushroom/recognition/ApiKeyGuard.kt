package com.yangzhiguo.mushroom.recognition

import com.yangzhiguo.mushroom.BuildConfig

/**
 * 启动期 guard：检查 BuildConfig.MINIMAX_API_KEY 是否仍是占位符或空。
 *
 * 设计意图：把"忘记在 local.properties 填 key"这种错误尽早暴露在 App 启动时
 * （或首次拍照时），而不是流式响应中收到 401 才反馈给用户。
 *
 * 占位符 `sk-cp-***REDACTED***` 与空字符串均视为未配置。
 */
object ApiKeyGuard {

    private const val PLACEHOLDER = "sk-cp-***REDACTED***"

    sealed class Status {
        /** key 看起来是真实签发值（以 sk-cp- 开头且非占位符）。 */
        data object Configured : Status()
        /** 未配置（key 为空或等于占位符）。 */
        data object Missing : Status()
    }

    fun check(): Status {
        val key = BuildConfig.MINIMAX_API_KEY
        return when {
            key.isBlank() -> Status.Missing
            key.trim() == PLACEHOLDER -> Status.Missing
            !key.startsWith("sk-cp-") -> Status.Missing
            else -> Status.Configured
        }
    }

    /**
     * 启动期检查：未配置时抛 [MissingApiKeyException]，调用方应 catch 后
     * 跳到 onboarding 引导用户配置。
     */
    fun require() {
        if (check() is Status.Missing) {
            throw MissingApiKeyException(
                "MINIMAX_API_KEY 未配置。请在 local.properties 中设置 " +
                    "MINI_MAX_API_KEY=sk-cp-xxx 真实值（占位符或空均无效）。" +
                    "参考 .env.example。"
            )
        }
    }
}

class MissingApiKeyException(message: String) : IllegalStateException(message)
