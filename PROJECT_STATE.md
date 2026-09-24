# PROJECT_STATE.md

Short, always-current state of the project. Updated continuously.

**Last updated:** 2026-09-24
**Current phase:** Phase 1 — on-device face identification prototype
**Current milestone:** M2 (vertical slice) — M0 and M1 complete

Status vocabulary used throughout the docs:
`PLANNED` · `IMPLEMENTED` · `TESTED` · `MEASURED` · `OBSERVED` · `ASSUMED` · `DEFERRED`

---

## What is implemented?

| Item | Status |
|---|---|
| Development toolchain (JDK 17, Android SDK 36, emulator, adb) | `IMPLEMENTED` `TESTED` — versions verified by direct invocation |
| Git repository + GitHub remote (public `jasgithub101/Patrick`, noreply author email) | `IMPLEMENTED` `TESTED` — pushed, in sync |
| Project documentation set + `CLAUDE.md` | `IMPLEMENTED` |
| Graphify 0.9.67 (project `.venv`, project-scoped skill) | `IMPLEMENTED` `TESTED` — used on demand (hooks removed); initial graph built |
| Gradle build (wrapper 8.14.3, AGP 8.13.2, Kotlin 2.2.20) + `fetch<Variant>Models` task | `IMPLEMENTED` `TESTED` — `assembleDebug` succeeds; model checksums verified |
| Android app module (M1 model-inspector screen) | `IMPLEMENTED` `TESTED` on emulator |
| AVD `patrick_api36` (webcam0 front camera, WHPX) | `IMPLEMENTED` `TESTED` — boots, app installs and runs |
| Face detection (SCRFD) | `PLANNED` |
| Face embedding (MobileFaceNet) | `PLANNED` |
| Room database | `PLANNED` |
| Similarity search (brute-force cosine, per-person max / mean-top-2, model-version guard) | `IMPLEMENTED` `TESTED` (10 JVM unit tests) — not yet wired to the app |
| Decision engine (MATCH/UNCERTAIN/UNKNOWN) | `IMPLEMENTED` `TESTED` (12 JVM unit tests) — not yet wired to the app |
| Registration flow | `PLANNED` |
| Duplicate detection | `PLANNED` |
| Latency measurement | `PLANNED` |

## What is currently being worked on?

**Where:** locally on the laptop. A cloud-session move was attempted, but the account has no
cloud environment, and the user chose to continue locally.

M2 — vertical slice: CameraX capture, SCRFD decoding (layout now confirmed, see Phase 1
report E1), 5-point alignment, embedding, and wiring the tested matcher and decision engine.

## What works?

- The app builds and runs on the emulator. Both ONNX models load and execute on-device
  (`MEASURED` on the emulator: detector warm run 181 ms at 640x640, embedder 21 ms; these are
  **not** phone figures).
- Matcher + decision engine pass 22/22 JVM unit tests.
- **No face has been detected or recognised yet**: no camera, no detector decoding.

## What doesn't work?

Nothing known broken. The webcam-backed camera path is configured but not yet exercised.

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
| No physical Android phone (user is asking their professor) | Latency cannot be measured on real hardware; emulator numbers are not representative | User |

It does not block M2-M5.

## What should happen next?

1. CameraX preview + still capture (front camera = webcam on the emulator).
2. SCRFD decoder + NMS in Kotlin, bound by output index (layout in Phase 1 report E1).
3. 5-point Umeyama alignment to 112x112, then embedding.
4. Wire into the tested matcher and decision engine with a tiny in-memory gallery.
5. First webcam session with the user (the first point where their involvement is needed).

## Important finding so far

The InsightFace pretrained weights chosen for Phase 1 are licensed for **non-commercial
research only** (`OBSERVED`, InsightFace README). Fine for this prototype. It blocks any real
deployment using these weights, so a replacement model is a hard requirement before
production.

## Honest limitations of the current state

Nothing has been measured. No accuracy, latency or recognition claim exists yet, and none
should be inferred from this document.
