package com.vanfieldguide

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelProbe(application: Application) : AndroidViewModel(application) {
    private val model = File(application.noBackupFilesDir, "embeddinggemma.litertlm")
    var busy by mutableStateOf(false); private set
    var hasModel by mutableStateOf(ComputeProfile.entries.any { GemmaRuntime.available(application, it) }); private set
    var hasNpu by mutableStateOf(GemmaRuntime.available(application, ComputeProfile.NPU)); private set    var hasCpu by mutableStateOf(GemmaRuntime.available(application, ComputeProfile.CPU_GPU)); private set
    var status by mutableStateOf(if (hasModel) "Model stored locally. Device inference has not run this session." else "Import the pinned Gemma model to begin."); private set

    fun importModel(uri: Uri) = work {
        val temporary = File(model.parentFile, "model.importing")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected file" }
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytes += read
                        require(bytes <= ComputeProfile.NPU.bytes) { "File exceeds the pinned model size" }
                        hash.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            val digest = hash.digest().joinToString("") { "%02x".format(it) }
            val profile = ComputeProfile.entries.singleOrNull { it.sha256 == digest && it.bytes == bytes }
                ?: error("Model checksum does not match either pinned artifact")
            require(profile != ComputeProfile.NPU || GemmaRuntime.supported()) { "This NPU model requires Tensor G5" }
            java.nio.file.Files.move(temporary.toPath(), GemmaRuntime.file(getApplication(), profile).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            GemmaRuntime.resetVerification()
            "${profile.label} model verified and saved. Build / resume indexes to prepare search."
        } finally { temporary.delete() }
    }

    fun runText(text: String) {
        if (text.isBlank()) return
        probe("text") { InputData.Text("task: search query | text: ${text.trim()}") }
    }

    fun runImage(uri: Uri) = probe("image") {
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected image" }
            input.readNBytes(20 * 1024 * 1024 + 1)
        }
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024) { "Choose an image smaller than 20 MB for the device test" }
        InputData.Image(bytes)
    }

    private fun probe(kind: String, input: () -> InputData) = work {
        val content = input()
        val start = SystemClock.elapsedRealtime()
        var inferenceMs = 0L
        val response = GemmaRuntime.run(getApplication(), kind == "image") { session ->
            val began = SystemClock.elapsedRealtime()
            session.embed(content).also { inferenceMs = SystemClock.elapsedRealtime() - began }
        }
        "${response.profile.label}${if (response.fallback) " fallback" else ""}: ${response.value.size} dimensions. Total: ${SystemClock.elapsedRealtime() - start} ms; inference: $inferenceMs ms."
    }

    private fun work(block: suspend () -> String) {
        if (busy) return
        busy = true
        status = "Working locally…"
        viewModelScope.launch {
            try { status = withContext(Dispatchers.IO) { block() } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = "Test incomplete: ${error.message ?: error.javaClass.simpleName}" }
            catch (error: LinkageError) { status = "Native runtime unavailable: ${error.message}" }
            finally {
                hasModel = ComputeProfile.entries.any { GemmaRuntime.available(getApplication(), it) }
                hasNpu = GemmaRuntime.available(getApplication(), ComputeProfile.NPU)                hasCpu = GemmaRuntime.available(getApplication(), ComputeProfile.CPU_GPU)
                busy = false
            }
        }
    }

    companion object {
        const val MODEL_BYTES = 387710976L
        const val MODEL_SHA256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
    }
}
