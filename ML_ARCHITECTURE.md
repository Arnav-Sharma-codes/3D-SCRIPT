# 3D Script Studio — Machine Learning Architecture Design

> **Status:** Design Phase — Implementation scheduled after UX validation  
> **Target:** Local, on-device ML model trained on user-supplied screenplays  
> **Goal:** Replace static heuristics with a learned narrative intelligence that improves the more scripts a writer feeds it.

---

## 1. Vision

Today, 3D Script Studio scores scenes using rule-based heuristics and Gemini AI prompts. The ML layer adds a **third intelligence tier** — a model trained *on the writer's own corpus* — so scores reflect the patterns and conventions of *their genre, style, and industry*.

```
Rule Engine  ──┐
Gemini AI    ──┼──► Score Fusion Layer ──► NarrativeTrack (3D + Cognitive)
ML Model     ──┘
```

---

## 2. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        3D SCRIPT STUDIO APP                          │
│                                                                     │
│  ┌────────────────┐   ┌────────────────┐   ┌─────────────────────┐  │
│  │ Script Editor  │   │  Viewport 3D   │   │  ML Training Studio │  │
│  │  (Fountain /   │   │  (Scene Graph) │   │  ← NEW WINDOW       │  │
│  │   PDF / FDX)   │   │                │   │                     │  │
│  └───────┬────────┘   └───────┬────────┘   └────────┬────────────┘  │
│          │                   │                      │               │
│          └─────────────── AppState ─────────────────┘               │
│                              │                                      │
└──────────────────────────────┼──────────────────────────────────────┘
                               │
              ┌────────────────▼──────────────────┐
              │          ENGINE  (JVM / Ktor)       │
              │                                    │
              │  ┌──────────┐  ┌────────────────┐  │
              │  │ Fountain │  │ NarrativeMetrics│  │
              │  │  Parser  │  │  (rule engine) │  │
              │  └────┬─────┘  └───────┬─────────┘  │
              │       │                │             │
              │  ┌────▼────────────────▼──────────┐  │
              │  │       Feature Extractor         │  │
              │  │  (SceneFeatureVector builder)   │  │
              │  └──────────────┬─────────────────┘  │
              │                 │                    │
              │  ┌──────────────▼─────────────────┐  │
              │  │       ML Inference Pipeline     │  │
              │  │  NarrativeTransformer.onnx      │  │
              │  └──────────────┬─────────────────┘  │
              │                 │                    │
              │  ┌──────────────▼─────────────────┐  │
              │  │       Score Fusion Layer        │  │
              │  │  (rule + Gemini + ML weighted)  │  │
              │  └─────────────────────────────────┘  │
              └───────────────────────────────────────┘
                               │
              ┌────────────────▼──────────────────┐
              │        ML Training Service         │
              │  (Python micro-service or ONNX     │
              │   runtime embedded in JVM)         │
              │                                    │
              │  • ScriptDataset storage           │
              │  • Feature pipeline                │
              │  • NarrativeTransformer training   │
              │  • ONNX model export               │
              └───────────────────────────────────┘
