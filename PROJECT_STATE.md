# PROJECT_STATE.md

Short, always-current state of the project. Updated continuously.

**Last updated:** 2026-09-24
**Current phase:** Phase 1 — on-device face identification prototype
**Current milestone:** M1 (project scaffolding) — M0 complete

Status vocabulary used throughout the docs:
`PLANNED` · `IMPLEMENTED` · `TESTED` · `MEASURED` · `OBSERVED` · `ASSUMED` · `DEFERRED`

---

## What is implemented?

| Item | Status |
|---|---|
| Development toolchain (JDK 17, Android SDK 36, emulator, adb) | `IMPLEMENTED` `TESTED` — versions verified by direct invocation |
| Git repository + `.gitignore` | `IMPLEMENTED` |
| Project documentation set + `CLAUDE.md` | `IMPLEMENTED` |
| Graphify 0.9.67 (project `.venv`, project-scoped skill) | `IMPLEMENTED` `TESTED` — used on demand (hooks removed); initial graph built |
| Gradle settings / wrapper config | `IMPLEMENTED` — not yet executed |
| Android app module | `PLANNED` |
| Face detection (SCRFD) | `PLANNED` |
| Face embedding (MobileFaceNet) | `PLANNED` |
| Room database | `PLANNED` |
| Similarity search | `PLANNED` |
| Decision engine (MATCH/UNCERTAIN/UNKNOWN) | `PLANNED` |
| Registration flow | `PLANNED` |
| Duplicate detection | `PLANNED` |
| Latency measurement | `PLANNED` |

## What is currently being worked on?

M1 — Android project scaffolding: Gradle module, `fetchModels` task, ONNX Runtime session
loading, and a one-off shape dump of both models to confirm tensor layout before any
decoding code is written.

## What works?

Only the toolchain so far. `adb 1.0.41`, `emulator 37.1.11.0`, `build-tools 36.0.0`,
`platforms;android-36`, and the `google_apis;x86_64` system image are all installed and
respond to version queries. **Nothing has been built or run yet.**

## What doesn't work?

Nothing is known broken — nothing is built yet. No AVD has been created, so the
webcam-backed camera path is `PLANNED` and entirely unverified.

## What decisions have been made?

See `docs/PHASE_1_REPORT.md` section 7 for the full record with rejected alternatives.
In brief:

- **Android, on-device only.** No cloud recognition.
- **Detector:** SCRFD-500MF (`det_500m.onnx`) via ONNX Runtime — chosen over ML Kit so the
  5-point landmarks match the convention ArcFace was trained on.
- **Embedder:** MobileFaceNet `w600k_mbf.onnx`, 512-d — chosen for on-device size (13.6 MB).
- **Both models from InsightFace `buffalo_sc.zip`** (16.1 MB total), fetched by a Gradle
  task rather than committed.
- **Toolchain:** command-line SDK, no Android Studio.
- **Test target:** emulator now, physical phone later.
- **Gallery population:** bulk enrolment from an LFW subset pushed via `adb`, because 100
  people cannot be photographed.
- **API level 36** (min 26).

## What are the current blockers?

| Blocker | Impact | Owner |
|---|---|---|
| First push to `origin` (public repo `jasgithub101/Patrick`) awaits user confirmation: commit metadata would publish the author email | Nothing is on GitHub yet | User decision |
| AVD not yet created; WHPX may need enabling from an elevated prompt (AMD CPU, so no HAXM) | Emulator testing cannot start | Next step |

Neither blocks M1.

## What should happen next?

1. Create the Android app module and Gradle build files.
2. Add the `fetchModels` task and verify `buffalo_sc.zip` extracts correctly.
3. Build a debug APK — first proof the toolchain actually compiles something.
4. Create the AVD with `hw.camera.front=webcam0` and boot it.
5. Load both ONNX models and **dump their input/output shapes** before writing any decoder.

## Important finding so far

The InsightFace pretrained weights chosen for Phase 1 are licensed for **non-commercial
research only** (`OBSERVED`, InsightFace README). Fine for this prototype. It blocks any real
deployment using these weights, so a replacement model is a hard requirement before
production.

## Honest limitations of the current state

Nothing has been measured. No accuracy, latency or recognition claim exists yet, and none
should be inferred from this document.
