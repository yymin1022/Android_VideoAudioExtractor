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
            audioExtractor.extractAudio(activity!!.applicationContext, videoFd)
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