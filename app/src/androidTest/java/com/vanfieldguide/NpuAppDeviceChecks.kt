package com.vanfieldguide

import android.app.Application
import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly invoked integration check on the owner's prepared pack. Does not delete originals/indexes. */
object NpuAppDeviceChecks {
    fun run(test: Instrumentation) {
        val app = test.targetContext.applicationContext as Application
        val output = File(app.noBackupFilesDir, "npu-app-validation.json")
        val report = JSONObject()
        var photos: PhotoViewModel? = null
        var binder: BinderViewModel? = null
        val npu = GemmaRuntime.file(app, ComputeProfile.NPU)
        val backup = File(npu.parentFile, "npu-validation-backup.litertlm")
        fun record(stage: String) { report.put("stage", stage); output.writeText(report.toString(2)) }
        fun awaitIdle(busy: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 240000
            while (busy()) { check(SystemClock.elapsedRealtime() < deadline) { "Timed out" }; Thread.sleep(100) }
            test.waitForIdleSync()
        }
        try {
            record("initializing")
            test.runOnMainSync {
                GemmaRuntime.initialize(app); GemmaRuntime.setNpuPreferred(app, true)
                photos = PhotoViewModel(app); binder = BinderViewModel(app)
            }
            // Let initial coverage refresh finish before indexing.
            Thread.sleep(700)
            val p = photos!!; val b = binder!!
            record("indexing manual")
            test.runOnMainSync { b.index() }; awaitIdle { b.busy }
            report.put("manualIndex", b.status)
            check(b.coverage.npuEmbedded == b.coverage.chunks && b.coverage.chunks > 0) { b.status }
            check(b.coverage.embedded == b.coverage.chunks)
            record("indexing photos")
            test.runOnMainSync { p.index() }; awaitIdle { p.busy }
            report.put("photoIndex", p.status)
            check(p.coverage.npuIndexed == p.coverage.total && p.coverage.total > 0) { p.status }
            check(p.coverage.indexed == p.coverage.total)
            val searches = JSONArray()
            for (query in listOf("van pictures", "inside the camper", "electrical wiring")) {
                val pair = JSONObject().put("query", query)
                for (accelerated in listOf(true, false)) {
                    test.runOnMainSync { GemmaRuntime.setNpuPreferred(app, accelerated); p.search(query) }; awaitIdle { p.busy }
                    check(p.results.isNotEmpty()) { p.status }
                    check(p.status.startsWith(if (accelerated) "Pixel NPU" else "CPU/GPU")) { p.status }
                    pair.put(if (accelerated) "npu" else "cpu", JSONObject().put("status", p.status).put("uris", JSONArray(p.results.map { it.uri })))
                }
                searches.put(pair)
            }
            report.put("photos", searches)
            val manual = JSONArray()
            for (accelerated in listOf(true, false)) {
                test.runOnMainSync { GemmaRuntime.setNpuPreferred(app, accelerated); b.search("inverter operating instructions", true) }; awaitIdle { b.busy }
                check(b.results.isNotEmpty() && b.status.contains(if (accelerated) "Pixel NPU" else "CPU/GPU")) { b.status }
                manual.put(JSONObject().put("npu", accelerated).put("status", b.status).put("pages", JSONArray(b.results.map { it.page + 1 })))
            }
            report.put("manual", manual)
            record("testing real automatic fallback")
            check(!backup.exists() && npu.renameTo(backup))
            npu.writeBytes(byteArrayOf(1)) // Controlled verification failure; original is preserved and restored below.
            test.runOnMainSync { GemmaRuntime.resetVerification(); GemmaRuntime.setNpuPreferred(app, true); p.search("van pictures") }; awaitIdle { p.busy }
            check(p.status.startsWith("CPU/GPU fallback") && p.results.isNotEmpty()) { p.status }
            report.put("automaticFallback", p.status).put("fallbackReason", GemmaRuntime.npuIssue)
            check(npu.delete() && backup.renameTo(npu))
            test.runOnMainSync { GemmaRuntime.resetVerification(); GemmaRuntime.setNpuPreferred(app, true); p.search("van pictures") }; awaitIdle { p.busy }
            check(p.status.startsWith("Pixel NPU")) { p.status }
            report.put("recoveredNpu", p.status)
            record("success")
            test.finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: dual indexes, NPU/CPU photo and manual searches, real automatic fallback, NPU recovery\n") })
        } catch (error: Throwable) {
            report.put("error", error.stackTraceToString()); record("failed")
            test.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.message}\n") })
        } finally {
            if (backup.exists()) { if (npu.exists()) npu.delete(); check(backup.renameTo(npu)) }
            test.runOnMainSync { GemmaRuntime.resetVerification(); GemmaRuntime.setNpuPreferred(app, true) }
        }
    }
}
