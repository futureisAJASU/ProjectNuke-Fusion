package com.projectnuke.fusion.modelzoo

import android.content.Context
import com.projectnuke.fusion.model.AcceleratorMode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class ModelFamily { GEMMA, QWEN, LLAMA, PHI, KIMI, DEEPSEEK, MISTRAL, CUSTOM }
enum class ModelRuntimeFormat { LITERT_LM, MEDIAPIPE_LLM, GGUF, ONNX, NEEDS_CONVERSION, EXYNOS_AI_STUDIO, REMOTE_API, UNKNOWN }
enum class ModelAvailability { READY, NEEDS_CONVERSION, NEEDS_DOWNLOAD, REMOTE_ONLY, UNSUPPORTED_ON_DEVICE, CUSTOM_IMPORTED }
enum class ModelMemoryClass { LOW, MEDIUM, HIGH, SERVER }
enum class ModelRecommendedDeviceClass { RAM_8GB_SAFE, RAM_12GB_RECOMMENDED, RAM_16GB_RECOMMENDED, SERVER_ONLY }

data class FusionModelSpec(
    val id: String,
    val displayName: String,
    val family: ModelFamily,
    val parameterLabel: String,
    val runtimeFormat: ModelRuntimeFormat,
    val availability: ModelAvailability,
    val memoryClass: ModelMemoryClass,
    val recommendedDeviceClass: ModelRecommendedDeviceClass,
    val recommendedMaxTokens8Gb: Int,
    val recommendedMaxTokens12Gb: Int,
    val recommendedMtpEnabled: Boolean,
    val recommendedReasoningEnabled: Boolean,
    val supportsVision: Boolean,
    val supportsReasoning: Boolean,
    val supportsNpuCandidate: Boolean,
    val notes: String,
    val fileName: String? = null,
    val downloadUrl: String? = null,
    val modelPageUrl: String? = null,
    val directDownloadUrl: String? = null,
    val directDownloadFileName: String? = null,
    val directDownloadFormat: String? = null,
    val directDownloadSizeGb: Float? = null,
    val requiresExternalBrowser: Boolean = true,
    val localPath: String? = null,
    val officialUrl: String? = null,
    val huggingFaceModelId: String? = null,
    val modelSizeEstimateGb: Float? = null,
    val minRecommendedRamGb: Int? = null,
    val recommendedRamGb: Int? = null,
    val localExecutionWarning: String? = null,
    val licenseLabel: String? = null,
    val sourceLabel: String? = null,
    val originalFileName: String? = null,
    val uriString: String? = null,
    val fileSizeBytes: Long? = null,
    val addedAt: Long? = null,
    val lastCheckedAt: Long? = null,
    val externallyReferenced: Boolean = false,
    val copiedInternally: Boolean = localPath != null
) {
    val isRemoteOnly: Boolean get() = availability == ModelAvailability.REMOTE_ONLY
    val canRunLocalRuntime: Boolean get() = runtimeFormat == ModelRuntimeFormat.LITERT_LM || runtimeFormat == ModelRuntimeFormat.MEDIAPIPE_LLM
}

