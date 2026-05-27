package com.projectnuke.fusion.modelzoo

import android.app.ActivityManager
import android.content.Context
import com.projectnuke.fusion.model.AcceleratorMode
import java.io.File

data class FusionModelCompatibilityReport(
    val formatLabel: String,
    val familyLabel: String,
    val localExecutionStatus: String,
    val memoryWarning: String?,
    val recommendedMaxTokens: Int,
    val recommendedAccelerator: AcceleratorMode,
    val mtpRecommendation: String,
    val npuCandidateStatus: String,
    val summary: String
)

object FusionModelCompatibility {
    fun check(context: Context, spec: FusionModelSpec): FusionModelCompatibilityReport {
        val format = spec.runtimeFormat.takeIf { it != ModelRuntimeFormat.UNKNOWN }
            ?: FusionModelCatalog.runtimeFormatForFile(spec.fileName ?: spec.localPath.orEmpty())
        val totalRamGb = totalRamGb(context)
        val memoryWarning = when {
            spec.availability == ModelAvailability.REMOTE_ONLY -> null
            spec.recommendedRamGb != null && totalRamGb < spec.recommendedRamGb -> "현재 기기 메모리 기준으로 다소 무거울 수 있습니다."
            spec.memoryClass == ModelMemoryClass.HIGH && totalRamGb <= 8.5f -> "현재 기기 메모리 기준으로 다소 무거울 수 있습니다."
            else -> null
        }
        val status = executionStatus(spec, format)
        val recommendedTokens = when {
            spec.availability == ModelAvailability.REMOTE_ONLY -> 0
            totalRamGb <= 8.5f -> spec.recommendedMaxTokens8Gb.coerceIn(1024, 2048)
            else -> spec.recommendedMaxTokens12Gb.takeIf { it > 0 } ?: spec.recommendedMaxTokens8Gb
        }
        val summary = when (status) {
            "실행 가능" -> "이 모델은 현재 Fusion에서 바로 실행할 수 있습니다."
            "변환 필요" -> "이 모델은 변환 후 사용할 수 있습니다."
            "원격 실행 필요" -> "이 모델은 원격 실행이 필요합니다."
            else -> "실행 전에 모델 파일을 다시 확인해 주세요."
        }
        return FusionModelCompatibilityReport(
            formatLabel = format.name,
            familyLabel = spec.family.name,
            localExecutionStatus = status,
            memoryWarning = memoryWarning,
            recommendedMaxTokens = recommendedTokens,
            recommendedAccelerator = if (spec.canRunLocalRuntime) AcceleratorMode.GPU else AcceleratorMode.AUTO,
            mtpRecommendation = if (spec.recommendedMtpEnabled && spec.family == ModelFamily.GEMMA) "권장" else "끄기 권장",
            npuCandidateStatus = if (spec.supportsNpuCandidate) "NPU 후보" else "지원 확인 필요",
            summary = summary
        )
    }

    fun canUseCurrentLiteRtPath(spec: FusionModelSpec): Boolean {
        if (spec.externallyReferenced && spec.localPath.isNullOrBlank()) return false
        val path = spec.localPath
        return spec.canRunLocalRuntime && (path == null || File(path).exists())
    }

    private fun executionStatus(spec: FusionModelSpec, format: ModelRuntimeFormat): String {
        if (spec.availability == ModelAvailability.REMOTE_ONLY) return "원격 실행 필요"
        if (format == ModelRuntimeFormat.NEEDS_CONVERSION || spec.availability == ModelAvailability.NEEDS_CONVERSION) return "변환 필요"
        if (spec.externallyReferenced && spec.localPath.isNullOrBlank()) return "실행 준비 필요"
        if (format == ModelRuntimeFormat.LITERT_LM || format == ModelRuntimeFormat.MEDIAPIPE_LLM) return "실행 가능"
        return "지원 확인 필요"
    }

    private fun totalRamGb(context: Context): Float {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(info)
        return info.totalMem / (1024f * 1024f * 1024f)
    }
}
