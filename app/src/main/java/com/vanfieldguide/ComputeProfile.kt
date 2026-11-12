package com.vanfieldguide

enum class ComputeProfile(val label: String, val fileName: String, val bytes: Long, val sha256: String) {
    NPU("Pixel NPU", "embeddinggemma-tensor-g5.litertlm", 559418036L,
        "96cdbae7b3dd6fa0f829fef2d0dd5bafc0179890d9bafbfe972db852e818d758"),
    CPU_GPU("CPU/GPU", "embeddinggemma.litertlm", 387710976L,
        "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0");
    val vectorColumn get() = if (this == NPU) "npu_vector" else "vector"
    val fingerprintColumn get() = if (this == NPU) "npu_fingerprint" else "fingerprint"
    val manualFingerprint get() = if (this == NPU) "eg2-440m:96cdbae7b3dd6fa0:litertlm018:tensorG5-dispatch230:768:runtime-prefix-v1:1100chars-overlap150:1024ctx" else StarterManual.FINGERPRINT
    val photoFingerprint get() = if (this == NPU) "eg2-440m:96cdbae7b3dd6fa0:litertlm018:tensorG5-dispatch230:768:runtime-query-v1:1024ctx:imageDecoder-srgb-1280-jpeg90-v1" else PhotoStore.FINGERPRINT
}

class NativeInferenceFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
data class BackendResult<T>(val value: T, val profile: ComputeProfile, val fallback: Boolean)

/** Only model/runtime failures trigger a retry; cancellation and data errors propagate. */
object BackendFallback {
    suspend fun <T> run(profiles: List<ComputeProfile>, failed: (ComputeProfile, NativeInferenceFailure) -> Unit = { _, _ -> },
                        operation: suspend (ComputeProfile) -> T): BackendResult<T> {
        var last: NativeInferenceFailure? = null
        for ((index, profile) in profiles.withIndex()) {
            try { return BackendResult(operation(profile), profile, index > 0) }
            catch (error: NativeInferenceFailure) { last = error; failed(profile, error) }
        }
        throw last ?: NativeInferenceFailure("No prepared model and matching index are available")
    }
}