```

---

## 3. Data Layer

### 3.1 ScriptDataset

A local SQLite database (`~/.3dscript/ml/dataset.db`) that persists every script the user feeds to the trainer.

| Table              | Columns                                                                    |
|--------------------|----------------------------------------------------------------------------|
| `scripts`          | `id`, `title`, `genre`, `source_path`, `raw_text`, `ingested_at`           |
| `scene_features`   | `script_id`, `scene_index`, `feature_json`, `label_json`                   |
| `training_runs`    | `id`, `started_at`, `finished_at`, `epochs`, `loss`, `model_path`         |
| `model_versions`   | `id`, `run_id`, `onnx_path`, `accuracy`, `is_active`                       |

### 3.2 SceneFeatureVector

Each scene is encoded into a **fixed-length float array** of 32 dimensions:

```
Dims 0–2   : (x, y, z) normalised to [0,1]           — raw 3D position
Dims 3–5   : (Δx, Δy, Δz) from previous scene        — velocity vector
Dim  6     : deltaD (Euclidean movement)              — pacing delta
Dim  7     : narrativeTime (scene_i / scene_count)    — position in story
Dim  8     : recallRetentionPct / 100                 — cognitive recollection
Dim  9     : emotionalFluxPct / 100                   — cognitive perception
Dim  10    : divergenceGap / 10                       — expectation divergence
Dim  11    : subversionScore / 10                     — twist intensity
Dims 12–14 : one-hot act encoding (I, II, III)        — story structure position
Dims 15–17 : one-hot token type (Setup, Twist, Overload)
Dim  18    : isDeadZone flag (0 / 1)
Dim  19    : dialogueDensity (dialogue lines / total lines, estimated)
Dims 20–31 : TF-IDF bag-of-slugline-words (top 12 screenplay verbs)
```

### 3.3 Labels

The model is trained on two task heads simultaneously:

| Label              | Type        | Range   | Meaning                                                      |
|--------------------|-------------|---------|--------------------------------------------------------------|
| `plotScore`        | regression  | [0, 10] | Ground truth plot progression for this scene                 |
| `characterScore`   | regression  | [0, 10] | Ground truth character arc score                             |
| `emotionScore`     | regression  | [0, 10] | Ground truth emotional tension score                         |
| `pacingClass`      | 3-class     | {0,1,2} | 0=dead zone, 1=steady, 2=dynamic                             |
| `structuralRole`   | 5-class     | {0–4}   | Setup / Rising Action / Midpoint / Climax / Resolution       |

Initial labels are **bootstrapped from the existing rule + Gemini scores**, then refined as the user annotates scripts manually in the ML Training Studio.

---

## 4. Model Architecture — NarrativeTransformer

### 4.1 Architecture Diagram

```
Input Sequence: [Scene_1 ... Scene_N]  (N × 32 float features)
                        │
              ┌─────────▼──────────┐
              │  Linear Projection  │  32 → 128 dims
              │  + Positional Enc.  │  sinusoidal, scene-index based
              └─────────┬──────────┘
                        │
              ┌─────────▼──────────┐
              │  Transformer Enc.  │  3 layers × 4 heads × 128 dims
              │  (self-attention   │  dropout=0.1, FFN dim=256
              │   across scenes)   │
              └─────────┬──────────┘
                        │
           ┌────────────┴─────────────┐
           │  Per-scene output token  │  shape: N × 128
           │  (scene-level heads)     │
           └────────────┬─────────────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
  ┌──────▼──────┐ ┌─────▼─────┐ ┌─────▼──────┐
  │ Regression  │ │ Pacing    │ │ Structural │
  │ Head        │ │ Classifier│ │ Role Head  │
  │ (3 outputs: │ │ (3-class  │ │ (5-class   │
  │  x, y, z)  │ │  softmax) │ │  softmax)  │
  └─────────────┘ └───────────┘ └────────────┘
```

### 4.2 Why a Transformer?

- Scenes are a **sequence** — a scene at position 60% cannot be understood without knowing what came before.  
- Self-attention lets the model learn *long-range dependencies*: an Chekhov's gun planted in scene 3 affects the score of the callback in scene 47.  
- Small enough (< 2 M parameters) to train locally on a CPU in minutes.

### 4.3 Training Configuration

| Hyperparameter    | Default Value | Tunable |
|-------------------|---------------|---------|
| Layers            | 3             | Yes     |
| Attention heads   | 4             | Yes     |
| Hidden dim        | 128           | Yes     |
| FFN dim           | 256           | Yes     |
| Dropout           | 0.1           | Yes     |
| Sequence length   | 300 scenes    | Fixed   |
| Batch size        | 8 scripts     | Yes     |
| Learning rate     | 1e-4          | Yes     |
| Epochs            | 50 (default)  | Yes     |
| Optimizer         | AdamW         | Fixed   |
| Loss              | MSE (reg) + CrossEntropy (clf) fused | Fixed |

### 4.4 Score Fusion (Inference)

After training, 3 scores (rule, Gemini, ML) are blended per scene:

```
finalScore(x) = α·ruleScore(x) + β·geminiScore(x) + γ·mlScore(x)

Default weights: α=0.25, β=0.45, γ=0.30
Adjustable per session in Settings.
γ increases as more scripts are trained (trust grows with corpus size).
```

---

## 5. ML Training Pipeline

```
User adds scripts
       │
       ▼
