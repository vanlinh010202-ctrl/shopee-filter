package com.example.shopeefilter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.regex.Pattern

class OrderFilterService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var controlBar: LinearLayout? = null
    private val targetViews = arrayOfNulls<TextView>(4)
    private val targetParams = arrayOfNulls<WindowManager.LayoutParams>(4)

    private var isRunning = false
    private var isAcceptingOrder = false
    private val handler = Handler(Looper.getMainLooper())
    private var stepToggle = false

    // Vòng lặp tự động bấm nút 1 và nút 3 khi đang chờ mở đơn trên bản đồ
    private val huntRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val prefs = getSharedPreferences("FilterConfig", Context.MODE_PRIVATE)
            val delay = prefs.getLong("LOOP_DELAY", 450L).coerceAtLeast(200L)

            val root = rootInActiveWindow
            if (root != null) {
                // Nếu đang hiện bảng xác nhận "Lấy đơn", ưu tiên bắn vào Nút số 4 ngay
                val confirmNodes = root.findAccessibilityNodeInfosByText("Lấy đơn")
                if (confirmNodes.any { it.text?.toString() == "Lấy đơn" }) {
                    tapTarget(3) // Nút số 4 (index 3)
                    handler.postDelayed(this, delay)
                    return
                }

                // Nếu thẻ đơn hàng đang mở (có nút "Nhận đơn"), để hàm phân tích xử lý
                val acceptNodes = root.findAccessibilityNodeInfosByText("Nhận đơn")
                if (acceptNodes.isNotEmpty()) {
                    evaluateAndProcessOrder(root)
                    handler.postDelayed(this, delay)
                    return
                }
            }

            // Nếu chưa mở thẻ đơn: Nhấp luân phiên Nút 1 (làm mới) và Nút 3 (mở chấm đỏ / tải lại trang)
            if (stepToggle) {
                tapTarget(0) // Nút 1
            } else {
                tapTarget(2) // Nút 3
            }
            stepToggle = !stepToggle
            handler.postDelayed(this, delay)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingTargets()
        createFloatingControlBar()
    }

    private fun createFloatingTargets() {
        val prefs = getSharedPreferences("FilterConfig", Context.MODE_PRIVATE)
        val defaultX = intArrayOf(80, 500, 620, 620)
        val defaultY = intArrayOf(650, 2100, 1150, 1220)

        for (i in 0..3) {
            val tv = TextView(this).apply {
                text = "${i + 1}"
                textSize = 18f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(180, 46, 204, 113)) // Xanh lá bán trong suốt
                    setStroke(4, Color.WHITE)
                }
            }

            val savedX = prefs.getInt("TARGET_X_$i", defaultX[i])
            val savedY = prefs.getInt("TARGET_Y_$i", defaultY[i])

            val params = WindowManager.LayoutParams(
                110, 110,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }

            // Cho phép kéo thả khi đang ở chế độ Dừng (Stop)
            tv.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    if (isRunning) return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(tv, params)
                            return true
                        }
                    }
                    return false
                }
            })

            targetViews[i] = tv
            targetParams[i] = params
            windowManager.addView(tv, params)
        }
    }

    private fun createFloatingControlBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 16, 12, 16)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.argb(220, 30, 30, 30))
            }
        }

        val btnPlayPause = Button(this).apply {
            text = "▶ CHẠY"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(39, 174, 96))
            setOnClickListener {
                isRunning = !isRunning
                if (isRunning) {
                    text = "■ DỪNG"
                    setBackgroundColor(Color.rgb(192, 57, 43))
                    setTargetsTouchPassThrough(true)
                    handler.post(huntRunnable)
                } else {
                    text = "▶ CHẠY"
                    setBackgroundColor(Color.rgb(39, 174, 96))
                    setTargetsTouchPassThrough(false)
                    handler.removeCallbacks(huntRunnable)
                }
            }
        }

        val btnSavePos = Button(this).apply {
            text = "LƯU VỊ TRÍ"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setOnClickListener {
                val prefs = getSharedPreferences("FilterConfig", Context.MODE_PRIVATE).edit()
                for (i in 0..3) {
                    targetParams[i]?.let { p ->
                        prefs.putInt("TARGET_X_$i", p.x)
                        prefs.putInt("TARGET_Y_$i", p.y)
                    }
                }
                prefs.apply()
                Toast.makeText(context, "Đã lưu tọa độ 4 nút 1-2-3-4!", Toast.LENGTH_SHORT).show()
            }
        }

        bar.addView(btnPlayPause)
        bar.addView(btnSavePos)

        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 10
            y = 0
        }

        controlBar = bar
        windowManager.addView(bar, barParams)
    }

    // Khi đang CHẠY, bật cờ FLAG_NOT_TOUCHABLE để ngón tay và lệnh Auto Click chạm xuyên qua 4 nút tròn
    private fun setTargetsTouchPassThrough(passThrough: Boolean) {
        for (i in 0..3) {
            val tv = targetViews[i] ?: continue
            val params = targetParams[i] ?: continue
            params.flags = if (passThrough) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager.updateViewLayout(tv, params)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning) return
        val rootNode = rootInActiveWindow ?: return

        // 1. Nếu bảng xác nhận "Lấy đơn free-pick" bật lên sau khi bấm Nút 2 -> Bấm ngay Nút 4
        val confirmNodes = rootNode.findAccessibilityNodeInfosByText("Lấy đơn")
        if (confirmNodes.any { it.text?.toString() == "Lấy đơn" }) {
            tapTarget(3) // Nút số 4
            return
        }

        // 2. Nếu có thẻ đơn hàng ("Nhận đơn") xuất hiện -> Đọc và lọc ngay lập tức
        val acceptNodes = rootNode.findAccessibilityNodeInfosByText("Nhận đơn")
        if (acceptNodes.isNotEmpty()) {
            evaluateAndProcessOrder(rootNode)
        }
    }

    private fun evaluateAndProcessOrder(rootNode: AccessibilityNodeInfo) {
        if (isAcceptingOrder) return

        val allTexts = mutableListOf<String>()
        extractAllText(rootNode, allTexts)
        val fullText = allTexts.joinToString(" | ")

        val prefs = getSharedPreferences("FilterConfig", Context.MODE_PRIVATE)
        val minKm = prefs.getFloat("MIN_KM", 10.0f)
        val minPrice = prefs.getFloat("MIN_PRICE", 0.0f)
        val allowedAreas = prefs.getString("ALLOWED_AREAS", "") ?: ""
        val blockedAreas = prefs.getString("BLOCKED_AREAS", "") ?: ""

        val distanceKm = parseRegexFloat(fullText, "(\d+(\.\d+)?)\s*km")
        val priceK = parseRegexFloat(fullText, "(\d+(\.\d+)?)k")

        if (distanceKm != null) {
            // ĐIỀU KIỆN: Quãng đường >= Số KM tối thiểu
            val isKmOk = distanceKm >= minKm
            val isPriceOk = (priceK == null) || (priceK >= minPrice)
            val isAreaOk = checkAddressCondition(allTexts, allowedAreas, blockedAreas)

            if (isKmOk && isPriceOk && isAreaOk) {
                // ĐƠN ĐẠT CHUẨN (>= Min KM) -> Bấm Nút số 2 ("Nhận đơn") rồi chờ 120ms bấm tiếp Nút số 4 ("Lấy đơn")
                isAcceptingOrder = true
                tapTarget(1) // Nút số 2
                handler.postDelayed({
                    tapTarget(3) // Nút số 4
                    isAcceptingOrder = false
                }, 140)
            } else {
                // ĐƠN KHÔNG ĐẠT (Ngắn hơn Min KM hoặc sai khu vực) -> Bấm Nút số 1 để đóng/bỏ qua
                tapTarget(0) // Nút số 1
            }
        }
    }

    // Chạm đúng vào tâm của Nút tròn (1, 2, 3 hoặc 4) mà người dùng đã kéo đặt trên màn hình
    private fun tapTarget(index: Int) {
        val tv = targetViews[index] ?: return
        val loc = IntArray(2)
        tv.getLocationOnScreen(loc)
        val centerX = (loc[0] + tv.width / 2f).coerceAtLeast(1f)
        val centerY = (loc[1] + tv.height / 2f).coerceAtLeast(1f)

        val path = Path().apply { moveTo(centerX, centerY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 35))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun extractAllText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.let { if (it.isNotBlank()) list.add(it.toString()) }
        for (i in 0 until node.childCount) {
            extractAllText(node.getChild(i), list)
        }
    }

    private fun parseRegexFloat(text: String, regex: String): Float? {
        val matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text)
        return if (matcher.find()) matcher.group(1)?.toFloatOrNull() else null
    }

    private fun checkAddressCondition(
        texts: List<String>,
        allowedStr: String,
        blockedStr: String
    ): Boolean {
        val giaoIndex = texts.indexOfFirst { it.contains("Giao:") }
        if (giaoIndex == -1 || giaoIndex + 1 >= texts.size) return true
        val deliveryAddress = texts[giaoIndex + 1].lowercase()

        if (blockedStr.isNotBlank()) {
            val blockedList = blockedStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (blockedList.any { deliveryAddress.contains(it) }) return false
        }

        if (allowedStr.isNotBlank()) {
            val allowedList = allowedStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            return allowedList.any { deliveryAddress.contains(it) }
        }
        return true
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(huntRunnable)
        controlBar?.let { windowManager.removeView(it) }
        for (tv in targetViews) {
            tv?.let { windowManager.removeView(it) }
        }
    }
}
