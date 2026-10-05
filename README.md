# 3DSCRIPT — Native Kotlin / Compose Multiplatform

3D Script Studio analyzes screenplays scene by scene, mapping plot progression, character arc, and emotional flux into a time-ordered 3D trajectory.

## Architecture

```
3DSCRIPT-kotlin/
├── shared/    — Data contracts only (SceneBlock, VectorPoint, NarrativeTrack, API DTOs)
├── engine/    — Proprietary server-side mathematical engine (Ktor + Gemini REST)
└── app/       — Compose Multiplatform desktop UI (macOS native)
```

## Mathematical Pipeline

```
Script Text → NarrativeParser → VectorMapper (Gemini AI)
    → NarrativeMetrics (scene-to-scene movement, dead zones)
    → NarrativeTrack (one exact score point per scene, ordered by time)
```

Every parsed scene receives its own plot, character-arc, and emotional-flux scores. The app preserves those three scores as the plotted coordinates; the time axis orders and connects scene points without changing their 3D positions. Movement and pacing diagnostics compare adjacent scenes. The editor imports screenplay text and text-based PDFs; scanned PDFs need OCR before import.

### Axis Definitions

| Axis | Dimension | Range |
|------|-----------|-------|
| **X** | Plot Progression — external world-state change, goal advancement, structural pivots | 0–10 |
| **Y** | Character Development — internal arc, agency shifts, relationship dynamics | 0–10 |
| **Z** | Emotional Flux — tension, dread, joy, tonal contrast, audience affect | 0–10 |

## Build

```bash
# 1. Copy and configure environment
cp .env.example .env
# Add your Gemini API key, or set SCORER_PROVIDER=mock for offline use

# 2. Build engine JAR
./gradlew :engine:jar

# 3. Run the desktop app (development)
./gradlew :app:run

# 4. Package as macOS .dmg
./gradlew :app:packageDmg
```

## Privacy

- Engine binds to `127.0.0.1` only — never accessible from network
- Script text never leaves your machine except for Gemini AI calls (user-controlled, user's own API key)
- No telemetry, analytics, or cloud sync
- Use `provider=mock` for fully offline operation

## 🤖 AI-Native Engineering & Methodology

**3D Script Studio** was conceptualized, architected, and directed by **Arnav Sharma**, utilizing modern AI coding tools as an execution stack to achieve extreme development velocity.

* **Product Architecture, Vector Math & UX Design:** Arnav Sharma
* **AI Coding Assistants & Code Generation:** Antigravity & OpenAI Codex
* **Semantic Vector Scoring Engine:** Gemini API

This project serves as a case study in **AI-native software architecture**—translating a novel spatial screenplay thesis into a fully functional, multi-module Kotlin Multiplatform desktop application in record time.

## Requirements

- Java 21 (Temurin recommended)
- Kotlin 2.1+
- macOS 12+
- Google Gemini API key (optional — mock mode available)

## 📜 License & Intellectual Property Moat

This repository is officially deployed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

### Open Source Track
The public multiplatform UI framework and data contracts are open-source. Any independent creator, student, or developer modifying this software or interacting with its data routing models remotely over a network **MUST** make their complete modified repository architecture publicly available under the same AGPL-3.0 terms.

### Enterprise & Studio Track
The strict AGPL-3.0 provisions completely prohibit the extraction or integration of this codebase into closed, proprietary commercial software suites or internal cloud networks managed by media conglomerates or streaming platforms.

For commercial studio deployment, automated pre-production API access pipelines, or enterprise operations requiring a bypass of the open-source mandate, you must secure a proprietary license directly from the parent entity.

**For Enterprise Licensing & Commercial Clearances:** arnavsharmawk@gmail.com 
**Copyright © 2026 Arnav Sharma. All Rights Reserved.**

## Demo Video Link: 
https://drive.google.com/file/d/1B917sfSrLaLnZqM8wP6XTZ-2dZboAQkw/view?usp=drive_link