┌──────────────────┐
│  Ingestion Queue │   Drag-and-drop / folder import
│  (Fountain, PDF, │   Each script parsed → SceneList
│   FDX, plain txt)│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Feature Extractor│   Runs existing NarrativeMetrics + CognitiveEngine
│                  │   Outputs SceneFeatureVector[N×32]
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Dataset Writer  │   Appends to SQLite scene_features table
│  (ScriptDataset) │   Deduplicates by content hash
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Training Run    │   Runs in background thread
│  NarrativeTransf.│   Emits epoch loss every N steps via StateFlow
│  (PyTorch via    │   User can pause / resume / cancel
│  TorchScript or  │
│  ONNX Runtime)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Model Exporter  │   Saves .onnx to ~/.3dscript/ml/models/
│                  │   Writes model_versions row to SQLite
│                  │   Swaps active model atomically
└──────────────────┘
```

---

## 6. ML Training Studio UI — New Window

### 6.1 Layout

```
┌───────────────────────────────────────────────────────────────────┐
│  🤖  ML TRAINING STUDIO                                   [×] [—] │
├──────────────────┬────────────────────────────────────────────────┤
│  SCRIPT LIBRARY  │  TRAINING CONTROL                              │
│                  │                                                │
│  [+ Import]      │  Corpus: 12 scripts · 1,847 scenes            │
│                  │                                                │
│  ✓ Tenet.fountain│  Model: NarrativeTransformer v3 (active)       │
│  ✓ Inception.pdf │  Last trained: 2 days ago                     │
│  ✓ Whiplash.txt  │                                                │
│  ✗ raw_draft.fdx │  [▶ Start Training]  [⏸ Pause]  [⏹ Stop]     │
│    (parse error) │                                                │
│  ...             │  Epoch  48 / 50  ████████████████░░  96%      │
│                  │  Train Loss:  0.0821   Val Loss: 0.0934        │
│                  │  ETA: ~2 min remaining                         │
│  [Clear All]     │                                                │
│                  ├────────────────────────────────────────────────┤
│                  │  PERFORMANCE CHART                             │
│                  │  (Loss curve: epoch vs train/val loss)         │
│                  │  [Line chart rendered in Compose Canvas]        │
│                  │                                                │
│                  │  SCORE FUSION WEIGHTS                         │
│                  │  Rule Engine  ━━━━━━━━━ 25%                   │
│                  │  Gemini AI    ━━━━━━━━━━━━━━━ 45%             │
│                  │  ML Model     ━━━━━━━━━━ 30%                  │
│                  │                                                │
│                  │  [Export Model]  [Reset to Default Weights]    │
└──────────────────┴────────────────────────────────────────────────┘
```

### 6.2 Key UI Composables

| Composable                  | Responsibility                                                   |
|-----------------------------|------------------------------------------------------------------|
| `MLStudioWindow`            | Root Compose window, owns `MLTrainingState`                     |
| `ScriptLibraryPanel`        | Drag-drop import, library list with parse status badges         |
| `TrainingControlPanel`      | Start/pause/stop, epoch progress bar, ETA                       |
| `LossCurveChart`            | Compose Canvas line chart: train loss + val loss per epoch      |
| `ScoreFusionSliders`        | Three sliders (α, β, γ) that always sum to 1.0                  |
| `ModelVersionSelector`      | Dropdown of saved model versions with accuracy ratings          |

---

## 7. Technology Stack

| Component                | Technology                        | Reason                                              |
|--------------------------|-----------------------------------|-----------------------------------------------------|
| Feature extraction       | Kotlin (existing engine)          | Reuses NarrativeMetrics + CognitiveEngine           |
| Dataset storage          | SQLite via `sqlite-jdbc`          | Embedded, no server, portable                       |
| Model training           | Python (PyTorch) micro-service    | Best ML ecosystem; local subprocess from JVM       |
| Model inference          | ONNX Runtime for JVM              | Runs exported .onnx natively in-process             |
| Training IPC             | Stdout/stdin JSON stream          | Simple, no network, no gRPC overhead               |
| UI                       | Kotlin Compose Multiplatform      | Consistent with existing app                        |

### Why Python for training?

Training a transformer efficiently requires PyTorch's autograd. Rather than implementing backprop in Kotlin, the engine spawns a local Python process (`python3 -m threescript.trainer`) that:

1. Reads features from SQLite
2. Trains the NarrativeTransformer
3. Exports to `model.onnx`
4. Streams JSON progress to stdout for the Compose UI to display live

**No internet required.** All training is local.

---

## 8. File Structure Plan

```
3D-SCRIPT-kotlin_chatgpt/
├── ML_ARCHITECTURE.md                   ← this document
│
├── ml/                                  ← new Gradle module
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/kotlin/com/threescript/ml/
│   │   │   ├── data/
│   │   │   │   ├── SceneFeatureVector.kt    ← 32-dim feature DTO
│   │   │   │   ├── ScriptDataset.kt         ← SQLite CRUD layer
│   │   │   │   └── TrainingRun.kt           ← run metadata model
│   │   │   ├── pipeline/
│   │   │   │   ├── FeatureExtractor.kt      ← scene → float[32]
│   │   │   │   ├── TrainingOrchestrator.kt  ← spawns Python subprocess
│   │   │   │   └── OnnxInferencePipeline.kt ← ONNX Runtime wrapper
│   │   │   └── fusion/
│   │   │       └── ScoreFusionLayer.kt      ← α·rule + β·gemini + γ·ml
│   │   └── test/kotlin/com/threescript/ml/
│   │       └── FeatureExtractorTest.kt
│
├── ml-python/                           ← PyTorch training process
│   ├── threescript/
│   │   ├── __init__.py
│   │   ├── trainer.py                   ← CLI entry point
│   │   ├── model.py                     ← NarrativeTransformer (PyTorch)
│   │   ├── dataset.py                   ← SQLite reader + DataLoader
│   │   └── export.py                    ← torch.onnx.export wrapper
│   └── requirements.txt                 ← torch, onnx, numpy, sqlite3
│
└── app/src/main/kotlin/com/threescript/app/
    └── ui/
        └── ml/
            ├── MLStudioWindow.kt        ← Compose window root
            ├── MLTrainingState.kt       ← state holder
            ├── ScriptLibraryPanel.kt    ← import + library list
            ├── TrainingControlPanel.kt  ← start/pause/stop + progress
            ├── LossCurveChart.kt        ← Canvas loss chart
            └── ScoreFusionSliders.kt    ← α/β/γ weight controls
