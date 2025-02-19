package com.yong.audioextractor.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.yong.audioextractor.R

class ExtractController: Controller() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_extract, container, false)
        return view
    }

    // 뒤로가기 동작 정의
    override fun handleBack(): Boolean {
        // Router로부터 현재 Controller Pop
        router.popCurrentController()
        return true
    }
}