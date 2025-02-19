package com.yong.audioextractor.activity

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.yong.audioextractor.R
import com.yong.audioextractor.controller.PlayController

/**
 * MainActivity
 * - 기본 Router 선언 및 초기화
 * - Router에 기본 Controller 지정
 */
class MainActivity: AppCompatActivity() {
    // 기본 Router 정의
    private lateinit var router: Router

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 뒤로가기 동작에 obBackPressed Callback 지정
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Container ViewGroup 초기화
        val controllerContainer = findViewById<ViewGroup>(R.id.main_controller_container)
        // Router 초기화
        router = Conductor.attachRouter(this, controllerContainer, savedInstanceState)

        // 기본 Controller 지정
        if(!router.hasRootController()) {
            router.setRoot(RouterTransaction.with(PlayController()))
        }
    }

    // onBackPressed Callback 정의
    private val onBackPressedCallback: OnBackPressedCallback = object: OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Router가 뒤로가기 동작을 처리하지 않은 경우
            // 즉, PlauController를 보이고 있는 경우
            if(!router.handleBack()) {
                // onBackPressed 동작 비활성화 및 Activity 기본 뒤로가기 동작 호출
                this.isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

    }
}