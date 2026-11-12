package com.vanfieldguide

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.InputData
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BinderViewModel(application: Application) : AndroidViewModel(application) {    companion object {        const val INTRO_STATUS = "Local AI App showcasing Gemma\nEmbeddingGemma 2 Text Vision · 440M parameters"    }
    private val store = BinderStore(application)
    private val original = StarterManual.original(application)
    private var job: Job? = null
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Import the matching REGO manual to begin."); private set
    var coverage by mutableStateOf(Coverage()); private set
    var pages by mutableStateOf<List<PageRecord>>(emptyList()); private set
    var results by mutableStateOf<List<ReferenceHit>>(emptyList()); private set
    var diagrams by mutableStateOf<List<DiagramReference>>(emptyList()); private set
    var searchRevision by mutableStateOf(0); private set
    var imported by mutableStateOf(original.isFile); private set
    var viewedPage by mutableStateOf<Int?>(null); private set
    var pageBitmap by mutableStateOf<Bitmap?>(null); private set
    var viewerStatus by mutableStateOf(""); private set
    private var renderJob: Job? = null

    init { viewModelScope.launch { refresh(); if (imported) status = INTRO_STATUS } }
    private suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) { store.coverage() to store.pages() }
        coverage = snapshot.first; pages = snapshot.second; imported = original.isFile
    }
    private fun work(operation: suspend () -> String) {
        if (busy) return
        busy = true; results = emptyList(); diagrams = emptyList()
        job = viewModelScope.launch {
            try { status = operation() }
            catch (error: CancellationException) { status = "Paused. Completed pages and embeddings are saved; resume when ready."; throw error }
            catch (error: Exception) { status = "Could not finish: ${error.message ?: error.javaClass.simpleName}. Saved originals and completed index entries remain available." }
            catch (error: LinkageError) { status = "Native model unavailable. Keyword search and PDF browsing remain available." }
            finally { withContext(NonCancellable) { refresh() }; busy = false }
        }
    }
    fun cancel() { job?.cancel() }
    fun clearSearch() {
        if (busy) return
        results = emptyList()
        diagrams = emptyList()
        searchRevision++
        status = if (imported) "Search cleared. Your saved manual is ready for another request." else "Search cleared. Import the matching REGO manual to begin."
    }
    fun importManual(uri: Uri) = work {
        status = "Copying and verifying the complete manual…"
        withContext(Dispatchers.IO) {
            original.parentFile?.mkdirs()
            val temporary = File(original.parentFile, "manual.importing")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            size += n; require(size <= 100L * 1024 * 1024) { "This starter importer accepts the supplied REGO manual, under 100 MB" }
                            digest.update(buffer, 0, n); output.write(buffer, 0, n)
                        }
                    }
                }
                require(digest.digest().joinToString("") { "%02x".format(it) } == StarterManual.SHA256) {
                    "This is not the reviewed REGO RIV1230RCL-1SS manual (A0). Other models and revisions are excluded from this starter pack"
                }
                java.nio.file.Files.move(temporary.toPath(), original.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        extractPages()
    }
    fun resumeExtraction() = work { extractPages() }
    private suspend fun extractPages(): String = withContext(Dispatchers.IO) {
        require(original.isFile) { "Import the manual first" }
        PdfRenderer(ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            store.setTotal(pdf.pageCount)
            for (number in 0 until pdf.pageCount) {
                currentCoroutineContext().ensureActive()
                if (store.hasPage(number)) continue
                try {
                    val text = pdf.openPage(number).use { page -> page.textContents.joinToString("\n") { it.text } }
                    store.savePage(number, text, if (text.isBlank()) "empty" else "text")
                } catch (error: Exception) { store.savePage(number, "", "error") }
                withContext(Dispatchers.Main) { status = "Extracting PDF page ${number + 1} of ${pdf.pageCount}…" }
            }
        }
        "Original saved locally. Page extraction complete; see coverage below. Keyword search is available. Build the semantic index to add Gemma search."
    }

    fun index() = work {
        require(imported) { "Import the manual first" }
        val start = SystemClock.elapsedRealtime()
        status = "Preparing NPU and fallback manual indexes…"
        val summary = withContext(Dispatchers.IO) {
            GemmaRuntime.prepare(getApplication(), false, needed = { profile -> store.passages(profile).any { it.vector == null } }) { session ->
                val pending = store.passages(session.profile).filter { it.vector == null }
                for ((position, part) in pending.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val vector = session.embed(InputData.Text(StarterManual.DOCUMENT_PREFIX + part.text))
                    store.saveVector(part.id, vector, session.profile)
                    withContext(Dispatchers.Main) { status = "${session.profile.label}: ${position + 1}/${pending.size} remaining passages · PDF page ${part.page + 1}" }
                }
            }
        }
        "$summary · ${(SystemClock.elapsedRealtime() - start) / 1000}s. Separate indexes saved locally."
    }

    fun search(query: String, semantic: Boolean) = work {
        require(query.isNotBlank()) { "Enter or speak a request" }
        require(query.length <= 800) { "Keep the search under 800 characters" }
        require(!Regex("(?i)\\b(heater|heating|webasto)\\b").containsMatchIn(query)) { "Inverter is selected. Heating sources are not in this starter pack" }
        status = "Searching saved manual pages…"
        val start = SystemClock.elapsedRealtime()
        var method = "Keyword baseline"
        var diagramMatches: List<DiagramReference> = emptyList()
        val hits = withContext(Dispatchers.IO) {
            val lexical = store.passages()
            diagramMatches = if (original.isFile) Retrieval.diagrams(lexical, query, StarterManual.DIAGRAMS) else emptyList()
            if (semantic) {
                try {
                    val counts = store.coverage()
                    val answer = GemmaRuntime.run(getApplication(), false, eligible = { profile ->
                        if (profile == ComputeProfile.NPU) counts.npuEmbedded > 0 && counts.npuEmbedded >= counts.embedded else counts.embedded > 0
                    }) { session ->
                        val vector = session.embed(InputData.Text(StarterManual.QUERY_PREFIX + query.trim()))
                        Retrieval.search(store.passages(session.profile), query, vector)
                    }
                    method = "${answer.profile.label}${if (answer.fallback) " fallback" else ""} · hybrid text ranks"
                    answer.value
                } catch (error: CancellationException) { throw error }
                catch (_: NativeInferenceFailure) { method = "Gemma unavailable or not indexed; keyword results only"; Retrieval.search(lexical, query, null) }
            } else Retrieval.search(lexical, query, null)
        }
        results = hits; diagrams = diagramMatches; searchRevision++
        if (hits.isEmpty() && diagramMatches.isEmpty()) "No saved page matched${if (Retrieval.identifiers(query).isNotEmpty()) " all exact identifiers in this request" else " this request"}. Try different words or browse the original."
        else "${diagramMatches.size} saved diagrams · ${hits.size} closest manual pages · ${SystemClock.elapsedRealtime() - start} ms. $method; diagram cards match saved labels."
    }

    suspend fun diagramPreview(number: Int): Bitmap = withContext(Dispatchers.IO) {
        require(StarterManual.DIAGRAMS.any { it.page == number } && original.isFile)
        renderOriginal(number, 640)
    }

    private fun renderOriginal(number: Int, width: Int): Bitmap =
        PdfRenderer(ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(number).use { page ->
                val height = (width.toDouble() * page.height / page.width).toInt().coerceIn(1, 3300)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }

    fun openPage(number: Int, scale: Int = 1) {
        if (number !in 0 until coverage.totalPages || !original.isFile) return
        renderJob?.cancel(); viewedPage = number; pageBitmap = null; viewerStatus = "Loading original PDF page…"
        renderJob = viewModelScope.launch {
            try {
                pageBitmap = withContext(Dispatchers.IO) {
                    renderOriginal(number, (1100 * scale).coerceAtMost(2200))
                }
                viewerStatus = ""
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { viewerStatus = "Page could not be rendered: ${error.message}" }
        }
    }
    fun closeViewer() { renderJob?.cancel(); viewedPage = null; pageBitmap = null }
    fun deleteAppCopy() = work {
        closeViewer()
        withContext(Dispatchers.IO) {
            if (original.exists()) check(original.delete()) { "Could not delete the app's copy" }
            store.removeCopyIndex()
        }
        "App copy and index removed. Your source file in Downloads is unchanged."
    }
}
