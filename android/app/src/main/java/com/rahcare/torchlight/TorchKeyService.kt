package com.rahcare.torchlight

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * ভলিউম-ডাউন বাটন চেপে ধরলে (প্রায় ০.৬ সেকেন্ড) টর্চ অন/অফ করে — অ্যাপ বন্ধ বা স্ক্রিন অফ থাকলেও।
 * ছোট চাপে স্বাভাবিকভাবে ভলিউম কমে।
 */
class TorchKeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var torchOn = false

    private var pending = false
    private var longPressFired = false

    private val longPress = Runnable {
        longPressFired = true
        toggleTorch()
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) torchOn = enabled
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = try {
            findFlashCamera()
        } catch (e: Exception) {
            null
        }
        if (cameraId != null) cameraManager.registerTorchCallback(torchCallback, handler)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        handler.removeCallbacks(longPress)
        if (cameraId != null) cameraManager.unregisterTorchCallback(torchCallback)
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // বাটন ধরে রাখলে একই ডাউন ইভেন্ট বারবার আসে — শুধু প্রথমটা গণনা করি
                if (!pending) {
                    pending = true
                    longPressFired = false
                    handler.postDelayed(longPress, LONG_PRESS_MS)
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                val wasLong = longPressFired
                handler.removeCallbacks(longPress)
                pending = false
                longPressFired = false
                if (!wasLong) lowerVolume()
                return true
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        handler.removeCallbacks(longPress)
        pending = false
    }

    private fun lowerVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustSuggestedStreamVolume(
            AudioManager.ADJUST_LOWER,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toggleTorch() {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, !torchOn)
        } catch (e: Exception) {
            // ক্যামেরা অন্য অ্যাপ ব্যবহার করছে — কিছু করার নেই
        }
    }

    private fun findFlashCamera(): String? {
        var fallback: String? = null
        for (id in cameraManager.cameraIdList) {
            val ch = cameraManager.getCameraCharacteristics(id)
            if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
            if (ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return id
            if (fallback == null) fallback = id
        }
        return fallback
    }

    companion object {
        private const val LONG_PRESS_MS = 600L
    }
}
