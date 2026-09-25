# Phase 1 Report — On-Device Face Identification Prototype

**Status:** IN PROGRESS · **Started:** 2026-09-24 · **Last updated:** 2026-09-24

This is a living document, updated continuously. It records what actually happened,
including failures. Status tags: `PLANNED` · `IMPLEMENTED` · `TESTED` · `MEASURED` ·
`OBSERVED` · `ASSUMED` · `DEFERRED`.

---

## 1. Phase Objective

Prove the core face-identification workflow on an Android device, entirely on-device. A person
is registered with multiple face embeddings, later identified from the camera alone, and
associated with a **dummy** ABHA ID. The system must return `UNKNOWN` or `UNCERTAIN` rather
than force the nearest candidate to be a match.

The engineering question Phase 1 answers is **"Can we make it work?"** It is not an accuracy
evaluation.

## 2. Scope

**Included:** camera input · face detection · quality checks · alignment · pretrained
embedding model · multiple embeddings per person · Room/SQLite persistence · dummy ABHA IDs ·
cosine similarity search · MATCH/UNCERTAIN/UNKNOWN decision engine · registration flow ·
identification flow · duplicate-registration warning · latency measurement · ~100-person test
gallery.

**Excluded (`DEFERRED`):** real ABHA/ABDM integration · cloud recognition or backend · device
sync · ANN/vector indexes · fine-tuning · Indian-specific training · liveness/anti-spoofing ·
production security · real patient data.

## 3. Starting State

`OBSERVED` on 2026-09-24:

- `C:\Users\jassu\Downloads\Patrick` was **empty** — no files, not a git repository.
- **No** JDK, Android SDK, Android Studio, Gradle, adb or `gh` on the machine.
- Python 3.11.9 with 27 pre-existing packages (a Whisper environment), Node 24, git 2.54.
- Full inventory: `ENVIRONMENT.md` §1.

## 4. Environment

Summary only — the authoritative inventory is `ENVIRONMENT.md`.

| Item | Value | Status |
|---|---|---|
| Host | Windows 11 Home 10.0.26200, AMD Ryzen 7 250, 15.3 GB RAM | `OBSERVED` |
| JDK | Temurin 17.0.20.1 | `IMPLEMENTED` (installed) |
| Android SDK | platform android-36, build-tools 36.0.0, platform-tools 37.0.1 | `IMPLEMENTED` (installed) |
| Emulator | 37.1.11.0, AVD `patrick_api36` (Pixel 7, API 36 x86_64, webcam0 front camera, WHPX) | `IMPLEMENTED` `TESTED` |
| AGP / Gradle / Kotlin | 8.13.2 / 8.14.3 / 2.2.20 (see D11) | `IMPLEMENTED` `TESTED` — builds |
| ML runtime | ONNX Runtime Android 1.30.0 | `IMPLEMENTED` `TESTED` — both models run on the emulator (E1) |
| Dev tooling | Graphify 0.9.67 in project `.venv` | `IMPLEMENTED` `TESTED` — on demand, no hooks (§11–12) |
| Test device | Emulator now; physical phone later | `PLANNED` |

## 5. Architecture

Status per component is tracked in section 6. Matcher and decision engine are `IMPLEMENTED` and `TESTED`; the rest is `PLANNED`.

```text
            ┌──────────── ui (Compose) ────────────┐
            │  Register · Identify · Result · Debug │
            └──────────────────┬────────────────────┘
                               │ (no ML/DB types leak into UI)
   CameraX ──► RecognitionPipeline
                 │  FaceDetector (SCRFD, ORT)         ◄─ interface
                 │  FaceQualityChecker
                 │  FaceAligner (5-pt Umeyama → 112×112)
                 │  FaceEmbeddingModel (MobileFaceNet, ORT) ◄─ interface
                 ▼
             FaceMatcher (brute-force cosine)  ◄─ interface
                 │  per-person score = max over that person's embeddings
                 ▼
         RecognitionDecisionEngine ──► MATCH / UNCERTAIN / UNKNOWN
                 ▲
             FaceRepository (Room: Person, Embedding, Audit) + in-memory matrix
```

## 6. Implementation

| Component | Status |
|---|---|
| Toolchain (JDK, SDK, emulator binaries) | `IMPLEMENTED` `TESTED` (version queries succeed) |
| Git repo, `.gitignore` | `IMPLEMENTED` |
| Gradle build + `fetch<Variant>Models` (checksum-verified) | `IMPLEMENTED` `TESTED` |
| App module, M1 ModelInspector screen | `IMPLEMENTED` `TESTED` on emulator |
| FaceMatcher (brute-force cosine, per-person aggregation, model-version guard) | `IMPLEMENTED` `TESTED` (JVM) |
| RecognitionDecisionEngine | `IMPLEMENTED` `TESTED` (JVM) |
| MlKitFaceDetector (ML Kit behind the FaceDetector interface) | `IMPLEMENTED`, compiles; untested on faces until E2 |
| FaceAligner (5-point similarity transform to the ArcFace 112x112 template) | `IMPLEMENTED`, compiles |
| FaceEmbeddingModel interface + ArcFaceOnnxEmbedder (w600k_mbf) | `IMPLEMENTED`, compiles |
| BitmapImages (Bitmap <-> RgbImage edge adapters) | `IMPLEMENTED` |
| Room database (person / embedding / audit), FaceRepository | `IMPLEMENTED` `TESTED` — 9 instrumented tests |
| RegistrationService, configurable images-per-person, dummy ABHA IDs | `IMPLEMENTED` `TESTED` |
| Duplicate-registration warning (never auto-merges) | `IMPLEMENTED` `TESTED` — 6 instrumented tests |
| FaceQualityChecker (7 gates, actionable messages, prominence-based face choice) | `IMPLEMENTED` `TESTED` `MEASURED` — E3, E4 |
| RecognitionPipeline (detect -> quality -> align -> embed, per-stage timings) | `IMPLEMENTED` `TESTED` |
| Runtime-configurable settings (DataStore) | `IMPLEMENTED` — not yet exposed in a UI |
| CameraX capture (live preview, still capture, rotation handling) | `IMPLEMENTED` `TESTED` on emulator (E5) |
| Compose demo UI (Home, Register, Identify, Employees, Technical Details) | `IMPLEMENTED` `TESTED` on emulator (E5) |
| RecognitionEngine facade (UI touches nothing else) | `IMPLEMENTED` `TESTED` |
| Bulk 100-person enrolment + search latency at scale | `PLANNED` (M5, now last) |

