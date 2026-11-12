package com.vanfieldguide

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File

/** Real SQLite persistence and access-revocation checks, isolated from the owner's database. */
class PhotoStoreDeviceTest : Instrumentation() {
    private var mode = "store"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode") ?: mode; start() }
    override fun onStart() {
        if (mode == "npu-app") { NpuAppDeviceChecks.run(this); return }
        if (mode == "scan") { ScanDeviceChecks.run(this); return }
        val name = "photo-test-${System.nanoTime()}.sqlite"
        var store = PhotoStore(targetContext, name)
        try {
            val first = DevicePhoto("content://test/1", "IMG_1.jpg", "v1")
            val second = DevicePhoto("content://test/2", "IMG_2.jpg", "v1")
            val vector = FloatArray(768).also { it[0] = 1f }
            store.reconcile(listOf(first, second))
            store.save(first, vector)
            store.close()
            store = PhotoStore(targetContext, name)
            check(store.coverage() == PhotoCoverage(2, 1, 0)) { "Completed embedding did not persist" }
            check(store.pending() == listOf(second)) { "Resume did not skip completed photo" }
            check(store.search(vector) == listOf(first.uri))
            store.reconcile(listOf(first.copy(version = "v2"), second))
            check(store.coverage().indexed == 0) { "Changed photo retained stale embedding" }
            store.save(second, vector)
            store.writableDatabase.execSQL("UPDATE photos SET fingerprint='incompatible'")
            check(store.search(vector).isEmpty()) { "Incompatible embeddings entered search" }
            check(store.pending().size == 2)
            store.save(second, vector)
            store.reconcile(listOf(first.copy(version = "v2")))
            check(store.search(vector).isEmpty()) { "Removed/revoked photo remained searchable" }
            store.failed(first)
            check(store.coverage().failed == 1)
            store.save(first.copy(version = "v2"), vector)
            check(store.coverage() == PhotoCoverage(1, 1, 0))
            check(store.search(vector, ComputeProfile.NPU).isEmpty()) { "CPU vector leaked into NPU search" }
            val other = FloatArray(768).also { it[1] = 1f }
            store.save(first.copy(version = "v2"), other, ComputeProfile.NPU)
            check(store.coverage() == PhotoCoverage(1, 1, 0, 1))
            check(store.pending(ComputeProfile.NPU).isEmpty())
            store.writableDatabase.execSQL("UPDATE photos SET npu_fingerprint='incompatible'")
            check(store.search(vector, ComputeProfile.NPU).isEmpty())
            check(store.search(vector) == listOf(first.uri)) { "NPU invalidation erased fallback" }
            store.save(first.copy(version = "v2"), other, ComputeProfile.NPU)
            store.reconcile(listOf(first.copy(version = "v3")))
            check(store.coverage() == PhotoCoverage(1, 0, 0, 0)) { "Source change did not invalidate both indexes" }
            store.clear()
            check(store.coverage() == PhotoCoverage())
            MigrationDeviceChecks.run(targetContext)
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: persistence, resume, edits, fingerprint mismatch, revoked access, retry, removal\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n") })
        } finally {
            store.close()
            targetContext.deleteDatabase(File(targetContext.noBackupFilesDir, name).path)
        }
    }
}
