package com.vanfieldguide

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

object ScanDeviceChecks {
    fun run(test: Instrumentation) {
        val app = test.targetContext.applicationContext as Application
        val output = File(app.noBackupFilesDir, "scan-validation.json")
        val report = JSONObject().put("fixtureType", "Generated label images; not photos of installed equipment")
        val checks = JSONArray()
        val fixture = File(app.cacheDir, "scan-test-label.jpg")
        fun label(model: String): Bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 65f; typeface = Typeface.MONOSPACE }
            listOf("RENOGY REGO", "INVERTER CHARGER", "MODEL: $model", "INPUT: 12V DC", "OUTPUT: 3000W").forEachIndexed { i, line -> canvas.drawText(line, 55f, 130f + i * 140, paint) }
        }
        fun save() { report.put("checks", checks); output.writeText(report.toString(2)) }
        try {
            GemmaRuntime.initialize(app)
            for ((npu, code, expected) in listOf(
                Triple(true, "RIV1230RCL-1SS-G1-US", MatchKind.EXACT),
                Triple(false, "RIV1230RCL-1SS-G1-US", MatchKind.EXACT),
                Triple(true, "RIV2430RCL-1SS-G1-US", MatchKind.CONFLICT),
                Triple(true, "RIV1230RCL-1SS", MatchKind.FAMILY))) {
                GemmaRuntime.setNpuPreferred(app, npu)
                val bitmap = label(code)
                val result = try { runBlocking { EquipmentScanner.analyze(app, bitmap) } } finally { bitmap.recycle() }
                val match = EquipmentMatcher.match(result.text, result.visual)
                checks.put(JSONObject().put("expected", expected.name).put("actual", match.kind.name)
                    .put("ocr", result.text).put("timing", result.timing).put("pages", JSONArray(result.pages.map { it.page + 1 })))
                save()
                check(match.kind == expected) { "OCR/match: ${result.text}: $match" }
                check(result.timing.startsWith(if (npu) "Pixel NPU" else "CPU/GPU")) { result.timing }
                check(result.pages.size == 3) { "Image-to-manual retrieval failed" }
            }
            val blank = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val negative = try { runBlocking { EquipmentScanner.analyze(app, blank) } } finally { blank.recycle() }
            check(EquipmentMatcher.match(negative.text, negative.visual).kind == MatchKind.NONE) { "Blank image became an equipment candidate" }
            checks.put(JSONObject().put("blankImage", "NONE").put("timing", negative.timing)); save()
            val bitmap = label("RIV1230RCL-1SS-G1-US")
            fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
            lateinit var vm: ScanViewModel
            test.runOnMainSync { vm = ScanViewModel(app); vm.scan(Uri.fromFile(fixture)) }
            val deadline = SystemClock.elapsedRealtime() + 60000
            while (vm.busy) { check(SystemClock.elapsedRealtime() < deadline); Thread.sleep(100) }
            check(vm.match?.kind == MatchKind.EXACT) { vm.status }
            test.runOnMainSync {
                vm.editLabel("RIV2430RCL-1SS-G1-US")
                check(vm.match == null) { "Editing retained stale match" }
                vm.checkLabel(); check(vm.match?.kind == MatchKind.CONFLICT)
                vm.clear(); check(vm.preview == null && vm.pages.isEmpty() && vm.match == null && vm.label.isEmpty())
                val capture = vm.captureUri(); check(capture.authority == "${app.packageName}.capture")
                vm.captureFinished(false); check(!vm.busy)
            }
            test.runOnMainSync {
                val uri = vm.captureUri()
                app.contentResolver.openOutputStream(uri)!!.use { out -> fixture.inputStream().use { it.copyTo(out) } }
                vm.captureFinished(true)
            }
            val captureDeadline = SystemClock.elapsedRealtime() + 60000
            while (vm.busy) { check(SystemClock.elapsedRealtime() < captureDeadline); Thread.sleep(100) }
            check(vm.match?.kind == MatchKind.EXACT) { vm.status }
            check(!File(app.cacheDir, "capture/equipment.jpg").exists()) { "Temporary capture was retained" }
            test.runOnMainSync { vm.clear() }
            checks.put(JSONObject().put("viewModel", "PASS: decode, OCR, edited conflict, clear, capture URI, cancel, capture result and cleanup"))
            report.put("result", "PASS"); save()
            test.finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: bundled OCR, NPU and CPU image retrieval, exact/family/conflict/blank, scan state, capture URI\n") })
        } catch (error: Throwable) {
            report.put("result", "FAIL").put("error", error.stackTraceToString()); save()
            test.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n") })
        } finally { fixture.delete(); GemmaRuntime.setNpuPreferred(app, true) }
    }
}