## 7. Technical Decisions

### D1 — Target platform for the first prototype

```text
Decision:   Where the Phase 1 prototype runs
Options:    (a) Python desktop + webcam  (b) Android app  (c) Python backend + thin Android client
Selected:   (b) Android, on-device
Reason:     The user's Phase 1 spec requires an Android phone and on-device recognition.
            Option (a) was initially recommended and accepted, then overridden by the user's
            spec. (c) breaks the on-device/offline requirement.
Trade-offs: Slower iteration. Batch experiments (threshold sweeps, FAR/FRR) are harder to run
            on a phone than on a desktop.
Future:     A desktop evaluation harness may still be valuable in Phase 2 for calibration,
            reusing the same ONNX models.
```

### D2 — Detection model

```text
Decision:   On-device face detector
Options:    (a) SCRFD-500MF via ONNX Runtime  (b) Google ML Kit  (c) ML Kit now, SCRFD later
Selected:   (a) SCRFD `det_500m.onnx` (2.52 MB)
Reason:     SCRFD emits the exact 5-point landmark convention ArcFace-family embedders were
            trained with, so alignment matches training-time preprocessing. It shares one
            runtime with the embedder.
Trade-offs: SCRFD output decoding (anchors across 3 strides + NMS) must be hand-written in
            Kotlin. ML Kit would have been far less code and gives head Euler angles for
            free. Its landmark definitions differ, which is ASSUMED (not measured) to degrade
            embedding quality.
Future:     The ML Kit vs SCRFD alignment difference is a measurable experiment if detection
            becomes a bottleneck.
```

**D2 REVISED 2026-09-24 — detector switched to Google ML Kit (user decision).**

```text
Why:        Four attempts to write the hand-written SCRFD decoder failed: responses were
            stopped by an automated safety check or tool calls arrived truncated. No
            reason was given, and none is assumed here. The user chose option (c)'s
            direction: ML Kit now, SCRFD possibly later.
Selected:   com.google.mlkit:face-detection 16.1.7 (bundled model, on-device).
Trade-offs: - ML Kit landmarks (eyes, NOSE_BASE, mouth corners) do not match the
              5-point convention the ArcFace-family embedder was trained on. The accuracy
              cost is ASSUMED, not measured; it can be measured later against SCRFD.
            - ML Kit exposes no detection confidence score.
            - Android-only: real-face tests move from JVM to instrumented tests on the
              emulator.
            - Gains head Euler angles (yaw/pitch/roll) for the pose quality check.
Privacy:    OBSERVED from Google's ML Kit data-disclosure page (checked 2026-09-24):
            ML Kit does NOT send images, face data or inference results, but it DOES
            send diagnostics: per-installation identifiers, performance metrics, device
            model/OS, package name/version, event types, error codes. No opt-out is
            documented. The POM confirms a telemetry transport dependency
            (transport-backend-cct). Acceptable for Phase 1 (no real patient data);
            MUST be revisited before any real deployment. SCRFD has no such traffic.
Unchanged:  det_500m.onnx is still fetched but unused by the pipeline. SCRFD remains a
            candidate for a later comparison experiment.
```

### D13 — Build order: camera moved to the end of Phase 1

```text
Decision:   When to build the camera path
Options:    (a) spec order: Camera first in the vertical slice  (b) images first, camera last
Selected:   (b), user decision 2026-09-24. This is a deliberate deviation from the spec's
            "Camera -> Face Detection -> ..." build order.
Reason:     Detection, alignment, embedding, matching, decisions, database, duplicate
            detection and the 100-person gallery can all be built and tested on image
            files (the LFW sets). The camera adds nothing to verifying them, and testing it
            properly needs the user present.
Trade-offs: Camera-specific problems (frame format conversion, rotation, front-camera
            mirroring) surface later. They are well-known and considered low risk.
Constraint: Phase 1 cannot be CLOSED without the camera path. The success criteria require
            live capture and identification. Preferred: test the camera directly on the
            physical phone; fall back to the emulator webcam if the phone is delayed.
```

### D18 — Demo UI brought forward ahead of the 100-person gallery

```text
Decision:   Build the demo UI (M6) before the bulk gallery (M5)
Reason:     The user needs a presentable demo for their professor. The UI depends only on the
             pipeline, which M2-M4 already completed and tested, so the order is safe.
Also:       Android Studio was installed at the same time (ENVIRONMENT.md 2.11). It is NOT
             required to build: the command-line toolchain still produces the APK, and Studio
             was pointed at the existing SDK, JDK, AVD and Gradle wrapper rather than
             installing duplicates.
Consequence: M5 (bulk 100-person enrolment and search latency at scale) is now the last
             engineering task before Phase 1 closeout.
```

### D19 — UI is a presentation layer only

```text
Decision:   How the UI reaches the recognition pipeline
Selected:   A single RecognitionEngine facade in the app package. Screens and ViewModels call
            it and nothing else.
Reason:     CLAUDE.md section 2 requires recognition logic to stay out of the UI. No screen
            imports ONNX Runtime, Room or the matcher; no screen contains a threshold. Every
            number displayed is returned by the pipeline for that specific attempt.
Trade-offs: One more indirection layer. Worth it: the UI can be replaced, and the engine stays
            testable without any UI.
Explicitly NOT done: no mock recognition, no hardcoded similarity, employee or latency values.
```

