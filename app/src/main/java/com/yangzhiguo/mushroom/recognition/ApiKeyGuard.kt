package com.yangzhiguo.mushroom.recognition

import com.yangzhiguo.mushroom.BuildConfig

/**
 * 启动期 guard：检查当前模型供应商对应的 API key 是否已配置。
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

    fun check(provider: AiModelProvider): Status {
        val key = when (provider) {
            AiModelProvider.DOUBAO -> BuildConfig.ARK_API_KEY
            AiModelProvider.MINIMAX -> BuildConfig.MINIMAX_API_KEY
        }
        return when {
            key.isBlank() -> Status.Missing
            provider == AiModelProvider.MINIMAX && key.trim() == PLACEHOLDER -> Status.Missing
            provider == AiModelProvider.MINIMAX && !key.startsWith("sk-cp-") -> Status.Missing
            provider == AiModelProvider.DOUBAO && !key.startsWith("ark-") -> Status.Missing
            else -> Status.Configured
        }
    }

    /**
     * 启动期检查：未配置时抛 [MissingApiKeyException]，调用方应 catch 后
     * 跳到 onboarding 引导用户配置。
     */
    fun require(provider: AiModelProvider) {
        if (check(provider) is Status.Missing) {
            val keyName = when (provider) {
                AiModelProvider.DOUBAO -> "ARK_API_KEY"
                AiModelProvider.MINIMAX -> "MINI_MAX_API_KEY"
            }
            throw MissingApiKeyException(
                "${provider.displayName} 的 API key 未配置。请在 local.properties 中设置 " +
                    "$keyName，参考 .env.example。"
            )
        }
    }
}

class MissingApiKeyException(message: String) : IllegalStateException(message)
