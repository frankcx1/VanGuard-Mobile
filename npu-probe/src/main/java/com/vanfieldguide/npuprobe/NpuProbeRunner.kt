package com.vanfieldguide.npuprobe

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Runs in a separate package/process. No access to the field guide's model/index or photos. */
class NpuProbeRunner : Instrumentation() {
    private var mode = "npu-text"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode") ?: mode; start() }
    override fun onStart() {
        val report = File(targetContext.noBackupFilesDir, "$mode.json")
        val result = JSONObject().put("mode", mode).put("stage", "starting")
        fun record(stage: String) { result.put("stage", stage); report.writeText(result.toString(2)); Log.i("VFGNpuProbe", "$mode: $stage") }
        try {
            val npu = mode.startsWith("npu")
            val vision = mode.endsWith("image")
            val model = File(targetContext.noBackupFilesDir, if (npu) "tensor-g5.litertlm" else "cpu-gpu.litertlm")
            val expected = if (npu) "96cdbae7b3dd6fa0f829fef2d0dd5bafc0179890d9bafbfe972db852e818d758" else "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
            record("verifying model")
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().buffered().use { input -> val buffer = ByteArray(1024 * 1024); while (true) {
                val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read)
            } }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected)
            val dir = targetContext.applicationInfo.nativeLibraryDir
            result.put("nativeLibraryDir", dir).put("libraries", JSONArray(File(dir).list()?.toList() ?: emptyList<String>()))
            val backend = if (npu) Backend.NPU(dir) else Backend.CPU()
            val engine = EmbeddingEngine(EmbeddingEngineConfig(modelPath = model.path, backend = backend,
                visionBackend = if (vision) { if (npu) Backend.NPU(dir) else Backend.GPU() } else null,
                maxInputLength = 1024, cacheDir = targetContext.cacheDir.path))
            try {
                record("initializing")
                val start = SystemClock.elapsedRealtimeNanos()
                engine.initialize()
                result.put("loadMs", (SystemClock.elapsedRealtimeNanos() - start) / 1e6)
                val times = JSONArray()
                val outputs = JSONArray()
                val image = if (vision) makeImage() else null
                val queries = listOf("van pictures", "inside a camper van", "electrical wiring", "a dog on a beach", "inverter operating instructions")
                for (i in 0 until 5) {
                    record("inference ${i + 1}")
                    val input = if (vision) InputData.Image(image!!) else InputData.Text("task: search query | text: ${queries[i]}")
                    val began = SystemClock.elapsedRealtimeNanos()
                    val vector = engine.computeEmbedding(listOf(input)).embedding
                    times.put((SystemClock.elapsedRealtimeNanos() - began) / 1e6)
                    check(vector.size == 768 && vector.all { it.isFinite() } && vector.any { it != 0f }) { "Invalid embedding" }
                    outputs.put(JSONArray(vector.toList()))
                }
                result.put("inferenceMs", times).put("vectors", outputs)
                record("success")
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "$mode: PASS, load ${result.getDouble("loadMs")} ms, inference $times ms\n") })
            } finally { if (engine.isInitialized()) engine.close() }
        } catch (error: Throwable) {
            result.put("error", error.stackTraceToString()); record("failed")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "$mode: FAIL ${error.message}\n") })
        }
    }
    private fun makeImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(200, 225, 240))
        val paint = Paint().apply { color = Color.DKGRAY }
        canvas.drawRect(100f, 120f, 530f, 350f, paint)
        paint.color = Color.BLUE; canvas.drawRect(370f, 150f, 500f, 240f, paint)
        paint.color = Color.BLACK; canvas.drawCircle(190f, 355f, 45f, paint); canvas.drawCircle(450f, 355f, 45f, paint)
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); bitmap.recycle(); out.toByteArray() }
    }
}