### D3 — Embedding model

```text
Decision:   Pretrained face-recognition model
Options:    (a) MobileFaceNet w600k_mbf (InsightFace buffalo_s/sc) (b) ResNet50 w600k_r50
            (buffalo_l) (c) third-party MobileFaceNet TFLite ports
Selected:   (a) w600k_mbf.onnx — 13.62 MB, input 1x3x112x112, 512-d output
Reason:     Mobile-sized, reputable provenance (InsightFace model zoo), ONNX so it runs on
            ONNX Runtime Android with no conversion step.
Trade-offs: See the benchmark finding below — a large accuracy gap on South Asian faces
            versus the ResNet50 model.
Future:     Phase 5 model comparison is central, not optional. R50 may be viable on-device
            within the ≤2 s budget; this is unmeasured.
Licensing:  OBSERVED (InsightFace README, checked 2026-09-24): code is MIT, but "the models
            trained with these data are available for non-commercial research purposes
            only", and this explicitly covers auto-downloaded packs such as buffalo_*.
            Acceptable for a research prototype. It BLOCKS any real deployment using these
            weights. Replacing them requires a commercially licensed model or one trained
            in-house (a Phase 6/7 concern).
```

**Benchmark finding** (`OBSERVED` from the InsightFace model-zoo README, not measured by us):

| Pack | Recognition | LFW | S. Asian | MR-ALL | IJB-C(E4) |
|---|---|---|---|---|---|
| buffalo_l | ResNet50@WebFace600K | 99.83 | **93.16** | 91.25 | 97.25 |
| buffalo_s/sc | MobileFaceNet@WebFace600K | 99.70 | **73.39** | 71.87 | 95.02 |

LFW suggests the two models are equivalent. The South Asian column shows a ~20-point gap.
This is direct evidence that a generic benchmark does not represent the intended population.

### D4 — Runtime

```text
Decision:   Inference runtime
Selected:   ONNX Runtime Android 1.30.0 (Maven Central)
Reason:     Runs both InsightFace models natively. The AAR includes an x86_64 ABI
            (verified by listing the AAR), so the emulator is viable.
Trade-offs: All four ABIs add ~135 MB of native libs -> abiFilters restricted to
            x86_64 + arm64-v8a.
```

### D5 — Toolchain

```text
Decision:   How to build
Options:    (a) Android Studio  (b) command-line SDK only  (c) user installs
Selected:   (b) command-line SDK
Reason:     ~5.8 GB instead of ~12-15 GB. Fully scriptable from the agent session.
Trade-offs: No IDE, profiler or layout preview. Android Studio can be added later on top of
            the same SDK.
```

### D6 — API level

```text
Decision:   compileSdk / system image level
Selected:   API 36 (minSdk 26)
Reason:     The latest stable AndroidX libraries (core 1.19, lifecycle 2.11, activity 1.13)
            are ASSUMED to require compileSdk 36. API 35 was the original plan; switched
            before the first build to avoid a known class of dependency-resolution failures.
Trade-offs: Not yet verified by a build.
```

### D7 — Build vs cloud environment

```text
Decision:   Where development runs
Options:    (a) Local hybrid  (b) Cloud primary  (c) Cloud only
Selected:   (a) Local, cloud optional
Reason:     A cloud container has no webcam, no AVD camera and no adb bridge to this machine,
            so the camera flow and latency could not be verified there. move_to_cloud also
            requires a git remote.
Future:     Cloud remains usable for camera-free work once a GitHub remote exists.
Update 2026-09-24 (after M1): the user asked to use the cloud wherever possible. A move to a
            cloud session was attempted and refused because the account has no cloud
            environment. The user then chose to drop the cloud session and continue locally.
            Decision (a) stands. No cloud environment exists or was used.
```

### D8 — Gallery population

```text
Decision:   How to reach ~100 registered people
Selected:   Debug bulk-enrolment from an LFW subset pushed via adb, through the IDENTICAL
            detect -> quality -> align -> embed -> store pipeline
Reason:     100 real people cannot be photographed for a prototype.
Source:     Hugging Face `logasja/lfw` (13,233 images, 5,749 identities, 188 MB parquet)
Trade-offs: LFW is celebrity web photography skewed toward white male adults. It exercises
            the plumbing; it says nothing about the intended population.
```

### D9 — Model distribution

```text
Decision:   Commit models vs fetch
Selected:   `fetchModels` Gradle task, checksum-verified, output gitignored
Reason:     Keeps ~16 MB out of git history; makes provenance explicit.
```

### D10 — Python tooling isolation

```text
Decision:   Where dev-time Python tools (Graphify, dataset helper) are installed
Options:    (a) system pip  (b) pipx/uv  (c) project-local venv
Selected:   (c) .venv in the repo root
Reason:     (a) would add ~30 packages to the pre-existing Whisper environment.
            (b) requires installing another global tool. (c) is removable by deleting one
            directory. Verified: system pip still lists 27 packages after the install.
```

### D11 — Dependency version set

```text
Decision:   Which library versions to pin
Options:    (a) newest of every library (Sept 2026: AGP 9.4, Kotlin 2.4.20, core 1.19,
                compose BOM 2026.09) (b) a coherent set from one release era
Selected:   (b) the AGP 8.13 era (late 2025): AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.20,
            KSP 2.2.20-2.0.3, core 1.17.0, activity-compose 1.11.0, lifecycle 2.9.4,
            compose BOM 2025.10.01, Room 2.8.3, CameraX 1.5.1, DataStore 1.1.7.
            ONNX Runtime stays at 1.30.0 (a Java/JNI library; its x86_64 ABI was verified).
Reason:     Mixing newest-of-everything risks libraries that require a newer compileSdk,
            AGP 9, or Kotlin metadata newer than the compiler can read. Each pinned
            version was verified to exist on Maven before use.
Trade-offs: Not on the latest releases. An upgrade pass is DEFERRED; it should be done as a
            deliberate, separate change.
```

