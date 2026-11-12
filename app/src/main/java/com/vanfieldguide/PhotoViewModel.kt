package com.vanfieldguide

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.InputData
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhotoStore(application)
    private val resolver = application.contentResolver
    private var job: Job? = null
    var busy by mutableStateOf(false); private set
    var coverage by mutableStateOf(PhotoCoverage()); private set
    var access by mutableStateOf("Photo access not granted"); private set
    var status by mutableStateOf("Choose photo access, then build the local Gemma index."); private set
    var photos by mutableStateOf<List<DevicePhoto>>(emptyList()); private set
    var results by mutableStateOf<List<DevicePhoto>>(emptyList()); private set
    var searched by mutableStateOf(false); private set
    var resultQuery by mutableStateOf(""); private set
    var viewed by mutableStateOf<DevicePhoto?>(null); private set
    var revision by mutableStateOf(0); private set

    private suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val full = app.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val partial = app.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val found: List<DevicePhoto> = if (!full && !partial) emptyList() else resolver.query(collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.GENERATION_MODIFIED),
            "${MediaStore.Images.Media.IS_PENDING}=0 AND ${MediaStore.Images.Media.IS_TRASHED}=0", null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor -> buildList<DevicePhoto> {
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                add(DevicePhoto(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                    cursor.getString(1) ?: "Device photo", "${cursor.getLong(2)}:${cursor.getLong(3)}:${cursor.getLong(4)}"))
            }
        } } ?: error("Cannot read the device photo library")
        store.reconcile(found)
        val count = store.coverage()
        withContext(Dispatchers.Main) {
            photos = found; coverage = count
            access = when { full -> "Access: all local photos"; partial -> "Access: selected local photos"; else -> "Photo access not granted" }
            val allowed = found.associateBy { it.uri }
            results = results.filter { allowed[it.uri]?.version == it.version }
            if (viewed?.let { allowed[it.uri]?.version != it.version } == true) viewed = null
        }
    }
    private fun work(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        job = viewModelScope.launch {
            try { operation() }
            catch (error: CancellationException) { status = "Paused. Completed photos are saved; tap Build / resume photo index to continue."; throw error }
            catch (error: Exception) { status = "Photo task stopped: ${error.message ?: "Could not read photos"}. Check access and retry." }
            catch (error: LinkageError) { status = "Native image model unavailable. Your original photos are unchanged." }
            finally {
                withContext(NonCancellable) { coverage = withContext(Dispatchers.IO) { store.coverage() } }
                busy = false
            }
        }
    }
    fun refresh() = work {
        syncLibrary()
        status = if (photos.isEmpty()) "No local photos are accessible. Choose photo access, or save cloud photos to your device first."
            else if (maxOf(coverage.indexed, coverage.npuIndexed) == coverage.total) "${maxOf(coverage.indexed, coverage.npuIndexed)}/${coverage.total} local photos indexed. Ready to search."
            else "${maxOf(coverage.indexed, coverage.npuIndexed)}/${coverage.total} local photos indexed. Build or resume the index to include the rest."
    }
    fun pause() { job?.cancel() }
    fun clearSearch() { results = emptyList(); searched = false; revision++ }
    fun open(photo: DevicePhoto) { viewed = photo }
    fun close() { viewed = null }
    fun clearIndex() = work {
        withContext(Dispatchers.IO) { store.clear() }
        results = emptyList(); searched = false
        syncLibrary()
        status = "Photo index removed. Your original photos are unchanged."
    }
    fun index() = work {
        syncLibrary()
        if (photos.isEmpty()) { status = "Choose access to local photos first."; return@work }
        results = emptyList(); searched = false
        status = "Preparing NPU and fallback photo indexes. Keep this screen open; you can pause anytime."
        val start = SystemClock.elapsedRealtime()
        val summary = withContext(Dispatchers.IO) {
            GemmaRuntime.prepare(getApplication(), true, needed = { store.pending(it).isNotEmpty() }) { session ->
                val pending = store.pending(session.profile)
                pending.forEachIndexed { position, photo ->
                    currentCoroutineContext().ensureActive()
                    withContext(Dispatchers.Main) { status = "${session.profile.label}: photo ${position + 1}/${pending.size} remaining" }
                    val bytes = try { imageBytes(photo) } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { store.failed(photo); null }
                    if (bytes != null) store.save(photo, session.embed(InputData.Image(bytes)), session.profile)
                    val count = store.coverage()
                    withContext(Dispatchers.Main) { coverage = count }
                }
            }
        }
        status = "$summary · ${(SystemClock.elapsedRealtime() - start) / 1000}s. ${coverage.failed} unreadable photos."
    }
    fun search(query: String) = work {
        require(query.isNotBlank() && query.length <= 800) { "Enter a description under 800 characters" }
        syncLibrary()
        results = emptyList(); searched = true; resultQuery = query.trim()
        require(coverage.indexed > 0 || coverage.npuIndexed > 0) { "Build the photo index first" }
        status = "Gemma is matching your description to photo content…"
        val start = SystemClock.elapsedRealtime()
        val answer = withContext(Dispatchers.IO) {
            GemmaRuntime.run(getApplication(), false, eligible = { profile ->
                if (profile == ComputeProfile.NPU) coverage.npuIndexed > 0 && coverage.npuIndexed >= coverage.indexed else coverage.indexed > 0
            }) { session ->
                val vector = session.embed(InputData.Text(StarterManual.QUERY_PREFIX + query.trim()))
                store.search(vector, session.profile)
            }
        }
        val byUri = photos.associateBy { it.uri }
        results = answer.value.mapNotNull { byUri[it] }
        revision++
        val count = if (answer.profile == ComputeProfile.NPU) coverage.npuIndexed else coverage.indexed
        status = "${answer.profile.label}${if (answer.fallback) " fallback" else ""} · ${results.size} closest photos from $count/${coverage.total} indexed · ${SystemClock.elapsedRealtime() - start} ms."
    }
    suspend fun bitmap(photo: DevicePhoto, edge: Int): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, Uri.parse(photo.uri))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            val ratio = minOf(1.0, edge.toDouble() / longest)
            decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
    }
    private suspend fun imageBytes(photo: DevicePhoto): ByteArray {
        val bitmap = bitmap(photo, 1280)
        return try { ByteArrayOutputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)); out.toByteArray()
        } } finally { bitmap.recycle() }
    }
    override fun onCleared() { store.close(); super.onCleared() }
}
