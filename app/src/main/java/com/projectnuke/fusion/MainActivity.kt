package com.projectnuke.fusion

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.projectnuke.fusion.llm.FusionRuntimeLock
import com.projectnuke.fusion.ui.FusionApp
import com.projectnuke.fusion.util.FusionMemoryManager
import com.projectnuke.fusion.util.FusionThumbnailCache

class MainActivity : ComponentActivity() {
    private var unregisterCacheClearer: (() -> Unit)? = null
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() {
            handleMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        }

        override fun onTrimMemory(level: Int) {
            handleMemoryTrim(level)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unregisterCacheClearer = FusionMemoryManager.registerLightweightCacheClearer { FusionThumbnailCache.clear() }
        registerComponentCallbacks(memoryCallbacks)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            FusionApp()
        }
    }

    override fun onDestroy() {
        unregisterComponentCallbacks(memoryCallbacks)
        unregisterCacheClearer?.invoke()
        unregisterCacheClearer = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        if (!FusionRuntimeLock.isChatGenerationRunning && !FusionRuntimeLock.isBenchmarkRunning) {
            Log.i("FusionMemory", "App moved to background; clearing caches and unloading idle engines")
            FusionMemoryManager.clearLightweightCaches()
            FusionMemoryManager.unloadIdleEngines()
        }
    }

    private fun handleMemoryTrim(level: Int) {
        Log.i("FusionMemory", "onTrimMemory level=$level")
        FusionMemoryManager.logMemorySnapshot("FusionMemory", this, "trim-level-$level")
        FusionMemoryManager.clearLightweightCaches()

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL &&
            !FusionRuntimeLock.isChatGenerationRunning &&
            !FusionRuntimeLock.isBenchmarkRunning
        ) {
            Log.i("FusionMemory", "모델 리소스를 정리하는 중입니다.")
            FusionMemoryManager.unloadIdleEngines()
        }
    }
}