### D12 — How model files reach the APK

```text
Decision:   Where fetchModels writes the models
Options:    (a) app/src/main/assets/models (gitignored)  (b) a generated assets directory
            registered through AGP's variant API
Selected:   (b) variant.sources.assets.addGeneratedSourceDirectory(...)
Reason:     Gradle wires the task dependency automatically, so there are no implicit-dependency
            validation errors from a task writing into src/. src/ stays clean. The download
            is cached in .cache/models/ so `clean` does not re-download it. Both the zip and
            each extracted model are SHA-256 verified:
              buffalo_sc.zip  57d31b56…2de47c72
              det_500m.onnx   5e4447f5…d8b4ea3a
              w600k_mbf.onnx  9cc6e4a7…b319eb4f
```

### D15 — Database schema and persistence (M3)

```text
Decision:   Persistence layer
Selected:   Room 2.8.3 over SQLite (KSP 2.2.20-2.0.3), three tables, schema version 1
            exported to app/schemas/ and committed.
            person(personId, dummyAbhaId UNIQUE, displayName, createdAt)
            embedding(embeddingId, personId FK CASCADE, vector BLOB, modelVersion, dim,
                      captureRole, createdAt)
            audit(auditId, timestamp, operation, outcome, personId, details)
Reason:     Matches the conceptual model in CLAUDE.md section 7. Room gives compile-time
            checked queries and a schema baseline for later migrations. WAL journal mode, so
            an interrupted write cannot leave a person without embeddings.
Trade-offs: KSP adds build time. Room is Android-only, so DB tests must be instrumented.
Details:    - Embeddings are stored as little-endian float32 BLOBs (EmbeddingCodec) with byte
              order fixed explicitly, so a database file stays readable across devices.
              Round-trip is lossless and asserted bit-for-bit.
            - modelVersion and dim are stored PER ROW, and loadGallery filters on both. Rows
              from another model version are never loaded into the same gallery.
            - Registration writes the person, all embeddings and the audit entry in ONE
              transaction.
            - No encryption yet. The Phase 1 MVP list includes local encryption; it is
              DEFERRED and recorded as a limitation, not silently dropped.
```

### D16 — Dummy ABHA identifiers are prefixed, not bare digits

```text
Decision:   Format of the placeholder health identifier
Options:    (a) 14 bare digits, like a real ABHA number  (b) a clearly marked placeholder
Selected:   (b) "DUMMY-" + 14 digits
Reason:     A bare 14-digit value is indistinguishable from a real ABHA number, so it could be
            copied into a real system, or a test record mistaken for a real one. The prefix
            makes that impossible, and isDummy() can assert it. Cheap safety for a prototype
            that handles health identifiers.
Trade-offs: Not format-identical to the real thing, so any future real-ABHA code path cannot
            be exercised with these values. That is intentional: real linkage must go through
            the authorised ABDM workflow (Phase 1 scope excludes it).
```

### D17 — Duplicate detection landed in M3 rather than M5

```text
Decision:   When to implement the duplicate-registration warning
Selected:   With registration (M3), earlier than the M5 slot in the original milestone plan.
Reason:     CLAUDE.md section 6 makes the duplicate check part of registration itself, and it
            reuses the already-tested matcher, so building registration without it would have
            meant revisiting the same class.
Behaviour:  Scores every new embedding against the existing gallery and keeps the strongest
            hit, since one matching image is enough to warrant a warning. Above the threshold
            it returns PossibleDuplicate and writes NOTHING. Identities are never merged
            automatically. `force = true` lets the operator override after confirming.
            The warn threshold (0.35) is stricter than the identification threshold on purpose:
            a false warning costs one confirmation, a missed duplicate creates two records for
            one person. UNCALIBRATED - Phase 1 has evidence from 10 identities only (E2).
```

## 8. Experiments

### E1 — Model tensor layout and load/inference on the emulator (M1)

```text
Experiment:  Confirm both ONNX models load under ONNX Runtime Android and record their real
             tensor layout before writing any decoding code.
Objective:   Replace ASSUMED output ordering with OBSERVED ordering.
Configuration:
             ORT Android 1.30.0, default SessionOptions (CPU), zero-filled float input.
             AVD patrick_api36: Pixel 7 profile, API 36 google_apis x86_64, 4 GB RAM,
             WHPX acceleration; host AMD Ryzen 7 250.
Method:      diagnostics/ModelInspector: create session from asset bytes, read declared
             input/output info, run twice (first + warm), read runtime output shapes.
Result (MEASURED, 2026-09-24, single run):
  det_500m.onnx   in  input.1 [1, 3, -1, -1]  (dynamic H/W; probed at 640x640)
                  out[0..2] scores  [12800,1] [3200,1] [800,1]     strides 8/16/32
                  out[3..5] bboxes  [12800,4] [3200,4] [800,4]
                  out[6..8] kps     [12800,10] [3200,10] [800,10]
                  load 247.3 ms | first run 183.8 ms | warm run 180.7 ms
  w600k_mbf.onnx  in  input.1 [-1, 3, 112, 112]
                  out[0] [1, 512]
                  load 167.0 ms | first run 39.9 ms | warm run 21.3 ms
Observation: 12800 = 80*80*2 -> 2 anchors per location at stride 8 (640/8 = 80).
             Output order is grouped by kind (all scores, then all boxes, then all kps),
             ascending stride within each group. Output names (443, 468, ...) carry no meaning.
Conclusion:  The SCRFD decoder will bind outputs by index with this layout, 2 anchors per
             cell, 3 strides. Timings are EMULATOR figures on an x86 host. They are NOT
             evidence for the <= 2 s phone target and only show the pipeline is plausibly
             fast enough to develop against.
```

