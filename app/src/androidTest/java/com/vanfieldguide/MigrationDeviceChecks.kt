package com.vanfieldguide

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object MigrationDeviceChecks {
    fun run(context: Context) {
        val name = "migration-${System.nanoTime()}.sqlite"
        val path = File(context.noBackupFilesDir, name)
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                db.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
                db.execSQL("CREATE TABLE pages (number INTEGER PRIMARY KEY, text TEXT NOT NULL, state TEXT NOT NULL)")
                db.execSQL("CREATE TABLE chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, page INTEGER NOT NULL, body TEXT NOT NULL, vector BLOB, fingerprint TEXT)")
                db.execSQL("INSERT INTO pages VALUES(13,'wiring diagram','text')")
                db.execSQL("INSERT INTO chunks(page,body,vector,fingerprint) VALUES(13,'wiring diagram',?,?)", arrayOf(ByteArray(768 * 4), StarterManual.FINGERPRINT))
                db.version = 1
            }
            BinderStore(context, name).use { store ->
                check(store.coverage().embedded == 1 && store.coverage().npuEmbedded == 0)
                val part = store.passages().single()
                check(store.passages(ComputeProfile.NPU).single().vector == null)
                val vector = FloatArray(768).also { it[0] = 1f }
                store.saveVector(part.id, vector, ComputeProfile.NPU)
                check(store.coverage().embedded == 1 && store.coverage().npuEmbedded == 1)
                store.savePage(13, "updated diagram", "text")
                check(store.coverage().embedded == 0 && store.coverage().npuEmbedded == 0)
            }
            context.deleteDatabase(path.path)
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                db.execSQL("CREATE TABLE photos(uri TEXT PRIMARY KEY,name TEXT NOT NULL,version TEXT NOT NULL,vector BLOB,fingerprint TEXT,error TEXT)")
                db.execSQL("INSERT INTO photos(uri,name,version,vector,fingerprint) VALUES('uri','photo','v1',?,?)", arrayOf(ByteArray(768 * 4), PhotoStore.FINGERPRINT))
                db.version = 1
            }
            PhotoStore(context, name).use { store -> check(store.coverage() == PhotoCoverage(1, 1, 0, 0)) }
        } finally { context.deleteDatabase(path.path) }
    }
}
