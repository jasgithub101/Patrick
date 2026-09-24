# Patrick — Local Android Face Identification (Research Prototype)

An Android app that identifies a registered person from their face **entirely on-device**,
and links them to a **dummy** ABHA ID.

> **Status: early Phase 1. Nothing is built yet.** Toolchain installed; app scaffolding in
> progress. See [`PROJECT_STATE.md`](PROJECT_STATE.md) for the current state.
>
> This is a research prototype. It makes **no** accuracy, security or production-readiness
> claims, and it uses no real ABHA/ABDM integration and no real patient data.

## Why it is built this way

This is **1:N open-set identification** ("who in the gallery is this, if anyone?"), not 1:1
verification. The central requirement is therefore false-positive prevention:

> **The system never returns an identity just because it is the nearest face.**

Every attempt ends in one of three outcomes:

| Outcome | Meaning |
|---|---|
| `MATCH` | Score, margin over the best *different* person, and input quality all clear configured thresholds |
| `UNCERTAIN` | A candidate exists but confidence is insufficient, or input quality is poor → retry |
| `UNKNOWN` | No sufficiently strong candidate → offer registration |

Thresholds are configurable and **uncalibrated** in Phase 1.

## Architecture (planned for Phase 1)

```text
Camera (CameraX)
  → FaceDetector        SCRFD-500MF, ONNX Runtime       (replaceable)
  → FaceQualityChecker  size · blur · light · pose · face count
  → FaceAligner         5-point similarity transform → 112×112
  → FaceEmbeddingModel  MobileFaceNet w600k_mbf, 512-d (replaceable)
  → FaceRepository      Room/SQLite: Person, Embedding(+model version), Audit
  → FaceMatcher         cosine similarity, max over each person's embeddings
  → RecognitionDecisionEngine → MATCH / UNCERTAIN / UNKNOWN
```

Models come from InsightFace `buffalo_sc` (16 MB). **These weights are licensed for
non-commercial research only.**

## Project phases

| Phase | Question | Status |
|---|---|---|
| 1 | Can we make it work on-device? | **In progress** |
| 2 | How reliable is it? (image count, conditions) | Planned |
| 3 | Can we prevent wrong matches? (calibration) | Planned |
| 4 | Can it handle a large gallery? | Planned |
| 5 | Which model belongs on the phone? | Planned |
| 6 | Does it work on the intended (Indian) population? | Planned |
| 7+ | Sync, security, liveness, scale | Planned |

Each phase has its own report in [`docs/`](docs/).

## Build

Prerequisites (see [`ENVIRONMENT.md`](ENVIRONMENT.md) for exact versions): JDK 17 and the
Android SDK (platform 36, build-tools 36.0.0). No global environment variables are set on the
development machine, so export them per shell:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
export ANDROID_HOME="/c/Users/jassu/Android/Sdk"
```

Build instructions will be added once the app module builds. Until then this section is
intentionally incomplete.

## Documentation

| File | Contents |
|---|---|
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | Short current state, blockers, next step |
| [`docs/PHASE_1_REPORT.md`](docs/PHASE_1_REPORT.md) | Full Phase 1 history: decisions, problems, failed approaches |
| [`ENVIRONMENT.md`](ENVIRONMENT.md) | Every installation, classified pre-existing vs project-installed |
| [`CLEANUP.md`](CLEANUP.md) | How to remove everything this project added |
| [`CLAUDE.md`](CLAUDE.md) | Persistent instructions for Claude Code |

## Development tooling

[Graphify](https://github.com/Graphify-Labs/graphify) is used at development time for
codebase architecture and impact analysis. It lives in the project-local `.venv` and is
**never** a dependency of the Android app.