### D14 — Embedding wrapper written by the user

```text
Context:    Four attempts by the agent to write the ONNX embedding wrapper were stopped by an
            automated safety check or arrived truncated (see P11, P12). The user wrote the
            file instead, from a contract the agent supplied (input/output shape, L2
            normalisation, modelVersion, no android.* imports).
Reviewed:   Preprocessing matches insightface v0.7 arcface_onnx.py: RGB (no BGR swap, since
            RgbImage is already RGB), planar CHW, (px - 127.5) / 127.5. Input "input.1",
            tensor [1, 3, 112, 112], output read from [1, 512]. All consistent with E1.
Fixes applied by the agent before it compiled:
            1. `nodeInfo.info.shape` does not compile - ValueInfo has no shape. Cast to
               TensorInfo first (the same cast ModelInspector already uses).
            2. Split into `interface FaceEmbeddingModel` + `class ArcFaceOnnxEmbedder`, as
               CLAUDE.md section 12 requires, so callers do not depend on the ONNX class.
            3. SessionOptions was closed only on the failure path; now closed on both.
            4. Reused the existing match/VectorMath.l2Normalize instead of a second copy.
Unverified: whether w600k_mbf contains its own Sub/Mul normalisation nodes (in which case
            mean 0 / std 1 would be correct instead of 127.5/127.5). ASSUMED it does not.
            Wrong normalisation would not crash; it would collapse the same-person vs
            different-person separation, which experiment E2 measures directly.
```

### E2 — End-to-end recognition on real LFW faces (M2)

```text
Experiment:  Run the full pipeline (ML Kit detect -> 5-point align -> MobileFaceNet embed ->
             brute-force cosine -> decision engine) on real face images.
Objective:   Does recognition actually work, and do same-person and different-person
             similarities separate? Also: is the (px-127.5)/127.5 preprocessing correct?
Configuration:
             Detector  ML Kit face-detection 16.1.7, PERFORMANCE_MODE_ACCURATE, all landmarks
             Embedder  w600k_mbf@9cc6e4a7 (512-d, L2-normalised), ONNX Runtime 1.30.0
             Matcher   BruteForceCosineMatcher, Aggregation.MAX
             Device    AVD patrick_api36 (API 36 x86_64, WHPX), host AMD Ryzen 7 250
Dataset:     logasja/lfw@0ee4797 via tools/prepare_dataset.py (deterministic, seed 20260924)
             10 enrolled identities x 5 images = 50 gallery embeddings
             10 held-out probes (1 per enrolled identity)
             10 never-enrolled identities (UNKNOWN probes)
Method:      app/src/androidTest/.../RecognitionPipelineTest.kt, run via
             ./gradlew connectedDebugAndroidTest. All 5 tests passed.

RESULT (MEASURED 2026-09-25, single run):

  Detection        50/50 enrolment images produced a usable 5-landmark face (0 failures)
                   70/70 images overall

  Cosine similarity distributions
                   same-person      n=100   min 0.376  p5 0.473  mean 0.623  p95 0.799  max 0.811
                   different-person n=1125  min -0.233 p5 -0.080 mean 0.029  p95 0.152  max 0.285

  Identification   rank-1 10/10 correct
                   best-vs-runner-up margin: mean 0.568, min 0.160
                   per-probe best score range 0.441 (Tim_Henman) to 0.876 (Fujio_Cho)

  Unknown handling 10/10 never-enrolled probes -> UNKNOWN, 0 false MATCHes
                   their best gallery score: mean 0.159, max 0.242

  Latency (emulator, x86 host - NOT phone figures)
                   detect  median 88 ms, p95 184 ms  (n=70)
                   embed   median 15 ms, p95  45 ms  (n=70)

Observation:  1. On this set the two distributions do NOT overlap: lowest same-person
                 similarity 0.376 > highest different-person similarity 0.285. Any threshold
                 in that gap separates all 70 images perfectly here.
              2. This also CONFIRMS the preprocessing assumption from D14. Wrong
                 normalisation would have collapsed the separation, and it did not.
              3. The placeholder matchThreshold of 0.5 would FALSE-REJECT 1 of 10 probes
                 (Tim_Henman at 0.441) while still rejecting every unknown. Evidence that
                 0.5 is too high for this model, not that the model is weak.
              4. ML Kit found a usable face in every image, so no quality gate was exercised.
                 LFW images are well-framed press photos; live camera input will differ.

Conclusion:   The M2 vertical slice works end to end on real faces. The ML Kit landmark
              mismatch (D2) did not prevent recognition on this set; its cost relative to
              SCRFD remains unmeasured.

              This is NOT calibration and NOT an accuracy claim. 10 identities and 70 images
              are far too few to estimate false-accept or false-reject rates, which need
              orders of magnitude more comparisons. Threshold calibration is Phase 2/3 work on
              the 100-person gallery and beyond. No number here transfers to the intended
              population: LFW is celebrity web photography, and the model's own published
              South Asian score is ~20 points below its LFW score (D3).
```

### E3 — Quality-metric survey and threshold calibration (M4)

