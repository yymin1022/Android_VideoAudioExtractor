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

    override fun handleBack(): Boolean {
        router.popCurrentController()
        return true
    }
}