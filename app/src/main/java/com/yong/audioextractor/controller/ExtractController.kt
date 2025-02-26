package com.yong.audioextractor.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.yong.audioextractor.R
import com.yong.audioextractor.model.extractor.AudioExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ExtractController
 * - Audio 추출 기능을 구현한 Controller
 * - Coroutine을 통해  Audio Extractor를 호출
 * - 작업이 완료되면 이전 화면으로 돌아감
 */
class ExtractController: Controller() {
    // Audio Extractor Model 선언
    private val audioExtractor = AudioExtractor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_extract, container, false)

        val videoFd = resources?.openRawResourceFd(R.raw.sample_video) ?: throw Exception("No video available")

        CoroutineScope(Dispatchers.Main).launch {
            // Extractor 호출
            audioExtractor.extractAudio(activity!!.applicationContext, videoFd)
            // Extractor 종료 시 이전 화면으로 Pop
            router.popCurrentController()
        }

        return view
    }

    // 뒤로가기 동작 정의
    override fun handleBack(): Boolean {
        // Router로부터 현재 Controller Pop
        router.popCurrentController()
        return true
    }
}