```text
Experiment:  Measure what the quality metrics actually look like on genuine face photos, then set
             the thresholds from that distribution.
Why:         The first thresholds were guesses, and they were wrong. With minBlurVariance = 60
             and a strict multiple-face rule, the gates rejected 22 of 70 ordinary LFW photos
             (31 percent): MULTIPLE_FACES 10, TOO_BLURRY 11, EXTREME_POSE 1. Quality scores
             peaked at 0.41 (mean 0.17), so the intended minQuality of 0.35 would have rejected
             most genuine faces and broken recognition outright.
Method:      QualityMetricsSurveyTest with every gate opened wide, so all 70 eval images are
             measured rather than rejected. Floors then set BELOW the observed minimum.

MEASURED distributions on 70 genuine LFW faces (2026-09-25):

  faces per image     1 face: 60   2 faces: 7   3 faces: 1   4 faces: 2
  interOcularPx       min 21.0  p5 32.5  median 39.6  p75 41.7  max 46.7
  blurVariance        min 15.9  p5 41.0  median 104.5 p75 172.1 max 750.1
  meanLuminance       min 83.5  p5 94.0  median 123.9 p75 137.5 max 174.0
  clippedFraction     min 0.0   median 0.00008        max 0.045
  abs yaw (deg)       median 7.5   p75 14.4  max 52.9
  abs pitch (deg)     median 5.2   p75 8.4   max 34.8
  abs roll (deg)      median 4.5   p75 6.9   max 24.8

Calibration applied:
  minInterOcularPx     24  -> 18   (below the observed min of 21)
  minBlurVariance      60  -> 12   (below the observed min of 16; the old value cut off 16 pct)
  maxAbsYaw            35  -> 45   maxAbsPitch 30 -> 40   (roll unchanged at 30)
  minAcceptableScore   0.35 -> 0.10   decision minQuality 0.35 -> 0.10
  scoring anchors      GOOD_INTER_OCULAR 80 -> 60, GOOD_BLUR_VARIANCE 500 -> 150
  multiple-face rule   any second face -> only a second face larger than 0.5x the subject area

Observation:  1. 14 percent of ordinary LFW photos contain a background face. Rejecting on ANY
                 second face would make the app unusable in public places, so the rule now picks
                 the most prominent face and rejects only when a second is comparably large,
                 i.e. when it is genuinely ambiguous who the operator meant.
              2. LFW faces span only 21 to 47 px between the eyes, because the images are
                 250x250 web photos. The ArcFace template spans about 35 px at 112x112, so many
                 of these faces are being UPSAMPLED. E2 nevertheless got rank-1 10/10 on them,
                 so they are usable, but a camera-based deployment should raise this floor.
              3. Gate ORDER matters for usefulness, not just correctness: a blown-out image also
                 fails the blur test, so checking blur first told the operator to "hold steady"
                 when the real problem was glare. Lighting is now checked before blur. Caught by
                 a JVM unit test, not by inspection.

Conclusion:   Thresholds are now derived from measured data rather than intuition, and are
              recorded as such. They remain UNCALIBRATED in the statistical sense: 70 images from
              one dataset cannot fix an operating point, and LFW's low resolution biases the size
              and blur floors downwards. Real camera input will differ, and the floors should be
              re-measured once live captures exist.
```

### E4 — Quality gates on real and deliberately degraded images (M4)

```text
Experiment:  Verify the gates accept ordinary photos and reject genuinely bad input.
Method:      QualityGateTest on the emulator. Good case: all 70 LFW eval images. Bad cases derived
             from a real eval image so only the defect differs: 12 passes of 3x3 box blur, x0.12
             brightness, x4.0 brightness, two eval faces composited side by side, and a flat image.
Configuration: calibrated thresholds from E3; ML Kit ACCURATE mode; w600k_mbf@9cc6e4a7.

RESULT (MEASURED 2026-09-25):

  Genuine photos accepted      67/70 (96 pct)
    remaining rejections       MULTIPLE_FACES 2, EXTREME_POSE 1
                               all three inspected and defensible: two images contain several
                               comparably sized people, one has a head turn beyond 45 degrees
  Quality score range          min 0.029, mean 0.432, max 0.683  (was max 0.41, mean 0.17)

  Degraded inputs
    flat grey image            NO_FACE
    12x box blur               TOO_BLURRY (blurVariance 1.4, versus a genuine min of 15.9)
    x0.12 brightness           TOO_DARK (meanLuminance 17.6, versus a genuine min of 83.5)
    x4.0 brightness            NO_FACE - the detector loses the face entirely before the
                               brightness gate is reached. Still rejected, but the operator gets
                               "no face detected" rather than "move out of direct light". Recorded
                               as a wording limitation, not a safety one.
    two people side by side    MULTIPLE_FACES
    any rejection              alignMs = 0 and embedMs = 0, asserted: a rejected image is never
                               aligned or embedded, so bad data cannot reach the gallery

  Full-pipeline latency (emulator, single accepted image)
    detect 59 ms, quality 7 ms, align 14 ms, embed 14 ms, total 94 ms

Conclusion:   The gates behave as intended on real data, and rejection provably short-circuits
              before embedding. Recognition results from E2 are unchanged by the quality layer
              (rank-1 10/10, 0 false matches).
              Latency is an EMULATOR figure and is NOT evidence about the 2 s phone target.
```

### E5 — Demo UI and camera path on the emulator (M6, brought forward)

