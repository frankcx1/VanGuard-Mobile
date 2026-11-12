package com.vanfieldguide

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.InputData
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*

data class ScanAnalysis(val text: String, val visual: Boolean, val pages: List<ReferenceHit>, val timing: String)

/** One explicit image request. OCR is bundled; Gemma uses the existing NPU-first fallback. */
object EquipmentScanner {
    // Broad alternatives prevent treating every input as the only manual in the pack.
    private val descriptions = listOf(
        "An electrical power inverter charger with metal casing, cooling vents and electrical terminals in a camper van.",
        "A refrigerator or kitchen appliance.", "A person or an animal.",
        "A landscape with trees and mountains.", "A vehicle exterior on a road.",
        "A shoe, clothing or a bag.", "A phone or computer screen.",
        "A diesel heater, furnace or air conditioner.", "A battery or solar panel.",
        "A blank surface or unreadable blurry photo.")
    suspend fun analyze(app: Application, bitmap: Bitmap): ScanAnalysis = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        var note = ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = try { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { note = "Label reader unavailable; enter the model number below. "; "" }
            finally { recognizer.close() }
        currentCoroutineContext().ensureActive()
        val ocrMs = SystemClock.elapsedRealtime() - start
        var inferenceMs = 0L
        var visual = false
        var pages = emptyList<ReferenceHit>()
        var backend = "Visual AI unavailable; label matching remains available"
        try {
            val bytes = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)); out.toByteArray()
            }
            val result = GemmaRuntime.run(app, true) { session ->
                var elapsed = 0L
                suspend fun embed(input: InputData): FloatArray {
                    val before = SystemClock.elapsedRealtime()
                    return session.embed(input).also { elapsed += SystemClock.elapsedRealtime() - before }
                }
                val image = embed(InputData.Image(bytes))
                val scores = descriptions.map { description ->
                    Retrieval.cosine(image, embed(InputData.Text(StarterManual.DOCUMENT_PREFIX + description)))
                }
                // A retrieval heuristic, never a calibrated identity/confidence score.
                val looksLikeInverter = scores[0] >= 0.20 && scores[0] - scores.drop(1).max() >= 0.02
                val hits = BinderStore(app).use { store ->
                    if (!StarterManual.original(app).isFile) emptyList() else store.passages(session.profile)
                        .filter { it.vector != null }.sortedByDescending { Retrieval.cosine(image, it.vector!!) }
                        .distinctBy { it.page }.take(3).map { ReferenceHit(it.page, it.text, "Gemma image-to-text similarity · unverified reference") }
                }
                Triple(looksLikeInverter, hits, elapsed)
            }
            visual = result.value.first; pages = result.value.second; inferenceMs = result.value.third
            backend = result.profile.label + if (result.fallback) " fallback" else ""
        } catch (e: CancellationException) { throw e }
        catch (_: NativeInferenceFailure) { /* Exact label lookup still works without Gemma. */ }
        ScanAnalysis(text, visual, pages, "$note$backend · AI inference $inferenceMs ms · label reading $ocrMs ms · total ${SystemClock.elapsedRealtime() - start} ms")
    }
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    var busy by mutableStateOf(false); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var label by mutableStateOf(""); private set
    var match by mutableStateOf<EquipmentMatch?>(null); private set
    var pages by mutableStateOf<List<ReferenceHit>>(emptyList()); private set
    var status by mutableStateOf("Photograph the equipment, then its model label if needed."); private set
    var labelEdited by mutableStateOf(false); private set
    private var visual = false
    private val capture get() = File(getApplication<Application>().cacheDir, "capture/equipment.jpg")
    fun captureUri(): Uri {
        check(!busy)
        capture.parentFile?.mkdirs()
        capture.delete()
        return FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.capture", capture)
    }
    fun captureFinished(success: Boolean) {
        if (success && capture.isFile) scan(Uri.fromFile(capture), true)
        else { capture.delete(); status = "Photo cancelled. Take another photo or choose one already on this phone." }
    }
    fun captureFailed() { capture.delete(); status = "Camera could not open. Use Choose photo instead." }
    fun editLabel(value: String) { label = value.take(3000); match = null; labelEdited = true }
    fun checkLabel() { match = EquipmentMatcher.match(label, visual) }
    fun clear() {
        if (busy) return
        preview = null; label = ""; match = null; pages = emptyList(); visual = false; labelEdited = false
        capture.delete(); status = "Scan cleared. Take a photo to find a saved manual."
    }
    fun scan(uri: Uri, deleteCapture: Boolean = false) {
        if (busy) return
        busy = true; preview = null; match = null; pages = emptyList(); label = ""; visual = false; labelEdited = false
        status = "Reading the label and comparing the image locally…"
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplication<Application>().contentResolver, uri)) { decoder, info, _ ->
                        val ratio = minOf(1.0, 1600.0 / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    }
                }
                preview = bitmap
                val result = EquipmentScanner.analyze(getApplication(), bitmap)
                label = result.text.take(3000); visual = result.visual; pages = result.pages; status = result.timing
                checkLabel()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = "Could not scan this photo: ${e.message}. Try another local photo." }
            finally { if (deleteCapture) capture.delete(); busy = false }
        }
    }
}
