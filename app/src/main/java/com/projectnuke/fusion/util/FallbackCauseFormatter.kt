package com.projectnuke.fusion.util

import com.projectnuke.fusion.llm.FallbackReason
import com.projectnuke.fusion.llm.RuntimeBackend
import com.projectnuke.fusion.llm.RuntimeExecutionSnapshot
import com.projectnuke.fusion.llm.RuntimeAttemptSnapshot
import com.projectnuke.fusion.llm.RuntimeFallbackEvent

object FallbackCauseFormatter {

    fun format(snapshot: RuntimeExecutionSnapshot): String {
        return formatEvents(snapshot.fallbackEvents)
    }

    fun format(attemptSnapshot: RuntimeAttemptSnapshot): String {
        return formatEvents(attemptSnapshot.fallbackEvents)
    }

    fun formatEvents(events: List<RuntimeFallbackEvent>): String {
        if (events.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for (event in events) {
            val text = when (event.reason) {
                FallbackReason.MTP_UNSUPPORTED -> {
                    "MTP를 지원하지 않아 MTP를 비활성화했습니다."
                }
                FallbackReason.MTP_SKIPPED_RECENT_FAILURE -> {
                    "이전 MTP 실패로 MTP를 건너뜁니다."
                }
                FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE -> {
                    "${event.attemptedTextBackend?.localizedName()} 백엔드가 최근 실패로 건너뜁니다."
                }
                FallbackReason.BACKEND_ENGINE_INIT_FAILED -> {
                    "${event.attemptedTextBackend?.localizedName()} 백엔드 초기화에 실패했습니다."
                }
                FallbackReason.SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED -> {
                    "예측 디코딩 활성화 플래그 적용에 실패했습니다."
                }
                FallbackReason.SPECULATIVE_DISABLE_FLAG_SETTLEMENT_FAILED -> {
                    "예측 디코딩 비활성화 플래그 적용에 실패했습니다."
                }
                FallbackReason.MTP_ENGINE_INIT_FAILED -> {
                    "MTP 엔진 초기화에 실패했습니다."
                }
                FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED -> {
                    "GPU 텍스트 엔진 실패로 CPU를 사용합니다."
                }
                FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED -> {
                    "GPU 비전 백엔드를 사용할 수 없어 CPU 비전을 사용합니다."
                }
                FallbackReason.ALL_CANDIDATES_EXHAUSTED -> {
                    "모든 후보 백엔드가 실패했습니다."
                }
                FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE -> {
                    "모든 후보 백엔드가 최근 실패로 건너뛰어졌습니다."
                }
            }
            if (text.isNotBlank() && text !in parts) {
                parts.add(text)
            }
        }
        return parts.joinToString(" | ")
    }

    fun formatFallbackSummary(snapshot: RuntimeExecutionSnapshot): String {
        val events = snapshot.fallbackEvents
        if (events.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for (event in events) {
            when (event.reason) {
                FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED -> {
                    parts.add("GPU 초기화 실패로 CPU를 사용했습니다.")
                }
                FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED -> {
                    parts.add("GPU 비전 백엔드를 사용할 수 없어 CPU 비전을 사용했습니다.")
                }
                FallbackReason.MTP_ENGINE_INIT_FAILED -> {
                    if (event.attemptedMtpEnabled == true) {
                        parts.add("MTP 초기화 실패로 비-MTP를 사용합니다.")
                    }
                }
                FallbackReason.BACKEND_ENGINE_INIT_FAILED -> {
                    parts.add("${event.attemptedTextBackend?.localizedName()} 백엔드 초기화에 실패했습니다.")
                }
                FallbackReason.ALL_CANDIDATES_EXHAUSTED -> {
                    parts.add("所有候选后端均失败")
                }
                FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE -> {
                    parts.add("所有候选后端均因最近失败被跳过")
                }
                else -> {
                    val text = formatSingleEvent(event)
                    if (text.isNotBlank()) parts.add(text)
                }
            }
        }
        return parts.joinToString("\n")
    }

    fun formatAttemptFallbackSummary(attemptSnapshot: RuntimeAttemptSnapshot): String {
        val events = attemptSnapshot.fallbackEvents
        if (events.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for (event in events) {
            when (event.reason) {
                FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED -> {
                    parts.add("GPU 初始化失败后使用CPU")
                }
                FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED -> {
                    parts.add("GPU 비전 백엔드를 사용할 수 없어 CPU 비전을 사용했습니다.")
                }
                FallbackReason.MTP_ENGINE_INIT_FAILED -> {
                    if (event.attemptedMtpEnabled == true) {
                        parts.add("MTP 初始化失败后使用非MTP")
                    }
                }
                FallbackReason.BACKEND_ENGINE_INIT_FAILED -> {
                    parts.add("${event.attemptedTextBackend?.localizedName()} 백엔드 초기화에 실패했습니다.")
                }
                FallbackReason.ALL_CANDIDATES_EXHAUSTED -> {
                    parts.add("所有候选后端均失败")
                }
                FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE -> {
                    parts.add("所有候选后端均因最近失败被跳过")
                }
                else -> {
                    val text = formatSingleEvent(event)
                    if (text.isNotBlank()) parts.add(text)
                }
            }
        }
        return parts.joinToString("\n")
    }

    private fun formatSingleEvent(event: RuntimeFallbackEvent): String {
        return when (event.reason) {
            FallbackReason.MTP_UNSUPPORTED -> "MTP 미지원"
            FallbackReason.MTP_SKIPPED_RECENT_FAILURE -> "MTP 최근 실패 건너뜀"
            FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE -> "${event.attemptedTextBackend?.localizedName()} 최근 실패 건너뜀"
            FallbackReason.BACKEND_ENGINE_INIT_FAILED -> "${event.attemptedTextBackend?.localizedName()} 초기화 실패"
            FallbackReason.SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED -> "예측 디코딩 활성化失败"
            FallbackReason.SPECULATIVE_DISABLE_FLAG_SETTLEMENT_FAILED -> "예측 디코딩 비활성化失败"
            FallbackReason.MTP_ENGINE_INIT_FAILED -> "MTP 初始化失败"
            FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED -> "GPU 텍스트 실패 → CPU"
            FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED -> "GPU 비전 실패 → CPU 비전"
            FallbackReason.ALL_CANDIDATES_EXHAUSTED -> "后所有候选均失败"
            FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE -> "后所有候选均被跳过"
        }
    }

    private fun RuntimeBackend?.localizedName(): String = when (this) {
        RuntimeBackend.GPU -> "GPU"
        RuntimeBackend.CPU -> "CPU"
        RuntimeBackend.UNKNOWN -> "确认不可用"
        null -> "确认不可用"
    }
}