object FusionModelCatalog {
    val builtIn: List<FusionModelSpec> = listOf(
        FusionModelSpec(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B-it",
            family = ModelFamily.GEMMA,
            parameterLabel = "E2B",
            runtimeFormat = ModelRuntimeFormat.LITERT_LM,
            availability = ModelAvailability.NEEDS_DOWNLOAD,
            memoryClass = ModelMemoryClass.MEDIUM,
            recommendedDeviceClass = ModelRecommendedDeviceClass.RAM_8GB_SAFE,
            recommendedMaxTokens8Gb = 2048,
            recommendedMaxTokens12Gb = 4096,
            recommendedMtpEnabled = false,
            recommendedReasoningEnabled = false,
            supportsVision = true,
            supportsReasoning = false,
            supportsNpuCandidate = true,
            notes = "기존 Gemma 실행 경로를 사용하는 로컬 모델입니다.",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            officialUrl = "https://ai.google.dev/gemma",
            huggingFaceModelId = "litert-community/gemma-4-E2B-it-litert-lm",
            modelSizeEstimateGb = 3.0f,
            minRecommendedRamGb = 8,
            recommendedRamGb = 8,
            localExecutionWarning = "8GB 기기에서 로컬 실행 후보로 사용할 수 있습니다.",
            sourceLabel = "Google Gemma"
        ),
        FusionModelSpec(
            id = "gemma-4-e4b-it",
            displayName = "Gemma 4 E4B-it",
            family = ModelFamily.GEMMA,
            parameterLabel = "E4B",
            runtimeFormat = ModelRuntimeFormat.LITERT_LM,
            availability = ModelAvailability.NEEDS_DOWNLOAD,
            memoryClass = ModelMemoryClass.HIGH,
            recommendedDeviceClass = ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED,
            recommendedMaxTokens8Gb = 1024,
            recommendedMaxTokens12Gb = 4096,
            recommendedMtpEnabled = false,
            recommendedReasoningEnabled = false,
            supportsVision = true,
            supportsReasoning = false,
            supportsNpuCandidate = true,
            notes = "8GB 기기에서는 실험용이며, 12GB 이상 기기를 권장합니다.",
            fileName = "gemma-4-E4B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
            officialUrl = "https://ai.google.dev/gemma",
            huggingFaceModelId = "litert-community/gemma-4-E4B-it-litert-lm",
            modelSizeEstimateGb = 5.0f,
            minRecommendedRamGb = 8,
            recommendedRamGb = 12,
            localExecutionWarning = "8GB 기기에서는 메모리 압박이 발생할 수 있습니다.",
            sourceLabel = "Google Gemma"
        ),
        FusionModelSpec("qwen3-0_6b-instruct", "Qwen3 0.6B Instruct", ModelFamily.QWEN, "0.6B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.LOW, ModelRecommendedDeviceClass.RAM_8GB_SAFE, 2048, 4096, false, false, false, true, true, "저메모리 기기에서 빠른 응답용으로 적합합니다.", downloadUrl = "https://huggingface.co/Qwen/Qwen3-0.6B", officialUrl = "https://github.com/QwenLM/Qwen3", huggingFaceModelId = "Qwen/Qwen3-0.6B", modelSizeEstimateGb = 1.2f, minRecommendedRamGb = 8, recommendedRamGb = 8, localExecutionWarning = "저메모리 기기에서도 실험하기 적합한 소형 모델입니다.", sourceLabel = "Qwen"),
        FusionModelSpec("qwen3-1_7b-instruct", "Qwen3 1.7B Instruct", ModelFamily.QWEN, "1.7B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.MEDIUM, ModelRecommendedDeviceClass.RAM_8GB_SAFE, 2048, 4096, false, false, false, true, true, "Fusion 기본 대체 모델 후보입니다.", downloadUrl = "https://huggingface.co/Qwen/Qwen3-1.7B", officialUrl = "https://github.com/QwenLM/Qwen3", huggingFaceModelId = "Qwen/Qwen3-1.7B", modelSizeEstimateGb = 3.4f, minRecommendedRamGb = 8, recommendedRamGb = 8, localExecutionWarning = "8GB 기기에서 실험할 수 있지만 maxTokens를 낮게 설정하는 것이 좋습니다.", sourceLabel = "Qwen"),
        FusionModelSpec("qwen3-4b-instruct", "Qwen3 4B Instruct", ModelFamily.QWEN, "4B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, 1024, 4096, false, false, false, true, true, "8GB 기기에서는 실험용으로만 권장합니다.", downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B", officialUrl = "https://github.com/QwenLM/Qwen3", huggingFaceModelId = "Qwen/Qwen3-4B", modelSizeEstimateGb = 8.0f, minRecommendedRamGb = 8, recommendedRamGb = 12, localExecutionWarning = "8GB 기기에서는 메모리 압박이 발생할 수 있습니다.", sourceLabel = "Qwen"),
        FusionModelSpec("qwen3_6-35b-a3b", "Qwen3.6 35B A3B", ModelFamily.QWEN, "35B A3B", ModelRuntimeFormat.REMOTE_API, ModelAvailability.REMOTE_ONLY, ModelMemoryClass.SERVER, ModelRecommendedDeviceClass.SERVER_ONLY, 0, 0, false, false, false, true, false, "서버 또는 원격 실행용 후보입니다.", downloadUrl = "https://huggingface.co/Qwen/Qwen3.6-35B-A3B", officialUrl = "https://github.com/QwenLM/Qwen3.6", huggingFaceModelId = "Qwen/Qwen3.6-35B-A3B", modelSizeEstimateGb = 70.0f, minRecommendedRamGb = 32, recommendedRamGb = 64, localExecutionWarning = "이 모델은 모바일 로컬 실행용이 아닙니다. 서버 또는 원격 실행을 권장합니다.", sourceLabel = "Qwen"),
        FusionModelSpec("llama-3_2-1b-instruct", "Llama 3.2 1B Instruct", ModelFamily.LLAMA, "1B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.LOW, ModelRecommendedDeviceClass.RAM_8GB_SAFE, 2048, 4096, false, false, false, false, true, "생태계와 호환성이 좋은 기준 모델입니다.", downloadUrl = "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct", officialUrl = "https://huggingface.co/meta-llama", huggingFaceModelId = "meta-llama/Llama-3.2-1B-Instruct", modelSizeEstimateGb = 2.0f, minRecommendedRamGb = 8, recommendedRamGb = 8, localExecutionWarning = "8GB 기기에서 기준 모델로 실험하기 적합합니다.", sourceLabel = "Meta Llama"),
        FusionModelSpec("llama-3_2-3b-instruct", "Llama 3.2 3B Instruct", ModelFamily.LLAMA, "3B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, 1024, 4096, false, false, false, false, true, "품질 비교용 모델입니다.", downloadUrl = "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct", officialUrl = "https://huggingface.co/meta-llama", huggingFaceModelId = "meta-llama/Llama-3.2-3B-Instruct", modelSizeEstimateGb = 6.0f, minRecommendedRamGb = 8, recommendedRamGb = 12, localExecutionWarning = "8GB 기기에서는 maxTokens 1024~2048을 권장합니다.", sourceLabel = "Meta Llama"),
        FusionModelSpec("phi-4-mini-instruct", "Phi-4-mini-instruct", ModelFamily.PHI, "mini", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, 1024, 4096, false, false, false, true, true, "정확한 답변, 코딩, 논리 설명용 후보입니다.", downloadUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct", officialUrl = "https://azure.microsoft.com/en-us/products/phi", huggingFaceModelId = "microsoft/Phi-4-mini-instruct", modelSizeEstimateGb = 7.0f, minRecommendedRamGb = 8, recommendedRamGb = 12, localExecutionWarning = "8GB 기기에서는 메모리 압박이 발생할 수 있습니다.", sourceLabel = "Microsoft Phi"),
        FusionModelSpec("phi-4-mini-instruct-litert", "Phi-4-mini-instruct LiteRT", ModelFamily.PHI, "mini", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, 1024, 4096, false, false, false, true, true, "LiteRT 실행 후보입니다.", downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct", officialUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct", huggingFaceModelId = "litert-community/Phi-4-mini-instruct", modelSizeEstimateGb = 7.0f, minRecommendedRamGb = 8, recommendedRamGb = 12, localExecutionWarning = "LiteRT 실행 후보입니다. 실제 호환성은 기기에서 확인해야 합니다.", sourceLabel = "LiteRT Community"),
        FusionModelSpec("phi-4-mini-reasoning", "Phi-4-mini-reasoning", ModelFamily.PHI, "mini", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, 1024, 4096, false, true, false, true, true, "추론 실험용입니다. 8GB 기기에서는 권장하지 않습니다.", downloadUrl = "https://huggingface.co/microsoft/Phi-4-mini-reasoning", officialUrl = "https://azure.microsoft.com/en-us/products/phi", huggingFaceModelId = "microsoft/Phi-4-mini-reasoning", modelSizeEstimateGb = 7.0f, minRecommendedRamGb = 8, recommendedRamGb = 12, localExecutionWarning = "추론 출력이 길어질 수 있어 8GB 기기에서는 권장하지 않습니다.", sourceLabel = "Microsoft Phi"),
        FusionModelSpec("phi-4-multimodal-instruct", "Phi-4-multimodal-instruct", ModelFamily.PHI, "multimodal", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED, 1024, 4096, false, false, true, true, true, "멀티모달 실험용입니다.", officialUrl = "https://azure.microsoft.com/en-us/products/phi", modelSizeEstimateGb = 8.0f, minRecommendedRamGb = 12, recommendedRamGb = 16, localExecutionWarning = "멀티모달 모델입니다. 16GB 이상 기기에서 확인하는 것을 권장합니다.", sourceLabel = "Microsoft Phi"),
        FusionModelSpec("deepseek-r1-distill-qwen-1_5b", "DeepSeek-R1-Distill-Qwen-1.5B", ModelFamily.DEEPSEEK, "1.5B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.MEDIUM, ModelRecommendedDeviceClass.RAM_8GB_SAFE, 1024, 2048, false, true, false, true, true, "소형 추론 모델 실험 후보입니다.", downloadUrl = "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B", officialUrl = "https://huggingface.co/deepseek-ai", huggingFaceModelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B", modelSizeEstimateGb = 3.0f, minRecommendedRamGb = 8, recommendedRamGb = 8, localExecutionWarning = "소형 추론 모델 실험 후보입니다. 출력이 길어질 수 있어 maxTokens를 낮게 설정하는 것이 좋습니다.", sourceLabel = "DeepSeek"),
        FusionModelSpec("deepseek-r1-distill-qwen-7b", "DeepSeek-R1-Distill-Qwen-7B", ModelFamily.DEEPSEEK, "7B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED, 1024, 2048, false, true, false, true, true, "8GB 기기에서는 권장하지 않습니다.", downloadUrl = "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", officialUrl = "https://huggingface.co/deepseek-ai", huggingFaceModelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", modelSizeEstimateGb = 14.0f, minRecommendedRamGb = 12, recommendedRamGb = 16, localExecutionWarning = "8GB 기기에서는 권장하지 않습니다.", sourceLabel = "DeepSeek"),
        FusionModelSpec("deepseek-v3-v4-server", "DeepSeek V3/V4 Server Models", ModelFamily.DEEPSEEK, "Server", ModelRuntimeFormat.REMOTE_API, ModelAvailability.REMOTE_ONLY, ModelMemoryClass.SERVER, ModelRecommendedDeviceClass.SERVER_ONLY, 0, 0, false, true, false, true, false, "원격 API 실행 후보입니다.", officialUrl = "https://api-docs.deepseek.com/", modelSizeEstimateGb = 100.0f, minRecommendedRamGb = 32, recommendedRamGb = 64, localExecutionWarning = "모바일 로컬 실행용이 아닙니다. 원격 API 실행을 권장합니다.", sourceLabel = "DeepSeek"),
        FusionModelSpec("ministral-3b", "Ministral 3B", ModelFamily.MISTRAL, "3B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, 1024, 4096, false, false, false, false, true, "Mistral 소형 모델 후보입니다.", downloadUrl = "https://docs.mistral.ai/models/overview", officialUrl = "https://docs.mistral.ai/models/overview", modelSizeEstimateGb = 6.0f, minRecommendedRamGb = 8, recommendedRamGb = 12, localExecutionWarning = "8GB 기기에서 실험할 수 있지만 메모리 압박이 발생할 수 있습니다.", sourceLabel = "Mistral AI"),
        FusionModelSpec("ministral-8b", "Ministral 8B", ModelFamily.MISTRAL, "8B", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED, 1024, 4096, false, false, false, false, true, "고메모리 기기 실험 후보입니다.", downloadUrl = "https://docs.mistral.ai/models/overview", officialUrl = "https://docs.mistral.ai/models/overview", modelSizeEstimateGb = 16.0f, minRecommendedRamGb = 12, recommendedRamGb = 16, localExecutionWarning = "12GB 이상 기기에서 실험하는 것을 권장합니다.", sourceLabel = "Mistral AI"),
        FusionModelSpec("devstral-small", "Devstral Small", ModelFamily.MISTRAL, "Small", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED, 1024, 4096, false, false, false, false, true, "코딩 에이전트용 모델 후보입니다.", downloadUrl = "https://mistral.ai/models", officialUrl = "https://mistral.ai/models", modelSizeEstimateGb = 24.0f, minRecommendedRamGb = 12, recommendedRamGb = 16, localExecutionWarning = "코딩 에이전트용 모델입니다. 모바일 로컬 실행은 변환과 추가 검증이 필요합니다.", sourceLabel = "Mistral AI"),
        FusionModelSpec("magistral-small", "Magistral Small", ModelFamily.MISTRAL, "Small", ModelRuntimeFormat.LITERT_LM, ModelAvailability.NEEDS_CONVERSION, ModelMemoryClass.HIGH, ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED, 1024, 4096, false, true, false, true, true, "추론 특화 모델 후보입니다.", downloadUrl = "https://mistral.ai/models", officialUrl = "https://mistral.ai/models", modelSizeEstimateGb = 24.0f, minRecommendedRamGb = 12, recommendedRamGb = 16, localExecutionWarning = "추론 특화 모델입니다. 8GB 기기에서는 권장하지 않습니다.", sourceLabel = "Mistral AI"),
        FusionModelSpec("mistral-small-4", "Mistral Small 4", ModelFamily.MISTRAL, "Small 4", ModelRuntimeFormat.REMOTE_API, ModelAvailability.REMOTE_ONLY, ModelMemoryClass.SERVER, ModelRecommendedDeviceClass.SERVER_ONLY, 0, 0, false, false, false, false, false, "서버 또는 고메모리 실행 후보입니다.", downloadUrl = "https://mistral.ai/models", officialUrl = "https://mistral.ai/news/mistral-small-4", modelSizeEstimateGb = 48.0f, minRecommendedRamGb = 16, recommendedRamGb = 24, localExecutionWarning = "모바일 로컬 실행보다는 서버 또는 고메모리 기기를 권장합니다.", sourceLabel = "Mistral AI"),
        FusionModelSpec("le-chat", "Le Chat", ModelFamily.MISTRAL, "Service", ModelRuntimeFormat.REMOTE_API, ModelAvailability.REMOTE_ONLY, ModelMemoryClass.SERVER, ModelRecommendedDeviceClass.SERVER_ONLY, 0, 0, false, false, true, true, false, "Mistral의 원격 AI 서비스입니다.", officialUrl = "https://mistral.ai/news/all-new-le-chat", minRecommendedRamGb = 0, recommendedRamGb = 0, localExecutionWarning = "Le Chat은 모델 파일이 아니라 Mistral의 원격 AI 서비스입니다.", sourceLabel = "Mistral AI"),
        FusionModelSpec("kimi-k2-instruct", "Kimi K2 Instruct", ModelFamily.KIMI, "K2", ModelRuntimeFormat.REMOTE_API, ModelAvailability.REMOTE_ONLY, ModelMemoryClass.SERVER, ModelRecommendedDeviceClass.SERVER_ONLY, 0, 0, false, false, false, true, false, "로컬 실행용이 아니라 서버/API 후보입니다.", downloadUrl = "https://huggingface.co/moonshotai/Kimi-K2-Instruct", officialUrl = "https://github.com/MoonshotAI/Kimi-K2", huggingFaceModelId = "moonshotai/Kimi-K2-Instruct", modelSizeEstimateGb = 100.0f, minRecommendedRamGb = 32, recommendedRamGb = 64, localExecutionWarning = "이 모델은 모바일 로컬 실행용이 아닙니다. 서버 또는 원격 실행을 권장합니다.", sourceLabel = "Moonshot AI")
    )

    fun all(context: Context): List<FusionModelSpec> = builtIn + loadImported(context)

    fun findByNameOrId(context: Context, value: String?): FusionModelSpec? {
        if (value.isNullOrBlank()) return null
        return all(context).firstOrNull { it.id == value || it.displayName == value }
            ?: inferSpec(value)
    }

    fun inferFamily(context: Context, value: String?): ModelFamily =
        findByNameOrId(context, value)?.family ?: inferFamilyFromText(value)

    fun inferFamilyFromText(value: String?): ModelFamily {
        val lower = value.orEmpty().lowercase()
        return when {
            "deepseek" in lower -> ModelFamily.DEEPSEEK
            "mistral" in lower || "ministral" in lower || "magistral" in lower || "devstral" in lower || "le chat" in lower -> ModelFamily.MISTRAL
            "qwen" in lower -> ModelFamily.QWEN
            "llama" in lower -> ModelFamily.LLAMA
            "phi" in lower -> ModelFamily.PHI
            "kimi" in lower -> ModelFamily.KIMI
            "gemma" in lower -> ModelFamily.GEMMA
            else -> ModelFamily.CUSTOM
        }
    }

    fun loadImported(context: Context): List<FusionModelSpec> {
        val file = importedMetadataFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val family = runCatching { ModelFamily.valueOf(item.optString("family", ModelFamily.CUSTOM.name)) }.getOrDefault(ModelFamily.CUSTOM)
                val path = item.optString("path", "")
                val uriString = item.optString("uri", "").ifBlank { null }
                val originalFileName = item.optString("originalFileName", "").ifBlank { null }
                val externallyReferenced = item.optBoolean("externallyReferenced", uriString != null && path.isBlank())
                val copiedInternally = item.optBoolean("copiedInternally", path.isNotBlank() && !externallyReferenced)
                val format = runCatching {
                    ModelRuntimeFormat.valueOf(item.optString("runtimeFormat"))
                }.getOrElse {
                    runtimeFormatForFile(originalFileName ?: path)
                }
                FusionModelSpec(
                    id = item.optString("id"),
                    displayName = item.optString("displayName"),
                    family = family,
                    parameterLabel = item.optString("parameterLabel", "Custom"),
                    runtimeFormat = format,
                    availability = availabilityForImported(format),
                    memoryClass = ModelMemoryClass.MEDIUM,
                    recommendedDeviceClass = ModelRecommendedDeviceClass.RAM_8GB_SAFE,
                    recommendedMaxTokens8Gb = 2048,
                    recommendedMaxTokens12Gb = 4096,
                    recommendedMtpEnabled = family == ModelFamily.GEMMA,
                    recommendedReasoningEnabled = false,
                    supportsVision = false,
                    supportsReasoning = family == ModelFamily.QWEN || family == ModelFamily.PHI || family == ModelFamily.DEEPSEEK || family == ModelFamily.MISTRAL,
                    supportsNpuCandidate = family != ModelFamily.KIMI,
                    notes = importedNotes(format, externallyReferenced),
                    fileName = originalFileName ?: File(path).name,
                    localPath = path.ifBlank { null },
                    minRecommendedRamGb = 8,
                    recommendedRamGb = 8,
                    localExecutionWarning = if (externallyReferenced) {
                        "이 런타임은 앱 내부 파일 경로가 필요할 수 있습니다."
                    } else {
                        "가져온 모델입니다. 실제 호환성은 기기에서 확인해야 합니다."
                    },
                    sourceLabel = if (externallyReferenced) "외부 파일 연결" else "사용자 가져오기",
                    originalFileName = originalFileName,
                    uriString = uriString,
                    fileSizeBytes = item.optLong("fileSizeBytes").takeIf { it > 0L },
                    addedAt = item.optLong("addedAt").takeIf { it > 0L },
                    lastCheckedAt = item.optLong("lastCheckedAt").takeIf { it > 0L },
                    externallyReferenced = externallyReferenced,
                    copiedInternally = copiedInternally
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveImported(context: Context, spec: FusionModelSpec) {
        val current = loadImported(context).filterNot { it.localPath == spec.localPath || it.id == spec.id }
        val array = JSONArray()
        (current + spec).forEach { model ->
            array.put(JSONObject().apply {
                put("id", model.id)
                put("displayName", model.displayName)
                put("family", model.family.name)
                put("parameterLabel", model.parameterLabel)
                put("path", model.localPath.orEmpty())
                put("runtimeFormat", model.runtimeFormat.name)
                put("originalFileName", model.originalFileName ?: model.fileName.orEmpty())
                put("uri", model.uriString.orEmpty())
                put("fileSizeBytes", model.fileSizeBytes ?: 0L)
                put("addedAt", model.addedAt ?: System.currentTimeMillis())
                put("lastCheckedAt", model.lastCheckedAt ?: System.currentTimeMillis())
                put("externallyReferenced", model.externallyReferenced)
                put("copiedInternally", model.copiedInternally)
            })
        }
        importedMetadataFile(context).writeText(array.toString(2))
    }

    fun runtimeFormatForFile(pathOrName: String): ModelRuntimeFormat {
        val lower = pathOrName.lowercase()
        return when {
            lower.endsWith(".litertlm") -> ModelRuntimeFormat.LITERT_LM
            lower.endsWith(".task") -> ModelRuntimeFormat.MEDIAPIPE_LLM
            lower.endsWith(".gguf") -> ModelRuntimeFormat.GGUF
            lower.endsWith(".onnx") -> ModelRuntimeFormat.ONNX
            lower.endsWith(".safetensors") -> ModelRuntimeFormat.NEEDS_CONVERSION
            lower.endsWith(".bin") -> ModelRuntimeFormat.UNKNOWN
            else -> ModelRuntimeFormat.UNKNOWN
        }
    }

    fun removeImported(context: Context, spec: FusionModelSpec) {
        val current = loadImported(context).filterNot { it.id == spec.id }
        val array = JSONArray()
        current.forEach { model ->
            array.put(JSONObject().apply {
                put("id", model.id)
                put("displayName", model.displayName)
                put("family", model.family.name)
                put("parameterLabel", model.parameterLabel)
                put("path", model.localPath.orEmpty())
                put("runtimeFormat", model.runtimeFormat.name)
                put("originalFileName", model.originalFileName ?: model.fileName.orEmpty())
                put("uri", model.uriString.orEmpty())
                put("fileSizeBytes", model.fileSizeBytes ?: 0L)
                put("addedAt", model.addedAt ?: System.currentTimeMillis())
                put("lastCheckedAt", model.lastCheckedAt ?: System.currentTimeMillis())
                put("externallyReferenced", model.externallyReferenced)
                put("copiedInternally", model.copiedInternally)
            })
        }
        importedMetadataFile(context).writeText(array.toString(2))
    }

    fun importedSpec(displayName: String, path: String, family: ModelFamily): FusionModelSpec {
        val format = runtimeFormatForFile(path)
        return FusionModelSpec(
            id = "custom-${System.currentTimeMillis()}",
            displayName = displayName.ifBlank { "Custom Model" },
            family = family,
            parameterLabel = "Custom",
            runtimeFormat = format,
            availability = availabilityForImported(format),
            memoryClass = ModelMemoryClass.MEDIUM,
            recommendedDeviceClass = ModelRecommendedDeviceClass.RAM_8GB_SAFE,
            recommendedMaxTokens8Gb = 2048,
            recommendedMaxTokens12Gb = 4096,
            recommendedMtpEnabled = family == ModelFamily.GEMMA,
            recommendedReasoningEnabled = false,
            supportsVision = false,
            supportsReasoning = family == ModelFamily.QWEN || family == ModelFamily.PHI || family == ModelFamily.DEEPSEEK || family == ModelFamily.MISTRAL,
            supportsNpuCandidate = family != ModelFamily.KIMI,
            notes = importedNotes(format, externallyReferenced = false),
            fileName = File(path).name,
            localPath = path,
            minRecommendedRamGb = 8,
            recommendedRamGb = 8,
            localExecutionWarning = "가져온 모델입니다. 실제 호환성은 기기에서 확인해야 합니다.",
            sourceLabel = "사용자 가져오기",
            originalFileName = File(path).name,
            addedAt = System.currentTimeMillis(),
            lastCheckedAt = System.currentTimeMillis(),
            externallyReferenced = false,
            copiedInternally = true
        )
    }

    fun externalLinkedSpec(displayName: String, originalFileName: String, uriString: String, fileSizeBytes: Long?, family: ModelFamily): FusionModelSpec {
        val format = runtimeFormatForFile(originalFileName)
        return FusionModelSpec(
            id = "external-${System.currentTimeMillis()}",
            displayName = displayName.ifBlank { originalFileName.ifBlank { "External Model" } },
            family = family,
            parameterLabel = "Custom",
            runtimeFormat = format,
            availability = availabilityForImported(format),
            memoryClass = ModelMemoryClass.MEDIUM,
            recommendedDeviceClass = ModelRecommendedDeviceClass.RAM_8GB_SAFE,
            recommendedMaxTokens8Gb = 2048,
            recommendedMaxTokens12Gb = 4096,
            recommendedMtpEnabled = family == ModelFamily.GEMMA,
            recommendedReasoningEnabled = false,
            supportsVision = false,
            supportsReasoning = family == ModelFamily.QWEN || family == ModelFamily.PHI || family == ModelFamily.DEEPSEEK || family == ModelFamily.MISTRAL,
            supportsNpuCandidate = family != ModelFamily.KIMI,
            notes = importedNotes(format, externallyReferenced = true),
            fileName = originalFileName,
            minRecommendedRamGb = 8,
            recommendedRamGb = 8,
            localExecutionWarning = "이 런타임은 앱 내부 파일 경로가 필요할 수 있습니다.",
            sourceLabel = "외부 파일 연결",
            originalFileName = originalFileName,
            uriString = uriString,
            fileSizeBytes = fileSizeBytes,
            addedAt = System.currentTimeMillis(),
            lastCheckedAt = System.currentTimeMillis(),
            externallyReferenced = true,
            copiedInternally = false
        )
    }

    private fun importedMetadataFile(context: Context): File = File(context.filesDir, "fusion_imported_models.json")

    private fun inferSpec(value: String): FusionModelSpec? {
        val family = inferFamilyFromText(value)
        if (family == ModelFamily.CUSTOM) return null
        return FusionModelSpec(
            id = value.lowercase().replace(" ", "-"),
            displayName = value,
            family = family,
            parameterLabel = "Custom",
            runtimeFormat = ModelRuntimeFormat.UNKNOWN,
            availability = ModelAvailability.CUSTOM_IMPORTED,
            memoryClass = ModelMemoryClass.MEDIUM,
            recommendedDeviceClass = ModelRecommendedDeviceClass.RAM_8GB_SAFE,
            recommendedMaxTokens8Gb = 2048,
            recommendedMaxTokens12Gb = 4096,
            recommendedMtpEnabled = family == ModelFamily.GEMMA,
            recommendedReasoningEnabled = false,
            supportsVision = false,
            supportsReasoning = family == ModelFamily.QWEN || family == ModelFamily.PHI || family == ModelFamily.DEEPSEEK || family == ModelFamily.MISTRAL,
            supportsNpuCandidate = family != ModelFamily.KIMI,
            notes = "이름에서 모델 패밀리를 추정했습니다.",
            minRecommendedRamGb = 8,
            recommendedRamGb = 8
        )
    }
}

private fun availabilityForImported(format: ModelRuntimeFormat): ModelAvailability = when (format) {
    ModelRuntimeFormat.LITERT_LM, ModelRuntimeFormat.MEDIAPIPE_LLM -> ModelAvailability.CUSTOM_IMPORTED
    ModelRuntimeFormat.NEEDS_CONVERSION -> ModelAvailability.NEEDS_CONVERSION
    else -> ModelAvailability.UNSUPPORTED_ON_DEVICE
}

private fun importedNotes(format: ModelRuntimeFormat, externallyReferenced: Boolean): String = when (format) {
    ModelRuntimeFormat.LITERT_LM, ModelRuntimeFormat.MEDIAPIPE_LLM -> if (externallyReferenced) {
        "외부 파일로 연결되었습니다. 실행 전 내부 복사가 필요할 수 있습니다."
    } else {
        "가져온 모델입니다."
    }
    ModelRuntimeFormat.NEEDS_CONVERSION -> "이 파일은 변환이 필요합니다."
    else -> "이 파일은 현재 직접 실행할 수 없습니다."
}

fun FusionModelSpec.statusLabel(localAvailable: Boolean): String = when {
    localAvailable -> "사용 가능"
    availability == ModelAvailability.NEEDS_CONVERSION -> "변환 필요"
    availability == ModelAvailability.NEEDS_DOWNLOAD -> "다운로드 필요"
    availability == ModelAvailability.REMOTE_ONLY -> "원격 전용"
    availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "지원되지 않음"
    availability == ModelAvailability.CUSTOM_IMPORTED -> "사용 가능"
    else -> "사용 가능"
}

fun FusionModelSpec.deviceLabel(): String = when (recommendedDeviceClass) {
    ModelRecommendedDeviceClass.RAM_8GB_SAFE -> "8GB 권장"
    ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED -> "12GB 권장"
    ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED -> "16GB 권장"
    ModelRecommendedDeviceClass.SERVER_ONLY -> "서버 전용"
}

fun FusionModelSpec.recommendedMaxTokensFor8GbLabel(): String = if (recommendedMaxTokens8Gb > 0) "maxTokens ${recommendedMaxTokens8Gb}" else "서버 설정 필요"

fun FusionModelSpec.applyRecommendedSettings(editor: android.content.SharedPreferences.Editor) {
    if (recommendedMaxTokens8Gb > 0) editor.putInt("max_tokens", recommendedMaxTokens8Gb)
    editor.putBoolean("speculative_decoding_enabled", recommendedMtpEnabled)
    editor.putBoolean("reasoning_enabled", recommendedReasoningEnabled)
    if (runtimeFormat == ModelRuntimeFormat.EXYNOS_AI_STUDIO) editor.putString("accelerator", AcceleratorMode.AUTO.name)
}
