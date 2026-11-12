package com.vanfieldguide

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object GemmaRuntime {
    var preferNpu by mutableStateOf(true); private set
    var lastBackend by mutableStateOf("Ready · NPU preferred") ; private set
    var npuIssue by mutableStateOf<String?>(null); private set
    private val verified = mutableMapOf<String, Pair<Long, Long>>()
    fun initialize(context: Context) { preferNpu = context.getSharedPreferences("compute", 0).getBoolean("prefer_npu", true) }
    fun setNpuPreferred(context: Context, value: Boolean) {
        preferNpu = value; npuIssue = null
        context.getSharedPreferences("compute", 0).edit().putBoolean("prefer_npu", value).apply()
        lastBackend = if (value) "Ready · NPU preferred" else "Ready · CPU/GPU selected"
    }
    fun file(context: Context, profile: ComputeProfile) = File(context.noBackupFilesDir, profile.fileName)
    fun supported() = Build.SOC_MODEL.replace("_", " ").equals("Tensor G5", ignoreCase = true)
    fun available(context: Context, profile: ComputeProfile) = file(context, profile).isFile && (profile != ComputeProfile.NPU || supported())
    fun candidates(context: Context) = buildList {
        if (preferNpu && npuIssue == null && available(context, ComputeProfile.NPU)) add(ComputeProfile.NPU)
        if (available(context, ComputeProfile.CPU_GPU)) add(ComputeProfile.CPU_GPU)
    }
    fun resetVerification() { verified.clear(); npuIssue = null }
    suspend fun prepare(context: Context, vision: Boolean, needed: (ComputeProfile) -> Boolean, block: suspend (Session) -> Unit): String {
        val choices = candidates(context)
        require(choices.isNotEmpty()) { "Import a verified Gemma model first" }
        val notes = mutableListOf<String>()
        for (profile in choices) {
            try {
                if (needed(profile)) withProfile(context, profile, vision, block)
                notes += "${profile.label} ready"
            } catch (error: NativeInferenceFailure) {
                if (profile == ComputeProfile.NPU) npuIssue = error.message
                notes += "${profile.label} incomplete: ${error.message}"
            }
        }
        return notes.joinToString(" · ")
    }
    private fun verify(context: Context, profile: ComputeProfile) {
        val source = file(context, profile)
        val stamp = source.length() to source.lastModified()
        if (verified[source.path] == stamp) return
        check(source.length() == profile.bytes) { "${profile.label} model size mismatch" }
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == profile.sha256) { "${profile.label} model checksum mismatch" }
        verified[source.path] = stamp
    }
    suspend fun <T> run(context: Context, vision: Boolean, eligible: (ComputeProfile) -> Boolean = { true }, block: suspend (Session) -> T): BackendResult<T> {
        val choices = candidates(context).filter(eligible)
        val result = BackendFallback.run(choices, failed = { profile, error ->
            if (profile == ComputeProfile.NPU) npuIssue = error.message ?: "NPU unavailable"
        }) { profile -> withProfile(context, profile, vision, block) }
        lastBackend = if (result.profile == ComputeProfile.NPU) "Last run: Pixel NPU"
            else if (preferNpu) "Last run: CPU/GPU fallback" else "Last run: CPU/GPU"
        return result.copy(fallback = result.fallback || (preferNpu && result.profile == ComputeProfile.CPU_GPU))
    }
    suspend fun <T> withProfile(context: Context, profile: ComputeProfile, vision: Boolean, block: suspend (Session) -> T): T {
        currentCoroutineContext().ensureActive()
        val engine = native {
            verify(context, profile)
            val backend = if (profile == ComputeProfile.NPU) Backend.NPU(context.applicationInfo.nativeLibraryDir) else Backend.CPU()
            EmbeddingEngine(EmbeddingEngineConfig(modelPath = file(context, profile).path, backend = backend,
                visionBackend = if (vision) { if (profile == ComputeProfile.NPU) Backend.NPU(context.applicationInfo.nativeLibraryDir) else Backend.GPU() } else null,
                maxInputLength = 1024, cacheDir = context.cacheDir.path))
        }
        var failure: Throwable? = null
        try {
            native { engine.initialize() }
            return block(Session(profile, engine))
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            if (engine.isInitialized()) try { native { engine.close() } }
            catch (closeError: NativeInferenceFailure) {
                if (failure != null) failure.addSuppressed(closeError) else throw closeError
            }
        }
    }
    class Session(val profile: ComputeProfile, private val engine: EmbeddingEngine) {
        suspend fun embed(input: InputData): FloatArray {
            currentCoroutineContext().ensureActive()
            return native {
                engine.computeEmbedding(listOf(input)).embedding.also {
                    check(PhotoRanking.valid(it)) { "Invalid native embedding" }
                }
            }.also { currentCoroutineContext().ensureActive() }
        }
    }
    private inline fun <T> native(block: () -> T): T = try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw NativeInferenceFailure(error.message ?: "Native inference failed", error) }
        catch (error: LinkageError) { throw NativeInferenceFailure(error.message ?: "Native library unavailable", error) }
}
