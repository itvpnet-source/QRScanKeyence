package com.keyence.qrscan

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExporter {

    data class ExportResult(
        val success: Boolean,
        val filePath: String = "",
        val error: String = ""
    )

    fun export(context: Context, items: List<ScanItem>, sessionId: Long): ExportResult {
        if (items.isEmpty()) return ExportResult(false, error = "Danh sách rỗng")

        return try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "QRScan_${sdf.format(Date())}.xlsx"
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            dir.mkdirs()
            val file = File(dir, fileName)

            ZipOutputStream(FileOutputStream(file)).use { zos ->

                // [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>""".trimIndent().toByteArray())
                zos.closeEntry()

                // _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent().toByteArray())
                zos.closeEntry()

                // xl/_rels/workbook.xml.rels
                zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>""".trimIndent().toByteArray())
                zos.closeEntry()

                // xl/workbook.xml
                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".trimIndent().toByteArray())
                zos.closeEntry()

                // xl/styles.xml — style 0=default, 1=highlight xanh, 2=highlight đỏ
                zos.putNextEntry(ZipEntry("xl/styles.xml"))
                zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills>
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFC6EFCE"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/></patternFill></fill>
  </fills>
  <borders><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="0" fillId="2" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0"/>
  </cellXfs>
</styleSheet>""".trimIndent().toByteArray())
                zos.closeEntry()

                // xl/sharedStrings.xml — chứa tất cả chuỗi QR
                val escaped = items.map { escapeXml(it.rawQr) }
                zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                val ssXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${items.size}" uniqueCount="${items.size}">""")
                    escaped.forEach { append("<si><t xml:space=\"preserve\">$it</t></si>") }
                    append("</sst>")
                }
                zos.write(ssXml.toByteArray())
                zos.closeEntry()

                // xl/worksheets/sheet1.xml
                // style: 1=xanh (hợp lệ), 2=đỏ (trùng)
                val dupSet = mutableSetOf<String>()
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                val wsXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                    append("""<cols><col min="1" max="1" width="60" customWidth="1"/></cols>""")
                    append("<sheetData>")
                    items.forEachIndexed { idx, item ->
                        val isDup = !dupSet.add(item.rawQr)
                        val style = if (isDup) 2 else 1  // đỏ nếu trùng, xanh nếu hợp lệ
                        append("""<row r="${idx + 1}">""")
                        append("""<c r="A${idx + 1}" t="s" s="$style"><v>$idx</v></c>""")
                        append("</row>")
                    }
                    append("</sheetData>")
                    append("</worksheet>")
                }
                zos.write(wsXml.toByteArray())
                zos.closeEntry()
            }

            ExportResult(success = true, filePath = file.absolutePath)

        } catch (e: Exception) {
            ExportResult(false, error = e.message ?: "Lỗi xuất Excel")
        }
    }

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun openFile(context: Context, filePath: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", File(filePath)
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Mở bằng..."))
        } catch (_: Exception) {}
    }
}
