package com.keyence.qrscan

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    data class ExportResult(
        val success: Boolean,
        val filePath: String = "",
        val error: String = ""
    )

    /**
     * Xuất file .xlsx đúng cấu trúc file mẫu:
     *  - Cột A: chuỗi QR thô, 1 dòng = 1 lần quét
     *  - Conditional formatting giống file gốc:
     *      + Tô màu xanh nếu dòng hợp lệ (không rỗng, không bắt đầu &/)
     *      + Tô màu đỏ nếu chuỗi bị trùng lặp
     */
    fun export(context: Context, items: List<ScanItem>, sessionId: Long): ExportResult {
        if (items.isEmpty()) return ExportResult(false, error = "Danh sách rỗng")

        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Sheet1")

            // ── Độ rộng cột A = 36 (giống file mẫu) ────────────────────────
            sheet.setColumnWidth(0, 36 * 256)

            // ── Style cho dữ liệu ─────────────────────────────────────────
            val dataStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply {
                    fontName = "Calibri"
                    fontHeightInPoints = 11
                }
                setFont(font)
            }

            // ── Ghi dữ liệu: mỗi dòng = 1 chuỗi QR thô vào cột A ─────────
            items.forEachIndexed { index, item ->
                val row = sheet.createRow(index)
                row.createCell(0).apply {
                    setCellValue(item.rawQr)
                    cellStyle = dataStyle
                }
            }

            // ── Conditional Formatting – giống hệt file mẫu ───────────────
            val cfRules = sheet.sheetConditionalFormatting
            val fullRange = arrayOf(CellRangeAddress.valueOf("A1:A1048576"))

            // Rule 2 (priority thấp): tô xanh nếu hợp lệ (không rỗng, không bắt đầu &/)
            val ruleValid = cfRules.createConditionalFormattingRule(
                ComparisonOperator.NO_COMPARISON,
                "AND(A1<>\"\",LEFT(A1,2)<>\"&/\")"
            )
            val validFill = ruleValid.createPatternFormatting()
            validFill.fillBackgroundColor = IndexedColors.LIGHT_GREEN.index
            validFill.fillPattern = PatternFormatting.SOLID_FOREGROUND

            // Rule 1 (priority cao): tô đỏ nếu trùng lặp
            val ruleDup = cfRules.createConditionalFormattingRule(
                ComparisonOperator.NO_COMPARISON,
                "COUNTIF(\$A\$1:A1,A1)>1"
            )
            val dupFill = ruleDup.createPatternFormatting()
            dupFill.fillBackgroundColor = IndexedColors.ROSE.index
            dupFill.fillPattern = PatternFormatting.SOLID_FOREGROUND

            cfRules.addConditionalFormatting(fullRange, ruleDup, ruleValid)

            // ── Ghi file ──────────────────────────────────────────────────
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "QRScan_${sdf.format(Date())}.xlsx"

            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            dir.mkdirs()

            val file = File(dir, fileName)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()

            ExportResult(success = true, filePath = file.absolutePath)

        } catch (e: Exception) {
            ExportResult(false, error = e.message ?: "Lỗi xuất Excel")
        }
    }

    fun openFile(context: Context, filePath: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", File(filePath)
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Mở bằng..."))
        } catch (e: Exception) {
            // Không có app mở Excel – bỏ qua, user tự copy file
        }
    }
}
