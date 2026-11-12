package com.vanfieldguide

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DevicePhoto(val uri: String, val name: String, val version: String)
data class PhotoCoverage(val total: Int = 0, val indexed: Int = 0, val failed: Int = 0, val npuIndexed: Int = 0)

class PhotoStore(context: Context, databaseName: String = "photos.sqlite") : SQLiteOpenHelper(context, File(context.noBackupFilesDir, databaseName).path, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE photos(uri TEXT PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL, vector BLOB, fingerprint TEXT, error TEXT, npu_vector BLOB, npu_fingerprint TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE photos ADD COLUMN npu_vector BLOB")
            db.execSQL("ALTER TABLE photos ADD COLUMN npu_fingerprint TEXT")
        } else error("Unsupported photo migration")
    }

    // Refresh from MediaStore before indexing/search: removed or no-longer-authorized photos disappear.
    fun reconcile(photos: List<DevicePhoto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT uri,version FROM photos", null).use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
            }
            val current = photos.map { it.uri }.toSet()
            existing.keys.filterNot { it in current }.forEach { db.delete("photos", "uri=?", arrayOf(it)) }
            for (photo in photos) {
                if (existing[photo.uri] != photo.version) {
                    db.insertWithOnConflict("photos", null, ContentValues().apply {
                        put("uri", photo.uri); put("name", photo.name); put("version", photo.version)
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                } else db.update("photos", ContentValues().apply { put("name", photo.name) }, "uri=?", arrayOf(photo.uri))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun coverage(): PhotoCoverage = readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(SUM(CASE WHEN vector IS NOT NULL AND fingerprint=? THEN 1 ELSE 0 END),0), COALESCE(SUM(CASE WHEN error IS NOT NULL THEN 1 ELSE 0 END),0), COALESCE(SUM(CASE WHEN npu_vector IS NOT NULL AND npu_fingerprint=? THEN 1 ELSE 0 END),0) FROM photos",
        arrayOf(FINGERPRINT, ComputeProfile.NPU.photoFingerprint),
    ).use { it.moveToFirst(); PhotoCoverage(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3)) }
    fun pending(profile: ComputeProfile = ComputeProfile.CPU_GPU): List<DevicePhoto> = readableDatabase.rawQuery(
        "SELECT uri,name,version FROM photos WHERE ${profile.vectorColumn} IS NULL OR ${profile.fingerprintColumn} IS NULL OR ${profile.fingerprintColumn}!=? ORDER BY rowid", arrayOf(profile.photoFingerprint),
    ).use { c -> buildList { while (c.moveToNext()) add(DevicePhoto(c.getString(0), c.getString(1), c.getString(2))) } }
    fun save(photo: DevicePhoto, vector: FloatArray, profile: ComputeProfile = ComputeProfile.CPU_GPU) {
        require(PhotoRanking.valid(vector)) { "Invalid photo embedding" }
        val blob = ByteBuffer.allocate(768 * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { blob.putFloat(it) }
        writableDatabase.update("photos", ContentValues().apply {
            put(profile.vectorColumn, blob.array()); put(profile.fingerprintColumn, profile.photoFingerprint); putNull("error")
        }, "uri=? AND version=?", arrayOf(photo.uri, photo.version))
    }
    fun failed(photo: DevicePhoto) {
        writableDatabase.update("photos", ContentValues().apply { put("error", "Could not read or index this photo") }, "uri=?", arrayOf(photo.uri))
    }
    fun search(query: FloatArray, profile: ComputeProfile = ComputeProfile.CPU_GPU): List<String> {
        val ranker = PhotoRanking(query, 24)
        readableDatabase.rawQuery("SELECT uri,${profile.vectorColumn} FROM photos WHERE ${profile.fingerprintColumn}=? AND ${profile.vectorColumn} IS NOT NULL", arrayOf(profile.photoFingerprint)).use { c ->
            while (c.moveToNext()) {
                val blob = c.getBlob(1)
                if (blob.size != 768 * 4) continue
                val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
                ranker.add(c.getString(0), FloatArray(768) { buffer.float })
            }
        }
        return ranker.results()
    }
    fun clear() { writableDatabase.delete("photos", null, null) }

    companion object {
        const val FINGERPRINT = "eg2-440m:92dcbea108899e5d:litertlm018:cpu-gpu:768:runtime-query-v1:1024ctx:imageDecoder-srgb-1280-jpeg90-v1"
    }
}
