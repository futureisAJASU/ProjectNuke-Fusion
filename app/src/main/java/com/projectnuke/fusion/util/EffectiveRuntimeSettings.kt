package com.projectnuke.fusion.util

import com.projectnuke.fusion.llm.MtpRuntimeStatus
import com.projectnuke.fusion.model.GenerationSettings
import kotlin.math.absoluteValue

data class EffectiveRuntimeSettings(
    val modelName: String,
    val modelPath: String?,
    val acceleratorRequested: String,
    val actualBackend: String,
    val mtpEnabled: Boolean,
    val mtpStatus: MtpRuntimeStatus,
    val maxTokens: Int,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val reasoningEnabled: Boolean,
    val webSearchEnabled: Boolean,
    val settingsRevision: Int,
    val engineRevision: Int
)

fun buildEffectiveRuntimeSettings(
    modelName: String,
    modelPath: String?,
    settings: GenerationSettings,
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    mtpStatus: MtpRuntimeStatus
): EffectiveRuntimeSettings {
    val requestedBackend = settings.accelerator.name
    val revisionSource = listOf(
        modelName,
        modelPath.orEmpty(),
        requestedBackend,
        settings.speculativeDecodingEnabled?.toString().orEmpty(),
        settings.maxTokens.toString(),
        settings.temperature.toString(),
        settings.topK.toString(),
        settings.topP.toString(),
        settings.reasoningBudgetTokens.toString()
    ).joinToString("|")
    val revision = revisionSource.hashCode().absoluteValue

    return EffectiveRuntimeSettings(
        modelName = modelName,
        modelPath = modelPath,
        acceleratorRequested = requestedBackend,
        actualBackend = requestedBackend,
        mtpEnabled = settings.speculativeDecodingEnabled == true,
        mtpStatus = mtpStatus,
        maxTokens = settings.maxTokens,
        temperature = settings.temperature,
        topK = settings.topK,
        topP = settings.topP,
        reasoningEnabled = reasoningEnabled,
        webSearchEnabled = webSearchEnabled,
        settingsRevision = revision,
        engineRevision = revision
    )
}

fun MtpRuntimeStatus.toKoreanMtpStatus(): String {
    return when (this) {
        MtpRuntimeStatus.OFF -> "꺼짐"
        MtpRuntimeStatus.REQUESTED -> "요청됨"
        MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST -> "MTP 요청으로 초기화됨"
        MtpRuntimeStatus.RUNTIME_CONFIRMED_ACTIVE -> "실행 중 활성화됨"
        MtpRuntimeStatus.UNSUPPORTED -> "미지원"
        MtpRuntimeStatus.FALLBACK_DISABLED -> "대체 비활성"
        MtpRuntimeStatus.FAILED -> "적용 실패"
    }
}

fun buildEffectiveSettingsLine(settings: EffectiveRuntimeSettings): String {
    return "settings rev ${settings.settingsRevision} · ${settings.actualBackend} · MTP ${settings.mtpStatus.toKoreanMtpStatus()} · max ${settings.maxTokens} · temp ${settings.temperature} · topK ${settings.topK} · topP ${settings.topP}"
}