```

---

## 9. Data Flow: End-to-End

```
1. User opens ML Training Studio window
2. Drags "Arrival.fountain" into Script Library panel
3. App calls engine /parse → gets List<ParsedScene>
4. FeatureExtractor maps each scene → SceneFeatureVector[32]
5. Labels bootstrapped from NarrativeMetrics + Gemini scores
6. ScriptDataset.insert(scriptId, features, labels) → SQLite
7. User clicks "▶ Start Training"
8. TrainingOrchestrator spawns:
       python3 -m threescript.trainer \
         --db ~/.3dscript/ml/dataset.db \
         --epochs 50 --lr 0.0001
9. trainer.py emits newline-delimited JSON progress:
       {"epoch":1,"train_loss":0.42,"val_loss":0.51,"eta_seconds":340}
10. TrainingOrchestrator parses stdout → updates MLTrainingState
11. LossCurveChart re-renders each epoch
12. At epoch 50, trainer.py exports model.onnx → model_versions row
13. OnnxInferencePipeline loads new model atomically
14. Next analysis run blends ML scores via ScoreFusionLayer
15. Scene Inspector shows "ML Model" badge alongside "Gemini AI" / "Rule Engine"
```

---

## 10. Phased Rollout

| Phase | Deliverable | Effort |
|-------|-------------|--------|
| **Phase 1 — Foundation** | `SceneFeatureVector`, `ScriptDataset` (SQLite), `FeatureExtractor`, `MLTrainingState`, `MLStudioWindow` skeleton UI | Medium |
| **Phase 2 — Python Bridge** | `TrainingOrchestrator` (subprocess), `trainer.py`, `model.py`, stdout streaming, ONNX export | Medium-High |
| **Phase 3 — Inference** | `OnnxInferencePipeline`, `ScoreFusionLayer`, Scene Inspector "ML" badge, Settings weight sliders | Medium |
| **Phase 4 — UX Polish** | LossCurveChart, ModelVersionSelector, manual label annotation view, draft comparison (before/after rewrite) | Low-Medium |

> **Start with Phase 1.** Once the data models compile and the UI window opens, Phase 2 is independently testable.

---

## 11. Privacy & Local-First Guarantee

- **No scripts leave the device.** Training runs entirely in a local Python process.
- **No telemetry.** The ML module has no network calls.
- **Model files stay local.** ONNX files stored in `~/.3dscript/ml/models/`.
- The user can delete the entire ML corpus (`~/.3dscript/ml/`) at any time from Settings.

---

*Document version: 1.0 · 3D Script Studio ML Architecture Design*
