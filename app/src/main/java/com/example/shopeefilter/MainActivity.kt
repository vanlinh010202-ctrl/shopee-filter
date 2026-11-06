package com.example.shopeefilter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        scrollView.addView(layout)

        val prefs = getSharedPreferences("FilterConfig", Context.MODE_PRIVATE)

        val title = TextView(this).apply {
            text = "CẤU HÌNH LỌC ĐƠN ĐƯỜNG DÀI (1-2-3-4)"
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }

        val lblMinKm = TextView(this).apply { text = "1. Quãng đường TỐI THIỂU để nhận (km):" }
        val edtMinKm = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ví dụ: 10.0 (Đơn >= 10km mới nhận)"
            setText(prefs.getFloat("MIN_KM", 10.0f).toString())
        }

        val lblMinPrice = TextView(this).apply { text = "2. Tiền cước TỐI THIỂU (nghìn đồng - k):" }
        val edtMinPrice = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ví dụ: 45.0 (Điền 0 nếu không cần lọc giá)"
            setText(prefs.getFloat("MIN_PRICE", 0.0f).toString())
        }

        val lblAllowed = TextView(this).apply { text = "3. Quận/Khu vực MUỐN ĐI (cách nhau dấu phẩy, để trống = nhận hết):" }
        val edtAllowed = EditText(this).apply {
            hint = "Ví dụ: Quận 12, Bình Thạnh, Thủ Đức"
            setText(prefs.getString("ALLOWED_AREAS", ""))
        }

        val lblBlocked = TextView(this).apply { text = "4. Quận/Khu vực CHẶN/NÉ (cách nhau dấu phẩy):" }
        val edtBlocked = EditText(this).apply {
            hint = "Ví dụ: Bình Chánh, Nhà Bè"
            setText(prefs.getString("BLOCKED_AREAS", ""))
        }

        val lblLoopDelay = TextView(this).apply { text = "5. Tốc độ nhấp mở chấm đỏ (mili-giây, khuyên dùng 350 - 600ms):" }
        val edtLoopDelay = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getLong("LOOP_DELAY", 450L).toString())
        }

        val btnSave = Button(this).apply {
            text = "LƯU CẤU HÌNH LỌC"
            setOnClickListener {
                prefs.edit()
                    .putFloat("MIN_KM", edtMinKm.text.toString().toFloatOrNull() ?: 10.0f)
                    .putFloat("MIN_PRICE", edtMinPrice.text.toString().toFloatOrNull() ?: 0.0f)
                    .putString("ALLOWED_AREAS", edtAllowed.text.toString())
                    .putString("BLOCKED_AREAS", edtBlocked.text.toString())
                    .putLong("LOOP_DELAY", edtLoopDelay.text.toString().toLongOrNull() ?: 450L)
                    .apply()
                Toast.makeText(context, "Đã lưu cấu hình! Các thay đổi có hiệu lực ngay.", Toast.LENGTH_SHORT).show()
            }
        }

        val btnOverlayPerm = Button(this).apply {
            text = "BƯỚC 1: Cấp quyền Hiển thị trên ứng dụng khác (Nút nổi)"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "Đã cấp quyền Hiển thị nút nổi!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnAccPerm = Button(this).apply {
            text = "BƯỚC 2: Bật Dịch vụ Hỗ trợ tiếp cận (Hiện 4 nút 1-2-3-4)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val guideText = TextView(this).apply {
            text = "\nHƯỚNG DẪN SỬ DỤNG:\n" +
                    "• Nút 1: Kéo vào vùng trống bản đồ (góc trái) để làm mới/đóng bảng cũ.\n" +
                    "• Nút 2: Kéo đè lên nút 'Nhận đơn' màu cam ở dưới đáy.\n" +
                    "• Nút 3: Kéo vào vị trí chấm đỏ đơn hàng trên bản đồ (hoặc nút Tải lại trang).\n" +
                    "• Nút 4: Kéo vào vị trí nút 'Lấy đơn' trên bảng xác nhận giữa màn hình.\n" +
                    "• Khi đang DỪNG (màu đỏ), bạn kéo thả 4 nút 1-2-3-4 thoải mái rồi bấm 'LƯU VỊ TRÍ'.\n" +
                    "• Khi bấm CHẠY (PLAY màu xanh), 4 nút sẽ khóa lại và cho phép chạm xuyên qua."
            setPadding(0, 24, 0, 0)
        }

        layout.addView(title)
        layout.addView(lblMinKm)
        layout.addView(edtMinKm)
        layout.addView(lblMinPrice)
        layout.addView(edtMinPrice)
        layout.addView(lblAllowed)
        layout.addView(edtAllowed)
        layout.addView(lblBlocked)
        layout.addView(edtBlocked)
        layout.addView(lblLoopDelay)
        layout.addView(edtLoopDelay)
        layout.addView(btnSave)
        layout.addView(btnOverlayPerm)
        layout.addView(btnAccPerm)
        layout.addView(guideText)

        setContentView(scrollView)
    }
}
