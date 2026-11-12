package com.vanfieldguide

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackendFallbackTest {
    private val choices = listOf(ComputeProfile.NPU, ComputeProfile.CPU_GPU)
    @Test fun npuIsPrimaryAndCpuIsNotCalledWhenItSucceeds() = runBlocking {
        val calls = mutableListOf<ComputeProfile>()
        val answer = BackendFallback.run(choices) { calls += it; "ok" }
        assertEquals(listOf(ComputeProfile.NPU), calls)
        assertEquals(ComputeProfile.NPU, answer.profile)
        assertFalse(answer.fallback)
    }
    @Test fun nativeFailureRetriesEntireOperationWithCpuProfile() = runBlocking {
        val calls = mutableListOf<ComputeProfile>()
        val answer = BackendFallback.run(choices) {
            calls += it
            if (it == ComputeProfile.NPU) throw NativeInferenceFailure("driver failed")
            "CPU index and query"
        }
        assertEquals(choices, calls)
        assertEquals(ComputeProfile.CPU_GPU, answer.profile)
        assertTrue(answer.fallback)
    }
    @Test fun cancellationAndStorageErrorsNeverTriggerFallback() {
        listOf(CancellationException("paused"), IllegalStateException("database error")).forEach { problem ->
            val calls = mutableListOf<ComputeProfile>()
            try {
                runBlocking { BackendFallback.run(choices) { calls += it; throw problem } }
                fail("Expected original error")
            } catch (error: Exception) { assertSame(problem, error) }
            assertEquals(listOf(ComputeProfile.NPU), calls)
        }
    }
    @Test fun everyBackendFailureIsReported() {
        val failures = mutableListOf<ComputeProfile>()
        assertThrows(NativeInferenceFailure::class.java) {
            runBlocking { BackendFallback.run(choices, failed = { profile, _ -> failures += profile }) { throw NativeInferenceFailure("unavailable") } }
        }
        assertEquals(choices, failures)
    }
}
