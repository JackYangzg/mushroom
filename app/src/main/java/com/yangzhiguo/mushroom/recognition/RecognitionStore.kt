package com.yangzhiguo.mushroom.recognition

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 识别流程状态机（设计文档 §2.2 / §4）。
 *
 * 状态流转：
 *  Idle → Uploading → Recognizing → Recognized → (LocalHit | LocalMiss)
 *       ↘ Error | Canceled
 *
 *  - 输入：用户拍的图片（base64 data URL）
 *  - 调用：MiniMaxApiClient（流式） + MushroomRepository（本地/远端查表）
 *  - 暴露：StateFlow<RecognitionState>（UI 订阅）
 *  - 副作用：折叠/展开思考区、取消、自动重试 1 次
 *
 * 自动重试策略（设计文档 §5）：
 *  - 一次失败后立即重试 1 次
 *  - 第二次仍失败 → state = Error(message, retryable=true)
 *  - 上限：1 次
 */
@HiltViewModel
class RecognitionStore @Inject constructor(
    private val api: MiniMaxApiClient,
    private val repository: MushroomRepository,
    private val historyRepository: RecognitionHistoryRepository,
    private val speciesDao: SpeciesDao,
) : ViewModel() {

    private val tag = "RecognitionStore"

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    /** 当前识别任务的 Job（用于 cancel）。 */
    private var currentJob: Job? = null

    /** 当前已累积的 thinking（stream 关闭后也保留）。 */
    private var thinkingAccum: String = ""

    /** 已 retry 一次（防止无限循环）。 */
    private var retried = false

    // ---- 对外操作 ----

    /**
     * 入口：开始一次识别。
     * @param imageDataUrl "data:image/jpeg;base64,..." 形式
     * @param photoUri 可选，本地文件 URI（仅用于回显）
     */
    fun startRecognition(imageDataUrl: String, photoUri: String? = null) {
        startRecognition(listOf(imageDataUrl), listOfNotNull(photoUri))
    }

    /**
     * 识别页首次进入时使用。导航到物种详情再返回会重新执行 Compose effect，
     * 但已有结果不应被重新读取图片或覆盖。
     */
    fun startRecognitionIfIdle(
        imageDataUrls: List<String>,
        photoUris: List<String>,
        userInfo: String = "",
    ) {
        if (_state.value != RecognitionState.Idle) return
        startRecognition(imageDataUrls, photoUris, userInfo)
    }

    fun startRecognition(
        imageDataUrls: List<String>,
        photoUris: List<String>,
        userInfo: String = "",
    ) {
        if (imageDataUrls.isEmpty()) {
            _state.value = RecognitionState.Error(
                message = "无法读取所选图片，请重新选择。",
                retryable = false,
            )
            return
        }
        // 防止重复启动
        if (_state.value is RecognitionState.Recognizing ||
            _state.value is RecognitionState.Uploading
        ) {
            Log.w(tag, "已有识别任务进行中，忽略新 startRecognition")
            return
        }
        retried = false
        thinkingAccum = ""
        _selectedImageUrl.value = null
        _selectedMushroom.value = null
        _fallbackName.value = ""
        _candidateSpeciesIds.value = emptyMap()
        runRecognition(imageDataUrls, photoUris, userInfo)
    }

    /** 取消当前识别任务，回到 Canceled。 */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = RecognitionState.Canceled
    }

    /** 重置到 Idle（用于"重新拍照"）。 */
    fun reset() {
        currentJob?.cancel()
        currentJob = null
        thinkingAccum = ""
        retried = false
        _selectedImageUrl.value = null
        _selectedMushroom.value = null
        _fallbackName.value = ""
        _candidateSpeciesIds.value = emptyMap()
        _state.value = RecognitionState.Idle
    }

    /**
     * 在 Recognized 状态下，用户选了某个候选 → 查本地或走远端。
     * 这里默认用 [RecognitionResult.candidates] 的第一个命中（取 Top 1 的逻辑
     * 已经在 MushroomRepository.lookup 中实现）。
     */
    fun selectTopCandidate() {
        val s = _state.value
        if (s !is RecognitionState.Recognized) return
        resolveLookup(s.result)
    }

    // ---- 内部 ----

    private fun runRecognition(
        imageDataUrls: List<String>,
        photoUris: List<String>,
        userInfo: String,
    ) {
        // 启动期 guard
        try {
            ApiKeyGuard.require()
        } catch (e: MissingApiKeyException) {
            _state.value = RecognitionState.Error(e.message ?: "API key 未配置", retryable = false)
            return
        }

        currentJob = viewModelScope.launch {
            _state.value = RecognitionState.Uploading

            val stream = api.streamRecognize(
                imageDataUrls = imageDataUrls,
                userPrompt = buildRecognitionPrompt(userInfo),
            )
            val collected = mutableListOf<Candidate>()

            try {
                stream.collect { event ->
                    when (event) {
                        is StreamEvent.ThinkingChunk -> {
                            thinkingAccum += event.delta
                            _state.value = RecognitionState.Recognizing(
                                thinkingSoFar = thinkingAccum,
                                isThinkingExpanded = true,
                            )
                        }
                        is StreamEvent.FinalCandidates -> {
                            collected.clear()
                            collected.addAll(event.candidates)
                        }
                        is StreamEvent.StreamError -> {
                            throw event.cause
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(tag, "识别失败: ${t.message}")
                if (!retried) {
                    retried = true
                    Log.w(tag, "触发自动重试 1 次")
                    runRecognition(imageDataUrls, photoUris, userInfo)
                    return@launch
                }
                _state.value = RecognitionState.Error(
                    message = t.message ?: "识别失败",
                    retryable = true,
                )
                return@launch
            }

            // 流式结束：把累积 thinking + 候选封装为 Recognized
            val result = RecognitionResult(
                thinking = thinkingAccum,
                candidates = collected.toList(),
            )
            if (result.candidates.isEmpty()) {
                _state.value = RecognitionState.Error(
                    message = "暂未识别出蘑菇。建议人工比对图鉴后重试。",
                    retryable = true,
                )
                return@launch
            }
            _state.value = RecognitionState.Recognized(result = result, photoUris = photoUris)
            _candidateSpeciesIds.value = result.candidates.mapNotNull { candidate ->
                speciesDao.findBestNameMatch(
                    scientificName = candidate.scientificName.trim(),
                    commonName = candidate.commonName.orEmpty().trim(),
                )?.let {
                    candidate.scientificName to it.mushroomId
                }
            }.toMap()
            runCatching {
                historyRepository.save(result = result, photoUris = photoUris)
            }.onFailure {
                Log.w(tag, "保存识别历史失败: ${it.message}")
            }

            // 自动触发本地检索（用户在识别页等结果时无需点选）
            resolveLookup(result)
        }
    }

    /**
     * 本地优先 → 远端兜底。
     * 命中：state = LocalHit，并把 mushroom 写入 selectedMushroom
     * 未命中：state = LocalMiss，附带 fallbackName
     */
    private fun resolveLookup(result: RecognitionResult) {
        when (val lookup = repository.lookup(result.candidates)) {
            is MushroomRepository.LookupResult.Hit -> {
                _state.update { current ->
                    if (current is RecognitionState.Recognized) {
                        RecognitionState.LocalHit(result)
                    } else current
                }
                _selectedMushroom.value = lookup.mushroom
                // 命中后异步从 Room 拉首张图（设计文档 §3.4 渲染字段）
                viewModelScope.launch {
                    val img = runCatching {
                        repository.findFirstImage(lookup.mushroom.scientificName)
                    }.getOrNull()
                    if (img != null) _selectedImageUrl.value = img
                }
            }
            is MushroomRepository.LookupResult.Miss -> {
                _state.value = RecognitionState.LocalMiss(result)
                _fallbackName.value = lookup.fallbackName
            }
        }
    }

    /** 选中蘑菇的首张图 URL（从 Room 数据库异步拉取）。 */
    private val _selectedImageUrl = MutableStateFlow<String?>(null)
    val selectedImageUrl: StateFlow<String?> = _selectedImageUrl.asStateFlow()

    /**
     * 选中蘑菇的 side-channel（state 是状态机，selectedMushroom 是数据）。
     * UI 订阅两者组合渲染。
     */
    private val _selectedMushroom = MutableStateFlow<LocalMushroom?>(null)
    val selectedMushroom: StateFlow<LocalMushroom?> = _selectedMushroom.asStateFlow()

    private val _fallbackName = MutableStateFlow<String>("")
    val fallbackName: StateFlow<String> = _fallbackName.asStateFlow()

    private val _candidateSpeciesIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    val candidateSpeciesIds: StateFlow<Map<String, Int>> = _candidateSpeciesIds.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
