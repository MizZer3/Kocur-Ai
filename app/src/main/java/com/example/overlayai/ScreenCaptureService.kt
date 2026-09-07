package com.example.overlayai

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONArray

class ScreenCaptureService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var btnCapture: Button

    private lateinit var prefs: SharedPreferences
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "isButtonHidden") {
            updateButtonVisibility()
        } else if (key == "buttonColor" || key == "buttonImageUri" || key == "buttonSize" || key == "buttonOpacity") {
            Handler(Looper.getMainLooper()).post {
                updateButtonStyle()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
        updateButtonVisibility()
    }

    private lateinit var btnAddScreenshot: Button
    private val capturedBitmaps = mutableListOf<Bitmap>()
    private var isCapturingAdditional = false

    private fun updateButtonVisibility() {
        if (::overlayView.isInitialized) {
            val isHidden = prefs.getBoolean("isButtonHidden", false)
            overlayView.visibility = if (isHidden) View.GONE else View.VISIBLE
        }
    }

    private fun setupOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_view, null)

        btnCapture = overlayView.findViewById(R.id.btnCapture)
        btnAddScreenshot = overlayView.findViewById(R.id.btnAddScreenshot)
        
        updateButtonStyle()

        val longPressHandler = Handler(Looper.getMainLooper())
        var isLongPress = false
        val longPressRunnable = Runnable {
            isLongPress = true
            val heightOffset = (48 * resources.displayMetrics.density).toInt()
            if (btnAddScreenshot.visibility == View.VISIBLE) {
                btnAddScreenshot.visibility = View.GONE
                params.y += heightOffset
            } else {
                btnAddScreenshot.visibility = View.VISIBLE
                params.y -= heightOffset
                val size = capturedBitmaps.size
                btnAddScreenshot.text = if (size == 0) "+" else size.toString()
            }
            windowManager.updateViewLayout(overlayView, params)
        }

        // Make it draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        btnCapture.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPress = false
                    longPressHandler.postDelayed(longPressRunnable, 500)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    val xDiff = Math.abs(event.rawX - initialTouchX)
                    val yDiff = Math.abs(event.rawY - initialTouchY)
                    if (xDiff < 10 && yDiff < 10 && !isLongPress) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val xDiff = Math.abs(event.rawX - initialTouchX)
                    val yDiff = Math.abs(event.rawY - initialTouchY)
                    if (xDiff >= 10 || yDiff >= 10) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        btnAddScreenshot.setOnClickListener {
            isCapturingAdditional = true
            startCaptureProcess()
        }

        btnCapture.setOnClickListener {
            if (btnCapture.text.isNotEmpty() && btnCapture.text != "...") {
                resetOverlay()
            } else {
                isCapturingAdditional = false
                startCaptureProcess()
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun updateButtonStyle() {
        if (!::btnCapture.isInitialized) return
        
        val buttonColorHex = prefs.getString("buttonColor", "#000000") ?: "#000000"
        val buttonImageUri = prefs.getString("buttonImageUri", "") ?: ""

        var isImageSet = false
        if (buttonImageUri.isNotEmpty()) {
            try {
                val uri = android.net.Uri.parse(buttonImageUri)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val roundedBitmap = getRoundedBitmap(bitmap)
                    btnCapture.background = android.graphics.drawable.BitmapDrawable(resources, roundedBitmap)
                    isImageSet = true
                }
            } catch (e: Exception) {
                // Ignore, fallback to color
            }
        }
        
        if (!isImageSet) {
            try {
                btnCapture.setBackgroundResource(R.drawable.bg_circle_button)
                btnCapture.background.setTint(Color.parseColor(buttonColorHex))
            } catch (e: Exception) {
                btnCapture.setBackgroundResource(R.drawable.bg_circle_button)
                btnCapture.background.setTint(Color.parseColor("#000000"))
            }
        }
        
        try {
            btnAddScreenshot.setBackgroundResource(R.drawable.bg_circle_button)
            btnAddScreenshot.background.setTint(Color.parseColor(buttonColorHex))
        } catch (e: Exception) {
            btnAddScreenshot.setBackgroundResource(R.drawable.bg_circle_button)
            btnAddScreenshot.background.setTint(Color.parseColor("#000000"))
        }
        
        val buttonSizeDp = prefs.getInt("buttonSize", 64)
        if (buttonSizeDp > 0) {
            val buttonSizePx = (buttonSizeDp * resources.displayMetrics.density).toInt()
            val lp = btnCapture.layoutParams
            lp.width = buttonSizePx
            lp.height = buttonSizePx
            btnCapture.layoutParams = lp
        }

        val buttonOpacity = prefs.getInt("buttonOpacity", 50)
        val alpha = (buttonOpacity * 255 / 100).coerceIn(0, 255)
        btnCapture.background.mutate().alpha = alpha
        
        btnCapture.requestLayout()
    }

    private fun getRoundedBitmap(bitmap: Bitmap): Bitmap {
        val minEdge = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(minEdge, minEdge, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.BLACK
        canvas.drawCircle(minEdge / 2f, minEdge / 2f, minEdge / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val xOffset = (bitmap.width - minEdge) / 2
        val yOffset = (bitmap.height - minEdge) / 2
        val srcRect = android.graphics.Rect(xOffset, yOffset, xOffset + minEdge, yOffset + minEdge)
        val destRect = android.graphics.Rect(0, 0, minEdge, minEdge)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
        return output
    }

    private fun startCaptureProcess() {
        val apiUrl = prefs.getString("apiUrl", "") ?: ""

        if (apiUrl.isEmpty()) {
            Toast.makeText(this, "Please configure AI settings first", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide overlay temporarily to not include it in the screenshot
        overlayView.visibility = View.INVISIBLE

        // Small delay to ensure the OS hides the view
        Handler(Looper.getMainLooper()).postDelayed({
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        processScreenshot(screenshotResult)
                    }

                    override fun onFailure(errorCode: Int) {
                        showError("Screenshot Failed: $errorCode")
                    }
                }
            )
        }, 300)
    }

    private fun processScreenshot(result: ScreenshotResult) {
        val hardwareBuffer = result.hardwareBuffer
        val colorSpace = result.colorSpace
        
        if (hardwareBuffer == null) {
            showError("No hardware buffer")
            return
        }

        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
        hardwareBuffer.close()

        if (bitmap == null) {
            showError("Failed to convert buffer to bitmap")
            return
        }

        val swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (swBitmap != null) {
            capturedBitmaps.add(swBitmap)
        }

        if (isCapturingAdditional) {
            val size = capturedBitmaps.size
            btnAddScreenshot.text = if (size == 0) "+" else size.toString()
            updateButtonVisibility()
            btnAddScreenshot.visibility = View.VISIBLE
            return
        }

        val bitmapsToSend = capturedBitmaps.toList()
        capturedBitmaps.clear()
        btnAddScreenshot.visibility = View.GONE

        showLoading()

        CoroutineScope(Dispatchers.IO).launch {
            val maxLength = prefs.getInt("maxOutputLength", 200)
            val responseText = executeSingleApi(bitmapsToSend, maxLength)

            withContext(Dispatchers.Main) {
                showResult(responseText)
            }
        }
    }
    
    data class LoadedProfile(
        val apiUrl: String,
        val apiKey: String,
        val apiFormat: Int,
        val thinkingLevel: Int
    )

    private fun getApiProfile(profileId: String): LoadedProfile? {
        val profilesJson = prefs.getString("apiProfiles", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(profilesJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("id") == profileId) {
                    return LoadedProfile(
                        obj.optString("apiUrl", ""),
                        obj.optString("apiKey", ""),
                        obj.optInt("apiFormat", 0),
                        obj.optInt("thinkingLevel", 0)
                    )
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getPromptText(profileId: String): String {
        val profilesJson = prefs.getString("promptProfiles", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(profilesJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("id") == profileId) {
                    return obj.optString("prompt", "")
                }
            }
        } catch (e: Exception) {}
        return ""
    }

    private fun executeSingleApi(bitmaps: List<Bitmap>, maxLength: Int): String {
        val apiUrl = prefs.getString("apiUrl", "") ?: ""
        val apiKey = prefs.getString("apiKey", "") ?: ""
        val prompt = prefs.getString("prompt", "") ?: ""
        val apiFormat = prefs.getInt("apiFormat", 0)
        val thinkingLevel = prefs.getInt("thinkingLevel", 0)
        
        val response = try {
            AiNetworkClient.sendRequest(this, apiUrl, apiKey, prompt, apiFormat, thinkingLevel, bitmaps)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
        
        return if (response.length > maxLength) "" else response
    }

    private fun showLoading() {
        if (prefs.getBoolean("isButtonHidden", false)) return
        overlayView.visibility = View.VISIBLE
        btnCapture.text = "..."
        // Keep it as a constrained square while loading
        val buttonSizeDp = prefs.getInt("buttonSize", 64)
        if (buttonSizeDp > 0) {
            val buttonSizePx = (buttonSizeDp * resources.displayMetrics.density).toInt()
            val lp = btnCapture.layoutParams
            lp.width = buttonSizePx
            lp.height = buttonSizePx
            btnCapture.layoutParams = lp
        }
    }

    private fun showResult(text: String) {
        if (prefs.getBoolean("isButtonHidden", false)) return
        overlayView.visibility = View.VISIBLE
        if (text.isEmpty()) {
            btnCapture.text = ""
            resetOverlay()
        } else {
            btnCapture.text = text
            // Expand size to wrap text instead of rigid square
            val lp = btnCapture.layoutParams
            lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            btnCapture.layoutParams = lp
        }
    }

    private fun showError(error: String) {
        Handler(Looper.getMainLooper()).post {
            if (prefs.getBoolean("isButtonHidden", false)) return@post
            overlayView.visibility = View.VISIBLE
            btnCapture.text = error
        }
    }

    private fun resetOverlay() {
        btnCapture.text = ""
        // Restore manual square size constraints
        val buttonSizeDp = prefs.getInt("buttonSize", 64)
        if (buttonSizeDp > 0) {
            val buttonSizePx = (buttonSizeDp * resources.displayMetrics.density).toInt()
            val lp = btnCapture.layoutParams
            lp.width = buttonSizePx
            lp.height = buttonSizePx
            btnCapture.layoutParams = lp
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        }
        if (::windowManager.isInitialized && ::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {}
        }
    }
}
