package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExportHelper {

  private const val TAG = "ExcelExportHelper"

  data class ColumnDef(
    val name: String,
    val width: Double
  )

  val COLUMNS = listOf(
    ColumnDef("S.No", 8.0),
    ColumnDef("Date", 14.0),
    ColumnDef("Counsellee Name", 22.0),
    ColumnDef("Counsellee Email", 26.0),
    ColumnDef("Assigned Counsellor", 22.0),
    ColumnDef("Wake Up Time", 16.0),
    ColumnDef("Japa Rounds", 14.0),
    ColumnDef("Finish Time of Japa", 18.0),
    ColumnDef("Mangal Aarti", 16.0),
    ColumnDef("Narasimha Aarti", 16.0),
    ColumnDef("Tulsi Aarti", 16.0),
    ColumnDef("Shringar Aarti", 16.0),
    ColumnDef("Guru Puja", 16.0),
    ColumnDef("Srimad Bhagavatam", 18.0),
    ColumnDef("Satsang", 16.0),
    ColumnDef("All Attended Programs", 30.0),
    ColumnDef("Book Reading Subject", 26.0),
    ColumnDef("Reading Duration", 18.0),
    ColumnDef("Lecture / Hearing Subject", 26.0),
    ColumnDef("Hearing Duration", 18.0),
    ColumnDef("Devotional Seva", 18.0),
    ColumnDef("Seva Duration", 18.0),
    ColumnDef("Sleeping Time", 16.0),
    ColumnDef("Submission Timestamp", 22.0)
  )

  /**
   * Generates a genuine .xlsx file in the cache directory and returns the File.
   */
  fun generateExcelFile(
    context: Context,
    submissions: List<SadhnaSubmission>,
    mentorMap: Map<String, UserProfile>,
    menteeMap: Map<String, UserProfile>,
    filePrefix: String = "ISKCON_Sadhna_Report"
  ): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val safePrefix = filePrefix.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
    val fileName = "${safePrefix}_$timeStamp.xlsx"

    val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
    val outFile = File(exportDir, fileName)

    FileOutputStream(outFile).use { fos ->
      ZipOutputStream(fos).use { zos ->
        // 1. [Content_Types].xml
        addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

        // 2. _rels/.rels
        addZipEntry(zos, "_rels/.rels", buildRootRelsXml())

        // 3. xl/_rels/workbook.xml.rels
        addZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

        // 4. xl/workbook.xml
        addZipEntry(zos, "xl/workbook.xml", buildWorkbookXml())

        // 5. xl/styles.xml
        addZipEntry(zos, "xl/styles.xml", buildStylesXml())

        // 6. xl/worksheets/sheet1.xml
        val sheetXml = buildWorksheetXml(submissions, mentorMap, menteeMap)
        addZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)
      }
    }

    return outFile
  }

  private fun addZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
    val entry = ZipEntry(entryName)
    zos.putNextEntry(entry)
    zos.write(content.toByteArray(Charsets.UTF_8))
    zos.closeEntry()
  }

  private fun buildContentTypesXml(): String {
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
  }

  private fun buildRootRelsXml(): String {
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
  }

  private fun buildWorkbookRelsXml(): String {
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
  }

  private fun buildWorkbookXml(): String {
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <bookViews>
    <workbookView xWindow="0" yWindow="0" windowWidth="20480" windowHeight="10240"/>
  </bookViews>
  <sheets>
    <sheet name="Sadhna Report" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
  }

  private fun buildStylesXml(): String {
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font>
      <sz val="11"/>
      <color rgb="FF000000"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FFFFFFFF"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FFE65100"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="4">
    <fill>
      <patternFill patternType="none"/>
    </fill>
    <fill>
      <patternFill patternType="gray125"/>
    </fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFE65100"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFF5F5F5"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="2">
    <border>
      <left/><right/><top/><bottom/><diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFD0D0D0"/></left>
      <right style="thin"><color rgb="FFD0D0D0"/></right>
      <top style="thin"><color rgb="FFD0D0D0"/></top>
      <bottom style="thin"><color rgb="FFD0D0D0"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <!-- 0: Default Normal -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <!-- 1: Header (Orange BG, White Bold text, centered, border) -->
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <!-- 2: Normal Data cell with border (Left aligned) -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment vertical="center"/>
    </xf>
    <!-- 3: Center Data cell with border (for numbers / dates / statuses) -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>"""
  }

  private fun buildWorksheetXml(
    submissions: List<SadhnaSubmission>,
    mentorMap: Map<String, UserProfile>,
    menteeMap: Map<String, UserProfile>
  ): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
""")

    // Freeze header row
    sb.append("""  <sheetViews>
    <sheetView tabSelected="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="20"/>
""")

    // Column widths
    sb.append("  <cols>\n")
    for (i in COLUMNS.indices) {
      val colNum = i + 1
      val width = COLUMNS[i].width
      sb.append("""    <col min="$colNum" max="$colNum" width="$width" customWidth="1"/>""").append("\n")
    }
    sb.append("  </cols>\n")

    // Sheet Data
    sb.append("  <sheetData>\n")

    // Row 1: Headers
    sb.append("""    <row r="1" ht="28" customHeight="1">""").append("\n")
    for (i in COLUMNS.indices) {
      val cellRef = "${getColumnLetter(i + 1)}1"
      val colName = escapeXml(COLUMNS[i].name)
      sb.append("""      <c r="$cellRef" s="1" t="inlineStr"><is><t>$colName</t></is></c>""").append("\n")
    }
    sb.append("    </row>\n")

    // Data Rows
    var rowIndex = 2
    for (sub in submissions) {
      val mentee = menteeMap[sub.menteeId]
      val devoteeName = sub.devoteeName?.ifEmpty { mentee?.name ?: "Devotee" } ?: (mentee?.name ?: "Devotee")
      val devoteeEmail = mentee?.email ?: ""
      val mentorId = sub.mentorId ?: mentee?.mentorId
      val mentorName = mentorId?.let { mentorMap[it]?.name } ?: (mentee?.mentorName ?: "Unassigned")
      val formattedMentor = if (mentorName.startsWith("HG ", ignoreCase = true)) mentorName else "HG $mentorName"

      val japaFinish = if (sub.japaFinishTime.isNotEmpty()) {
        sub.japaFinishTime
      } else if (sub.japaCompletedHours.isNotEmpty() || sub.japaCompletedMinutes.isNotEmpty()) {
        "${sub.japaCompletedHours.ifEmpty { "0" }}h ${sub.japaCompletedMinutes.ifEmpty { "0" }}m"
      } else {
        "-"
      }

      val mangalAarti = getProgramStatus(sub.spiritualActivities, "Mangal Aarti")
      val narasimhaAarti = getProgramStatus(sub.spiritualActivities, "Narasimha Aarti")
      val tulsiAarti = getProgramStatus(sub.spiritualActivities, "Tulsi Aarti")
      val shringarAarti = getProgramStatus(sub.spiritualActivities, "Shringar Aarti")
      val guruPuja = getProgramStatus(sub.spiritualActivities, "Guru Puja")
      val srimadBhagavatam = getProgramStatus(sub.spiritualActivities, "Srimad Bhagvatam", "Bhagavatam")
      val satsang = getProgramStatus(sub.spiritualActivities, "Satsang")

      val allProgramsStr = if (sub.spiritualActivities.isNotEmpty()) {
        sub.spiritualActivities.joinToString(", ")
      } else {
        "None"
      }

      val sevaStatus = if (sub.sevaCompleted) "Completed" else "Not performed"
      val sevaDuration = if (sub.sevaCompleted) {
        "${sub.sevaHours.ifEmpty { "0" }}h ${sub.sevaMinutes.ifEmpty { "0" }}m"
      } else {
        "-"
      }

      val formattedCreatedAt = formatTimestamp(sub.createdAt)

      val rowValues = listOf(
        Pair((rowIndex - 1).toString(), 3), // S.No
        Pair(sub.date, 3), // Date
        Pair(devoteeName, 2), // Mentee Name
        Pair(devoteeEmail, 2), // Mentee Email
        Pair(formattedMentor, 2), // Assigned Mentor
        Pair(sub.wakeUpTime.ifEmpty { "-" }, 3), // Wake Up Time
        Pair(sub.japaRounds.toString(), 3), // Japa Rounds
        Pair(japaFinish, 3), // Finish Time of Japa
        Pair(mangalAarti, 3), // Mangal Aarti
        Pair(narasimhaAarti, 3), // Narasimha Aarti
        Pair(tulsiAarti, 3), // Tulsi Aarti
        Pair(shringarAarti, 3), // Shringar Aarti
        Pair(guruPuja, 3), // Guru Puja
        Pair(srimadBhagavatam, 3), // SB
        Pair(satsang, 3), // Satsang
        Pair(allProgramsStr, 2), // All Programs
        Pair(sub.bookReadingSubject.ifEmpty { "-" }, 2), // Book Reading
        Pair(if (sub.bookReadingSubject.isNotEmpty()) sub.bookReadingTime.ifEmpty { "-" } else "-", 3), // Reading Duration
        Pair(sub.hearingSubject.ifEmpty { "-" }, 2), // Hearing Subject
        Pair(if (sub.hearingSubject.isNotEmpty()) sub.hearingTime.ifEmpty { "-" } else "-", 3), // Hearing Duration
        Pair(sevaStatus, 3), // Seva Completed
        Pair(sevaDuration, 3), // Seva Duration
        Pair(sub.sleepingTime.ifEmpty { "-" }, 3), // Sleeping Time
        Pair(formattedCreatedAt, 3) // Submission Timestamp
      )

      sb.append("""    <row r="$rowIndex" ht="22" customHeight="1">""").append("\n")
      for (c in rowValues.indices) {
        val cellRef = "${getColumnLetter(c + 1)}$rowIndex"
        val (value, styleId) = rowValues[c]
        val safeVal = escapeXml(value)
        sb.append("""      <c r="$cellRef" s="$styleId" t="inlineStr"><is><t>$safeVal</t></is></c>""").append("\n")
      }
      sb.append("    </row>\n")
      rowIndex++
    }

    sb.append("  </sheetData>\n")

    // AutoFilter
    val lastColLetter = getColumnLetter(COLUMNS.size)
    val maxRow = if (rowIndex > 2) (rowIndex - 1) else 2
    sb.append("""  <autoFilter ref="A1:${lastColLetter}$maxRow"/>""").append("\n")

    sb.append("</worksheet>")
    return sb.toString()
  }

  private fun getProgramStatus(activities: List<String>, vararg programKeywords: String): String {
    val matched = activities.any { act ->
      programKeywords.any { kw -> act.contains(kw, ignoreCase = true) }
    }
    return if (matched) "Attended" else "Not attended"
  }

  private fun formatTimestamp(timestamp: Any?): String {
    if (timestamp == null) return "-"
    return try {
      when (timestamp) {
        is Timestamp -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
          sdf.format(timestamp.toDate())
        }
        is Date -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
          sdf.format(timestamp)
        }
        is Number -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
          sdf.format(Date(timestamp.toLong()))
        }
        is String -> timestamp
        else -> timestamp.toString()
      }
    } catch (e: Exception) {
      "-"
    }
  }

  private fun getColumnLetter(columnNumber: Int): String {
    var num = columnNumber
    val result = StringBuilder()
    while (num > 0) {
      val remainder = (num - 1) % 26
      result.append(('A'.code + remainder).toChar())
      num = (num - 1) / 26
    }
    return result.reverse().toString()
  }

  private fun escapeXml(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
      when (ch) {
        '&' -> out.append("&amp;")
        '<' -> out.append("&lt;")
        '>' -> out.append("&gt;")
        '"' -> out.append("&quot;")
        '\'' -> out.append("&apos;")
        else -> {
          // Allow valid XML chars (skip control characters below 0x20 except tab, lf, cr)
          val code = ch.code
          if (code in 0x20..0xD7FF || code == 0x9 || code == 0xA || code == 0xD) {
            out.append(ch)
          }
        }
      }
    }
    return out.toString()
  }

  /**
   * Saves or copies the generated Excel file to the user's public Downloads directory.
   * Returns the user-friendly location string on success, or null on failure.
   */
  fun saveExcelToPublicDownloads(context: Context, sourceFile: File): String? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
          put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
          put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
          resolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
              inputStream.copyTo(outputStream)
            }
          }
          "Downloads/${sourceFile.name}"
        } else {
          null
        }
      } else {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
          if (!downloadsDir.exists()) downloadsDir.mkdirs()
          val destFile = File(downloadsDir, sourceFile.name)
          sourceFile.copyTo(destFile, overwrite = true)
          MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            null
          )
          "Downloads/${sourceFile.name}"
        } else {
          null
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not save to public Downloads: ${e.message}", e)
      null
    }
  }

  /**
   * Intent to share the generated Excel file via WhatsApp, Gmail, Drive, etc.
   */
  fun shareExcelFile(context: Context, file: File, subject: String = "ISKCON Daily Sadhna Report") {
    try {
      val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "Hare Krishna, please find attached the ISKCON Daily Sadhna Excel export report (.xlsx).")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(intent, "Share Sadhna Excel Report")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  /**
   * Intent to open the generated Excel file directly in Microsoft Excel, Google Sheets, etc.
   */
  fun openExcelFile(context: Context, file: File) {
    try {
      val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      val chooser = Intent.createChooser(intent, "Open Excel File with...")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      // If opening directly fails, fallback to share chooser
      Toast.makeText(context, "Opening share chooser...", Toast.LENGTH_SHORT).show()
      shareExcelFile(context, file)
    }
  }
}
