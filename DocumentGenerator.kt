package com.example.geminidevagent.tools

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.geminidevagent.data.WorkspaceFile
import com.example.geminidevagent.data.WorkspaceRepository
import org.json.JSONArray
import java.io.ByteArrayOutputStream

object DocumentGenerator {

    fun createPdf(
        repo: WorkspaceRepository,
        filename: String,
        title: String,
        bodyText: String
    ): WorkspaceFile {
        val safeName = if (filename.endsWith(".pdf", true)) filename else "$filename.pdf"
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val maxLineWidth = pageWidth - margin * 2

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11.5f
            isAntiAlias = true
        }

        val wrappedLines = wrapText(bodyText, bodyPaint, maxLineWidth)
        var lineIdx = 0
        var pageNum = 1

        while (lineIdx < wrappedLines.size || pageNum == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin + 10f

            if (pageNum == 1) {
                canvas.drawText(title.take(65), margin, y, titlePaint)
                y += 28f
            }

            while (lineIdx < wrappedLines.size && y < pageHeight - margin) {
                canvas.drawText(wrappedLines[lineIdx], margin, y, bodyPaint)
                y += 17f
                lineIdx++
            }
            doc.finishPage(page)
            pageNum++
            if (lineIdx >= wrappedLines.size) break
        }

        val baos = ByteArrayOutputStream()
        doc.writeTo(baos)
        doc.close()
        return repo.writeBytes(safeName, baos.toByteArray())
    }

    fun createExcelXml(
        repo: WorkspaceRepository,
        filename: String,
        sheetName: String,
        headers: JSONArray,
        rows: JSONArray
    ): WorkspaceFile {
        val safeName = if (filename.endsWith(".xls", true)) filename else "$filename.xls"
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\"?>\n")
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
        sb.append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
        sb.append("<Worksheet ss:Name=\"${escapeXml(sheetName.take(30))}\"><Table>\n")

        if (headers.length() > 0) {
            sb.append("<Row>")
            for (i in 0 until headers.length()) {
                sb.append("<Cell><Data ss:Type=\"String\">${escapeXml(headers.optString(i))}</Data></Cell>")
            }
            sb.append("</Row>\n")
        }

        for (r in 0 until rows.length()) {
            val row = rows.optJSONArray(r) ?: continue
            sb.append("<Row>")
            for (c in 0 until row.length()) {
                val cellVal = row.optString(c)
                val isNum = cellVal.toDoubleOrNull() != null
                val type = if (isNum) "Number" else "String"
                sb.append("<Cell><Data ss:Type=\"$type\">${escapeXml(cellVal)}</Data></Cell>")
            }
            sb.append("</Row>\n")
        }

        sb.append("</Table></Worksheet></Workbook>")
        return repo.writeFile(safeName, sb.toString())
    }

    fun createWordDoc(
        repo: WorkspaceRepository,
        filename: String,
        title: String,
        htmlBody: String
    ): WorkspaceFile {
        val safeName = if (filename.endsWith(".doc", true)) filename else "$filename.doc"
        val docContent = """
            <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word'>
            <head><meta charset='utf-8'><title>${escapeXml(title)}</title>
            <style>body{font-family:Calibri,Arial,sans-serif;line-height:1.5;margin:36pt;} h1{color:#1a73e8;} table{border-collapse:collapse;width:100%;} td,th{border:1px solid #ccc;padding:6pt;}</style>
            </head><body><h1>${escapeXml(title)}</h1>$htmlBody</body></html>
        """.trimIndent()
        return repo.writeFile(safeName, docContent)
    }

    fun createSlideDeck(
        repo: WorkspaceRepository,
        filename: String,
        deckTitle: String,
        slidesJson: JSONArray
    ): WorkspaceFile {
        val safeName = if (filename.endsWith(".html", true)) filename else "${filename}_slides.html"
        val slidesHtml = StringBuilder()
        for (i in 0 until slidesJson.length()) {
            val s = slidesJson.optJSONObject(i) ?: continue
            val sTitle = escapeXml(s.optString("title", "Slide ${i + 1}"))
            val sBody = escapeXml(s.optString("body", "")).replace("\n", "<br/>")
            slidesHtml.append("<section class='slide'><h2>$sTitle</h2><p>$sBody</p><div class='num'>${i + 1} / ${slidesJson.length()}</div></section>\n")
        }
        val html = """
            <!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>${escapeXml(deckTitle)}</title>
            <style>
              body{margin:0;background:#090d16;color:#f1f5f9;font-family:Inter,sans-serif;scroll-snap-type:y mandatory;overflow-y:scroll;height:100vh;}
              .slide{min-height:100vh;padding:48px 32px;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center;scroll-snap-align:start;border-bottom:1px solid #1e293b;position:relative;}
              h2{color:#7dd3fc;font-size:1.8rem;margin:0 0 16px;} p{font-size:1.1rem;line-height:1.6;max-width:720px;}
              .num{position:absolute;bottom:20px;right:24px;color:#64748b;font-size:0.85rem;}
            </style></head><body>$slidesHtml</body></html>
        """.trimIndent()
        return repo.writeFile(safeName, html)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                result.add("")
            } else {
                var current = ""
                for (word in paragraph.split(" ")) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotEmpty()) result.add(current)
                        current = word
                    }
                }
                if (current.isNotEmpty()) result.add(current)
            }
        }
        return result
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