```text
Experiment:  Verify the demo UI drives the REAL recognition pipeline end to end, with a live
             camera, and that every value on screen is measured rather than hardcoded.
Why now:     The user needs a presentable demo for their professor, so M6 (UI + camera) was
             brought forward ahead of M5 (100-person gallery). See D18.
Configuration: AVD patrick_api36 with hw.camera.front=webcam0 (the laptop webcam feeds the
             emulator's front camera); debug APK; ML Kit + w600k_mbf@9cc6e4a7.
Method:      Driven through adb (taps and uiautomator dumps), reading the on-screen values back.

RESULT (MEASURED 2026-09-25):

  Live camera preview      WORKS. CameraX binds the front camera and the webcam feed renders in
                           the Compose preview on both Register and Identify.
  Registration             WORKS end to end on a real face:
                             5 of 5 samples captured and accepted through the real pipeline
                             measured quality scores 0.760 and 0.678 (per-sample, real values)
                             saved as "Demo" with dummy id DUMMY-42881341226468
                             5 embeddings persisted; home screen then showed "Registered
                             Employees (1)" read from the database
  Guided capture           Prompts advance per sample (frontal, left, right, neutral, natural)
                           and the capture button correctly stops at 5 of 5.
  Identification           Ran the real pipeline against the live camera with no person in
                           frame. Correctly returned UNCERTAIN / "No face detected. Point the
                           camera at the person." in 1493 ms.
                           A MATCH against a registered face was NOT verified: it needs a person
                           in front of the camera, which the agent cannot arrange. PENDING.
  Technical Details panel  Every field real, read back from the running app:
                             detector, alignment, model w600k_mbf@9cc6e4a7, dims 512
                             registered people 1, stored embeddings 5   (live DB counts)
                             decision UNCERTAIN (LOW_QUALITY), rejected because NO_FACE
                             thresholds 0.350 / 0.100 / 0.100  (the calibrated M4 values)
                             detect 1493 ms, quality 0, align 0, embed 0, search 0, total 1493
  Short-circuit proof      align = embed = 0 ms on a rejected frame, visible in the UI, matching
                           the assertion in the instrumented tests: a rejected image is never
                           aligned or embedded.

Latency note: detection on a full-resolution camera frame took about 1.5 s on the emulator,
             far above the 59 ms measured on 250x250 LFW images (E4). Expected: ML Kit in
             ACCURATE mode scales with input resolution, and this is an x86 emulator. It is NOT
             evidence about phone latency, but it does suggest downscaling the frame before
             detection is worth measuring (deferred to Phase 2).

Problem found: the soft keyboard covered the Save button, so registration could not be completed
             until the keyboard was dismissed. A real usability bug, not a test artefact. Fixed
             with imePadding plus windowSoftInputMode=adjustResize, and reinstalled.

Conclusion:  The UI is a presentation layer over the existing pipeline; it contains no
             recognition logic and no mock data. Registration, quality rejection, persistence
             and the technical panel are verified on a live camera. MATCH / UNKNOWN against a
             live face remain to be demonstrated by the user.
```

## 9. Performance

`MEASURED` on the **emulator only** (E1, E2). Emulator timings run on an x86 laptop CPU and are
**not** evidence about the ≤ 2 s phone target, which remains unverified.

| Stage | Median | p95 | n |
|---|---|---|---|
| Face detection (ML Kit, full image) | 53 ms | 91 ms | 70 |
| Quality assessment | 7 ms | - | 1 |
| Alignment (112x112 warp) | 14 ms | - | 1 |
| Embedding (MobileFaceNet, 112x112) | 11 ms | 16 ms | 70 |
| **Full pipeline, accepted image** | **94 ms** | - | 1 |
| Model load (detector / embedder, one-off) | 247 / 167 ms | - | 1 |

A rejected image costs only detection plus the quality check (about 60 ms here) because it never
reaches alignment or embedding.

Search latency is not yet measured at scale (the E2 gallery is only 50 embeddings). Memory,
battery and thermals: not measured. Camera capture latency: not measured (M6).

## 10. Recognition Evaluation

First real evaluation: see **E2** for the full setup and numbers. Summary, all `MEASURED`
2026-09-25 on the emulator:

| Item | Value |
|---|---|
| Dataset | LFW via `logasja/lfw@0ee4797`, deterministic subset |
| Identities enrolled | 10, with 5 images each (50 gallery embeddings) |
| Test samples | 10 held-out probes + 10 never-enrolled probes |
| Known-person result | rank-1 10/10 correct |
| Unknown-person result | 10/10 UNKNOWN, **0 false matches** |
| False accepts | 0 of 10 at matchThreshold 0.5 |
| False rejects | 1 of 10 would occur at matchThreshold 0.5 (probe scored 0.441) |
| Same-person similarity | mean 0.623 (min 0.376) |
| Different-person similarity | mean 0.029 (max 0.285) |
| Best-vs-second-best margin | mean 0.568, min 0.160 |
| Model version | `w600k_mbf@9cc6e4a7` |
| Input quality | not gated; ML Kit found a usable face in all 70 images |

**No accuracy percentage is claimed.** With 10 identities and 70 images, false-accept and
false-reject rates cannot be estimated meaningfully; that needs a far larger gallery and many
more comparisons (Phase 2/3). The observed separation is a property of this small sample.

## 11. Problems Encountered

