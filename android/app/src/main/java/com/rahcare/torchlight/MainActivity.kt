package com.rahcare.torchlight

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** টর্চলাইট: বড় অন/অফ বাটন + স্থির / SOS / ঝলক (স্ট্রোব) মোড। ক্যামেরা পারমিশন লাগে না। */
class MainActivity : Activity() {

    private enum class Mode(val label: String) {
        STEADY("স্থির"), SOS("SOS"), STROBE("ঝলক")
    }

    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    private var mode = Mode.STEADY
    private var step = 0

    private lateinit var power: TextView
    private lateinit var status: TextView
    private val chips = LinkedHashMap<Mode, TextView>()

    // SOS = তিন ছোট, তিন লম্বা, তিন ছোট ঝলক
    private val sosSteps: List<Pair<Boolean, Long>> by lazy {
        val s = ArrayList<Pair<Boolean, Long>>()
        fun letter(unit: Long) {
            for (i in 0 until 3) {
                s.add(Pair(true, unit))
                s.add(Pair(false, if (i < 2) 250L else 750L))
            }
        }
        letter(250L)
        letter(750L)
        letter(250L)
        s.add(Pair(false, 1500L))
        s
    }

    private val runner = object : Runnable {
        override fun run() {
            if (!active) return
            when (mode) {
                Mode.STEADY -> setTorch(true)
                Mode.STROBE -> {
                    setTorch(step % 2 == 0)
                    step++
                    handler.postDelayed(this, 90L)
                }
                Mode.SOS -> {
                    val cur = sosSteps[step % sosSteps.size]
                    setTorch(cur.first)
                    step++
                    handler.postDelayed(this, cur.second)
                }
            }
        }
    }

    // অন্য অ্যাপ/সিস্টেম টর্চ বন্ধ করলে বাটনও বন্ধ দেখাবে (শুধু স্থির মোডে)
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId && mode == Mode.STEADY && active && !enabled) {
                active = false
                refreshUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = try {
            findFlashCamera()
        } catch (e: Exception) {
            null
        }
        buildUi()
        if (cameraId != null) {
            cameraManager.registerTorchCallback(torchCallback, handler)
        } else {
            power.isEnabled = false
        }
        refreshUi()
    }

    override fun onDestroy() {
        handler.removeCallbacks(runner)
        if (cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
            } catch (e: Exception) {
                // ignore
            }
            cameraManager.unregisterTorchCallback(torchCallback)
        }
        super.onDestroy()
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

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, on)
        } catch (e: CameraAccessException) {
            fail()
        } catch (e: IllegalArgumentException) {
            fail()
        }
    }

    private fun fail() {
        active = false
        handler.removeCallbacks(runner)
        refreshUi()
        Toast.makeText(this, "টর্চ চালু করা যায়নি — অন্য কোনো অ্যাপ ক্যামেরা ব্যবহার করছে কি?", Toast.LENGTH_LONG).show()
    }

    private fun applyState() {
        handler.removeCallbacks(runner)
        step = 0
        if (active) handler.post(runner) else setTorch(false)
        refreshUi()
    }

    private fun toggle() {
        active = !active
        applyState()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundRect(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(color)
        return d
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.parseColor("#0B0F14"))
        root.setPadding(dp(24), dp(48), dp(24), dp(32))

        val title = TextView(this)
        title.text = "টর্চলাইট"
        title.setTextColor(Color.WHITE)
        title.textSize = 26f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        root.addView(title, LinearLayout.LayoutParams(matchParent, wrapContent))

        root.addView(View(this), LinearLayout.LayoutParams(matchParent, 0, 1f))

        power = TextView(this)
        power.textSize = 24f
        power.typeface = Typeface.DEFAULT_BOLD
        power.gravity = Gravity.CENTER
        power.isClickable = true
        power.setOnClickListener { toggle() }
        val pl = LinearLayout.LayoutParams(dp(210), dp(210))
        pl.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(power, pl)

        status = TextView(this)
        status.setTextColor(Color.parseColor("#8A97A8"))
        status.textSize = 15f
        status.gravity = Gravity.CENTER
        val sl = LinearLayout.LayoutParams(matchParent, wrapContent)
        sl.topMargin = dp(24)
        root.addView(status, sl)

        root.addView(View(this), LinearLayout.LayoutParams(matchParent, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (m in Mode.values()) {
            val chip = TextView(this)
            chip.text = m.label
            chip.textSize = 16f
            chip.typeface = Typeface.DEFAULT_BOLD
            chip.gravity = Gravity.CENTER
            chip.setPadding(0, dp(14), 0, dp(14))
            chip.isClickable = true
            chip.setOnClickListener {
                mode = m
                applyState()
            }
            val cl = LinearLayout.LayoutParams(0, wrapContent, 1f)
            cl.leftMargin = dp(5)
            cl.rightMargin = dp(5)
            row.addView(chip, cl)
            chips[m] = chip
        }
        root.addView(row, LinearLayout.LayoutParams(matchParent, wrapContent))

        setContentView(root)
    }

    private fun refreshUi() {
        val amber = Color.parseColor("#FFB703")
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(if (active) amber else Color.parseColor("#1B2430"))
        bg.setStroke(dp(6), if (active) Color.parseColor("#FFD866") else Color.parseColor("#2A3441"))
        power.background = bg
        power.text = if (active) "চালু" else "বন্ধ"
        power.setTextColor(if (active) Color.parseColor("#1A1200") else Color.parseColor("#8A97A8"))

        status.text = when {
            cameraId == null -> "এই ডিভাইসে ফ্ল্যাশ পাওয়া যায়নি"
            active -> "টর্চ জ্বলছে — " + mode.label
            else -> "চালু করতে বাটনে চাপ দিন"
        }

        for ((m, chip) in chips) {
            val sel = m == mode
            chip.background = roundRect(if (sel) amber else Color.parseColor("#1B2430"), 14)
            chip.setTextColor(if (sel) Color.parseColor("#1A1200") else Color.parseColor("#C5CEDA"))
        }
    }
}
