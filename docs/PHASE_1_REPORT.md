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
| Detector, aligner, embedder wrapper, quality, DB, camera, UI | `PLANNED` (M2-M5) |

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

## 9. Performance

Nothing measured yet. The ≤ 2 s target is `PLANNED`, not achieved.

## 10. Recognition Evaluation

Not applicable yet.

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

Nothing is built. No app, no AVD, no measurements.

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
[ ] Camera input
[ ] Face detection
[ ] Face alignment
[ ] Embedding generation
[ ] Local database (Room) with multiple embeddings per person
[ ] Similarity search
[ ] MATCH / UNCERTAIN / UNKNOWN decision engine
[ ] Registration workflow (configurable image count, default 5)
[ ] Quality checks
[ ] Duplicate-registration warning
[ ] Latency measurement
[ ] ~100-person gallery via bulk enrolment
[ ] Tests: no face, multiple faces, poor quality, registration, multiple embeddings,
    persistence, known person, unknown person, uncertain, duplicate, latency
```

## 16. Final Phase State

Not reached.