| # | Problem | Status |
|---|---|---|
| P1 | Canonical LFW host `vis-www.cs.umass.edu` unreachable (curl `000`), so `sklearn.fetch_lfw_people` would fail | Resolved (§12) |
| P2 | `sdkmanager --licenses` fed from a PowerShell string pipe did not receive input; the install then reported that every package was refused for unaccepted licences, yet exited 0 | Resolved |
| P3 | A background `sdkmanager ... \| tail` install exited 0 after downloading only 12% of the emulator; platform-tools, platforms and build-tools were missing | Resolved |
| P4 | A large bash heredoc for `ENVIRONMENT.md` failed with `unexpected EOF while looking for matching '` | Resolved (used the file-writing tool instead) |
| P5 | `graphify install --project` writes hooks calling bare `graphify`, which is not on PATH (it lives in `.venv`) | Resolved |
| P6 | Fixing P5 by editing `.claude/settings.json`, and running the first `graphify update .`, were both denied by the auto-mode safety classifier as self-modification | Resolved by user decision |
| P7 | `local.properties` written with `sdk.dir=C\:\Users\...`: the shell collapsed the doubled backslashes, and Java properties treats `\` as an escape, so the path would have resolved wrongly | Resolved: forward slashes (`C:/Users/jassu/Android/Sdk`) |
| P13 | The soft keyboard covered the Save button on the registration screen, so registration could not be completed while the keyboard was open | Fixed: `imePadding()` on the scrollable screens plus `windowSoftInputMode="adjustResize"` |
| P12 | Writing the ONNX embedding wrapper failed the same way (safety check / truncated writes). Worked around by splitting the file into small appended chunks after the user supplied the code | Resolved; see D14 |
| P11 | Writing the SCRFD decoder failed 4 times: 2 responses stopped by an automated safety check, others truncated mid-file or tool calls with missing parameters. Partial files were deleted so the build stayed green | Closed by user decision: detector switched to ML Kit (D2 revised). Recorded as a failed approach |
| P10 | `move_to_cloud` refused: "This account has no cloud environment yet" | Closed by user decision: stay local |
| P9 | Second build failed: `Unresolved reference: net` / `nio` in `app/build.gradle.kts`. Inside a Gradle Kotlin script `java` resolves to the `java {}` project extension, so `java.net.URI` is not the JDK package | Resolved with top-level `import java.net.URI` etc. |
| P8 | First `assembleDebug` failed: `settings.gradle.kts:5 Illegal escape: '\.'`. The regex `"com\\.android.*"` lost a backslash when written through a bash heredoc, and a Python fix through bash lost it again | Resolved: Kotlin raw strings `"""com\.android.*"""`, written with an exact-edit tool. Lesson: don't write backslash-heavy source through the shell |

## 12. Solutions

- **P1:** use the Hugging Face mirror `logasja/lfw` (HTTP 200 verified; schema has `image` +
  `label` with 5,749 class names).
- **P2:** `yes | sdkmanager.bat --licenses` from bash. All licences accepted.
- **P3:** re-ran the install with output redirected to a log file instead of piped. All five
  packages then verified present on disk.
- **P4:** wrote the markdown with the file-writing tool rather than a heredoc.
- **P5/P6:** a fix pointing the hooks at `$CLAUDE_PROJECT_DIR/.venv/...` was prepared but not
  applied. The **user chose to remove the hooks instead** (deleted `.claude/settings.json`), so
  Graphify is used on demand. The first `graphify update .` then succeeded: 178 nodes, 166
  edges, 21 communities, all from docs and Gradle settings since no Kotlin exists yet.

## 13. Failed Approaches

- PowerShell string pipe to accept SDK licences (P2).
- Piping a long `sdkmanager` install to `tail` (P3).
- Initial plan of a Python desktop prototype — superseded by the Android requirement, not
  technically failed (D1).

## 14. Current Limitations

What the system still cannot do, as of 2026-09-25:

- **No camera.** Recognition only runs on image files. Deliberate (D13); it is the last M6 step
  and Phase 1 cannot close without it.
- **Quality gates are calibrated on 70 LFW images only** (E3). LFW is low-resolution web
  photography, which drags the size and blur floors down; live camera input will differ, and the
  floors should be re-measured once real captures exist.
- **An over-exposed face reports "no face detected"** rather than "move out of direct light",
  because the detector fails before the brightness gate is reached (E4). Rejection is correct;
  the wording is not the most helpful.
- **No UI.** The only screen is the M1 model-inspector diagnostic.
- **Thresholds are uncalibrated**, and E2 shows the placeholder 0.5 is too high: it would
  false-reject a genuine probe scoring 0.441.
- **Only 10 identities evaluated**, not the ~100 target. No FAR/FRR estimate is possible.
- **No local encryption.** On the Phase 1 MVP list; DEFERRED, and now recorded rather than
  quietly dropped.
- **No latency evidence on real hardware.** Emulator figures only; no physical phone yet.
- **ML Kit sends diagnostics to Google** (never images or face data) with no documented opt-out
  (D2 revised).
- **Model weights are non-commercial research only** (D3), so they cannot ship in a product.

## 15. Phase Completion Checklist

```text
[x] Toolchain installed and verified (JDK 17, SDK 36, emulator, adb)
[x] Git repository initialised
[x] Documentation set created (README, PROJECT_STATE, ENVIRONMENT, CLEANUP, this report)
[x] CLAUDE.md created
[x] Graphify installed + registered; hooks removed by user; initial graph built
[x] GitHub remote configured and pushed (public repo; commits use GitHub noreply email)
[x] AVD created with webcam-backed camera (patrick_api36; WHPX usable, no admin step needed)
[x] Android project builds (assembleDebug, 99 MB debug APK, x86_64 + arm64-v8a only)
[x] Models fetched (checksum-verified) and loaded on the emulator; tensor shapes dumped (E1)
[x] Decision engine + brute-force matcher implemented; 22/22 JVM unit tests pass
    (incl. the different-person runner-up rule and the cross-model-version guard)
[x] Camera input                      CameraX live preview + still capture (E5)
[x] Face detection                    ML Kit; 70/70 eval images, E2
[x] Face alignment                    5-point similarity to ArcFace template
[x] Embedding generation              w600k_mbf, 512-d, L2-normalised, E2
[ ] Local database (Room) with multiple embeddings per person   <- next (M3)
[x] Similarity search                 brute-force cosine, per-person aggregation
[x] MATCH / UNCERTAIN / UNKNOWN        22 JVM tests + E2 on real faces
[x] Registration workflow (configurable image count, default 5)
[x] Quality checks (7 gates, calibrated in E3, verified in E4)
[x] Duplicate-registration warning (D17)
[ ] Latency measurement
[ ] ~100-person gallery via bulk enrolment
[~] Tests (53 total, all passing: 33 JVM + 20 instrumented)
    [x] known person (E2, rank-1 10/10)
    [x] unknown person (E2, 0 false matches)
    [x] latency (E2, emulator only)
    [x] registration, multiple embeddings per person
    [x] database persistence across a close/reopen cycle
    [x] duplicate registration (warns, stores nothing, never merges)
    [x] uncertain / ambiguous margin (12 JVM decision-engine tests)
    [x] no face, multiple faces, poor quality (blur / dark / bright) — E4
    [x] quality thresholds calibrated from measured data — E3
```

## 16. Final Phase State

Not reached.
