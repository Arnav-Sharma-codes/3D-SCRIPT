package com.threescript.ml.data

import java.time.Instant

/**
 * ScriptDataset — local SQLite CRUD layer for the ML training corpus.
 *
 * All screenplays the user imports for training are persisted here. The dataset
 * is intentionally kept local-only: nothing leaves the device.
 *
 * Database location: ~/.3dscript/ml/dataset.db
 *
 * Schema (DDL generated on first open):
 *
 *   CREATE TABLE scripts (
 *     id           INTEGER PRIMARY KEY AUTOINCREMENT,
 *     title        TEXT NOT NULL,
 *     genre        TEXT,
 *     source_path  TEXT,
 *     content_hash TEXT UNIQUE,          -- SHA-256 to avoid duplicates
 *     scene_count  INTEGER,
 *     ingested_at  INTEGER               -- epoch seconds
 *   );
 *
 *   CREATE TABLE scene_features (
 *     id           INTEGER PRIMARY KEY AUTOINCREMENT,
 *     script_id    INTEGER REFERENCES scripts(id) ON DELETE CASCADE,
 *     scene_index  INTEGER NOT NULL,
 *     features     BLOB NOT NULL,        -- float[32] little-endian
 *     labels       TEXT NOT NULL         -- JSON-encoded SceneLabel
 *   );
 *
 *   CREATE TABLE training_runs (
 *     id           INTEGER PRIMARY KEY AUTOINCREMENT,
 *     started_at   INTEGER,
 *     finished_at  INTEGER,
 *     epochs       INTEGER,
 *     final_loss   REAL,
 *     model_path   TEXT
 *   );
 *
 *   CREATE TABLE model_versions (
 *     id           INTEGER PRIMARY KEY AUTOINCREMENT,
 *     run_id       INTEGER REFERENCES training_runs(id),
 *     onnx_path    TEXT NOT NULL,
 *     val_loss     REAL,
 *     accuracy_pct REAL,
 *     is_active    INTEGER DEFAULT 0,
 *     created_at   INTEGER
 *   );
 */
class ScriptDataset(private val dbPath: String) {

    // ── Script CRUD ───────────────────────────────────────────────────────────

    /** Insert a new script. Returns generated ID, or -1 if duplicate (by hash). */
    fun insertScript(entry: ScriptEntry): Long {
        // TODO: open JDBC connection, INSERT OR IGNORE, return last_insert_rowid()
        throw NotImplementedError("Phase 1 — implement with sqlite-jdbc")
    }

    fun getAllScripts(): List<ScriptEntry> {
        // TODO: SELECT * FROM scripts ORDER BY ingested_at DESC
        throw NotImplementedError("Phase 1")
    }

    fun deleteScript(id: Long) {
        // TODO: DELETE FROM scripts WHERE id=? (cascades to scene_features)
        throw NotImplementedError("Phase 1")
    }

    // ── Scene Feature CRUD ────────────────────────────────────────────────────

    fun insertFeatures(vectors: List<SceneFeatureVector>, labels: List<SceneLabel>) {
        // TODO: batch INSERT into scene_features
        throw NotImplementedError("Phase 1")
    }

    fun getFeaturesForScript(scriptId: Long): List<Pair<SceneFeatureVector, SceneLabel>> {
        // TODO: SELECT features, labels WHERE script_id=?
        throw NotImplementedError("Phase 1")
    }

    /** Returns ALL features across the entire corpus for training. */
    fun getAllFeatures(): List<Pair<SceneFeatureVector, SceneLabel>> {
        // TODO: SELECT features, labels FROM scene_features (paginated in future)
        throw NotImplementedError("Phase 1")
    }

    // ── Training Run CRUD ─────────────────────────────────────────────────────

    fun startTrainingRun(): Long {
        // TODO: INSERT training_runs (started_at=now, epochs=0)
        throw NotImplementedError("Phase 1")
    }

    fun finishTrainingRun(runId: Long, epochs: Int, finalLoss: Double, modelPath: String) {
        // TODO: UPDATE training_runs SET finished_at=now, epochs, final_loss, model_path
        throw NotImplementedError("Phase 1")
    }

    // ── Model Version CRUD ────────────────────────────────────────────────────

    fun registerModelVersion(runId: Long, onnxPath: String, valLoss: Double, accuracyPct: Double): Long {
        // TODO: INSERT model_versions; deactivate all others; activate this one
        throw NotImplementedError("Phase 1")
    }

    fun getActiveModelPath(): String? {
        // TODO: SELECT onnx_path FROM model_versions WHERE is_active=1 LIMIT 1
        throw NotImplementedError("Phase 1")
    }

    fun getModelVersions(): List<ModelVersion> {
        // TODO: SELECT * FROM model_versions ORDER BY created_at DESC
        throw NotImplementedError("Phase 1")
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    fun getCorpusStats(): CorpusStats {
        // TODO: SELECT count(DISTINCT script_id), count(*) FROM scene_features
        throw NotImplementedError("Phase 1")
    }
}

/** Metadata row for a script in the training corpus. */
data class ScriptEntry(
    val id: Long = 0,
    val title: String,
    val genre: String? = null,
    val sourcePath: String? = null,
    val contentHash: String,
    val sceneCount: Int,
    val ingestedAt: Instant = Instant.now(),
)

/** A saved and versioned ONNX model. */
data class ModelVersion(
    val id: Long,
    val runId: Long,
    val onnxPath: String,
    val valLoss: Double,
    val accuracyPct: Double,
    val isActive: Boolean,
    val createdAt: Instant,
)

/** High-level corpus statistics shown in the Training Studio. */
data class CorpusStats(
    val scriptCount: Int,
    val sceneCount: Int,
    val annotatedSceneCount: Int,
    val lastIngestedAt: Instant?,
)
