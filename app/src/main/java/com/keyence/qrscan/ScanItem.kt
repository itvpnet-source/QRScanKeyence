package com.keyence.qrscan

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model 1 lần quét QR – lưu nguyên chuỗi thô, đúng như file mẫu cột A
 */
@Entity(tableName = "scan_items")
data class ScanItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,                              // ID phiên (timestamp mở app)
    val rawQr: String,                                // Chuỗi QR thô – ghi thẳng ra cột A
    val scannedAt: Long = System.currentTimeMillis()  // Ngày giờ quét (tự động)
) {
    fun getFormattedDateTime(): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(scannedAt))
}
