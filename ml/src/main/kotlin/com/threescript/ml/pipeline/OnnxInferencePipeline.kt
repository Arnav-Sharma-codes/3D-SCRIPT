package com.threescript.ml.pipeline

import com.threescript.ml.data.SceneFeatureVector

/**
 * OnnxInferencePipeline — runs NarrativeTransformer inference using ONNX Runtime.
 *
 * The ONNX model accepts a sequence of scene feature vectors and outputs
 * per-scene regression scores (x, y, z) plus classification logits.
 *
 * ONNX Runtime for JVM dependency (add to ml/build.gradle.kts):
 *   implementation("com.microsoft.onnxruntime:onnxruntime:1.17.0")
 *
 * Input tensor shape:  [1, N, 32]  (batch=1, N scenes, 32 features each)
 * Output tensors:
 *   "regression"       [1, N, 3]   (x, y, z scores)
 *   "pacing_logits"    [1, N, 3]   (Dead/Steady/Dynamic)
 *   "structure_logits" [1, N, 5]   (5 structural roles)
 */
class OnnxInferencePipeline(private val modelPath: String) {

    // Late-init: OrtEnvironment and OrtSession from onnxruntime dependency
    // private lateinit var env: OrtEnvironment
    // private lateinit var session: OrtSession

    /**
     * Load the ONNX model from disk. Call once at startup or after a new training run.
     * Thread-safe; call from a background coroutine.
     */
    fun load() {
        // TODO (Phase 3):
        // env = OrtEnvironment.getEnvironment()
        // session = env.createSession(modelPath, OrtSession.SessionOptions())
        throw NotImplementedError("Phase 3 — implement after ONNX Runtime dependency is added")
    }

    /**
     * Run inference on a sequence of SceneFeatureVectors.
     *
     * @param features  Ordered list of features for all scenes in one script
     * @return          Per-scene ML score triples (x, y, z) in [0, 10]
     */
    fun infer(features: List<SceneFeatureVector>): List<MLSceneScore> {
        // TODO (Phase 3):
        // 1. Pack features into a float[1][N][32] tensor
        // 2. Run session.run(inputs)
        // 3. Unpack "regression" output tensor → list of (x, y, z)
        throw NotImplementedError("Phase 3")
    }

    fun close() {
        // session.close(); env.close()
    }
}

/** ML model output for a single scene. */
data class MLSceneScore(
    val sceneIndex: Int,
    val x: Float,   // Predicted plot score [0, 10]
    val y: Float,   // Predicted character score [0, 10]
    val z: Float,   // Predicted emotion score [0, 10]
    val pacingClassId: Int,      // 0=Dead, 1=Steady, 2=Dynamic
    val structuralRoleId: Int,   // 0–4
    val confidence: Float,       // Mean softmax confidence across classification heads
)
