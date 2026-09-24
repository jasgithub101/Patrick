# CLAUDE.md

Project: **Local Android Face Recognition System**

This file contains persistent instructions for Claude Code throughout the entire project.

---

## 1. Project Goal

Build an Android application that performs local face identification and associates
recognized people with **dummy ABHA IDs**.

The project is developed incrementally through multiple phases. The immediate objective is a
working Phase 1 prototype.

The long-term system may eventually support large-scale deployment, synchronization across
registration devices, stronger security, liveness detection, and real ABHA/ABDM integration.
**Do not implement those prematurely.**

## 2. Core Principles

- Build a working prototype before optimizing for scale.
- Prefer simple, understandable architecture.
- Measure before making performance or accuracy claims.
- Do not fabricate results.
- Keep important components replaceable.
- Keep recognition logic separate from UI.
- Keep model selection configurable/reproducible.
- Document important decisions and findings continuously.
- Do not prematurely implement future-phase functionality.
- Never force an identity match when confidence is insufficient.

## 3. Phase 1 Scope

Phase 1 is a local Android face-recognition prototype.

**Required:** Android phone · on-device face recognition · camera input · face detection ·
basic face-quality checking · face alignment/preprocessing where required · pretrained
face-recognition model · embedding generation · multiple embeddings per person ·
SQLite/local persistent storage · dummy ABHA IDs · local similarity search · confidence
evaluation · MATCH / UNCERTAIN / UNKNOWN outcomes · registration workflow · identification
workflow · duplicate-registration warning · recognition latency measurement · testing with
approximately 100 registered people.

**Explicitly out of scope for Phase 1 — do NOT implement:** real ABHA integration · real ABDM
integration · cloud recognition · cloud backend · device synchronization · national-scale
deployment · distributed databases · advanced ANN infrastructure unless actually required by
Phase 1 measurements · model fine-tuning · Indian-specific model training · liveness
detection · advanced anti-spoofing · production biometric security architecture · real
patient/biometric data.

## 4. Phase 1 Recognition Pipeline

```text
Camera
  ↓
Face Detection
  ↓
Quality Check
  ↓
Face Alignment / Preprocessing
  ↓
Face Embedding Model
  ↓
Local Repository
  ↓
Similarity Search
  ↓
Confidence / Decision Engine
  ↓
MATCH / UNCERTAIN / UNKNOWN
```

Registration:

```text
Camera → Face Detection → Quality Check → Capture Multiple Images
       → Generate Embeddings → Store Embeddings → Person + Dummy ABHA ID
```

Identification:

```text
Camera → Face Detection → Quality Check → Embedding → Local Search
       → Candidate Evaluation → MATCH / UNCERTAIN / UNKNOWN
```

## 5. Critical Recognition Rule

**The system MUST NOT automatically match the nearest candidate.**

A candidate having the highest similarity does not necessarily mean that the candidate is the
correct person. The decision engine should consider configurable criteria such as:

- Similarity score
- Best-vs-second-best margin
- Input quality
- Potentially multiple stored embeddings

The system must be capable of returning `MATCH`, `UNCERTAIN`, and `UNKNOWN`.

Low confidence should result in a retry or uncertain result rather than a forced identity.
Initial thresholds are experimental and must not be treated as scientifically validated.

> Implementation note: the "second-best" in the margin must be the best score of a
> **different person**, not the second-best embedding. The second-best embedding is usually
> another photo of the same person, which would collapse the margin and silently disable this
> rule.

## 6. Registration

Initial registration target: **5 images per person**. Make the number configurable so later
experiments can compare 3 / 5 / 7 / 10. Do not assume 5 is optimal — it is only the initial
configuration.

Capture useful variation where practical: frontal · slight left/right pose · different
natural expressions · with/without spectacles where applicable.

Store multiple embeddings for each person. Perform a duplicate check during registration. If a
strong existing match is found: **warn the user; do not automatically merge identities.**

## 7. Database

Use SQLite or an appropriate Android SQLite-based persistence layer. Conceptually:

```text
Person
 ├── personId
 ├── dummyAbhaId
 └── createdAt

Embedding
 ├── embeddingId
 ├── personId
 ├── embeddingVector
 ├── modelVersion
 └── createdAt
```

The database must survive application restarts. Store model version information with
embeddings so that future model changes can be handled safely.

## 8. Model Selection

Use a pretrained face-recognition model for Phase 1. Do not select a model solely because it
is popular. Evaluate: recognition quality · Android compatibility · inference speed · model
size · memory usage · runtime availability · licensing · ease of integration · availability of
reliable pretrained weights.

Do not fine-tune during Phase 1. Keep the embedding model behind an abstraction/interface so
it can be replaced later.

## 9. Quality Checking

At minimum handle: no face · multiple faces · face too small · excessive blur · poor lighting
where practical · excessive pose where practical.

Poor-quality input should normally result in a retry rather than producing/storing a
low-quality embedding.

## 10. Performance

Measure actual performance. At minimum record: face detection latency · embedding inference
latency · search latency · total recognition latency.

Initial target: **≤ 2 seconds per normal recognition attempt**, where practical. Do not claim
the target has been achieved unless it has actually been measured.

## 11. Evaluation

The initial target is approximately 100 registered test people. This is a prototype
validation target, not a statistically meaningful national-scale accuracy evaluation.

Where possible measure: known-person recognition · unknown-person rejection · false accepts ·
false rejects · similarity scores · threshold behavior · best-vs-second-best margin ·
recognition latency · model version · input quality.

**Do not fabricate accuracy, FAR, FRR, latency, or any other metric.** When reporting results,
explain the test setup and dataset.

## 12. Architecture

Keep major components separated. Prefer abstractions similar to:

```text
FaceDetector
FaceQualityChecker
FaceAligner
FaceEmbeddingModel
FaceRepository
FaceMatcher
RecognitionDecisionEngine
```

Exact names may change if there is a strong implementation reason. The goal is
replaceability and separation of concerns, not unnecessary abstraction. Avoid
over-engineering.

## 13. Development Strategy

Implement incrementally, preferably in this order:

1. Inspect environment/repository
2. Establish baseline
3. Create minimal Android project if required
4. Camera
5. Face detection
6. Embedding generation
7. Minimal local database
8. Similarity search
9. MATCH/UNCERTAIN/UNKNOWN
10. Registration
11. Multiple embeddings
12. Quality checks
13. Duplicate detection
14. Evaluation logging
15. Testing
16. Documentation

Build and test after meaningful milestones. Keep the project buildable.

## 14. Graphify

Graphify is a **development-time** codebase knowledge and architecture tool. It is **NOT**
part of the Android application's runtime.

Use it to understand: project structure · classes · functions · dependencies · imports · call
relationships · component relationships · architectural impact of changes.

Use it especially when: investigating unfamiliar code · understanding dependencies ·
evaluating refactoring impact · finding relationships between components · reviewing
architecture after major changes.

Keep the Graphify graph updated after significant structural changes. **Do not add Graphify
dependencies to the Android application's production code.** Track Graphify and its
installation in `ENVIRONMENT.md`. Graphify was installed specifically for this project, so its
removal is included in `CLEANUP.md`.

## 15. Environment Management

Maintain `ENVIRONMENT.md`. Before installing anything:

1. Inspect the existing environment.
2. Determine whether it is already installed.
3. Reuse compatible existing installations.
4. Avoid duplicate versions.
5. Install only what is necessary.
6. Record every project-specific installation.

Track: JDK · Android SDK · build tools · Gradle · Kotlin · Python · Node/npm · ML runtimes ·
ONNX/TFLite dependencies · native libraries · CLI tools · Graphify · model files · other
project-specific development tools.

For each relevant installation record: `Component` · `Version` · `Reason` · `Date` ·
`Required by` · `Installation method` · `Removal method` · `Status` · `Origin`.

Classify installations as `PRE-EXISTING` · `PROJECT-INSTALLED` · `PROJECT-MODIFIED` ·
`UNKNOWN`. **Never automatically remove `UNKNOWN` components.**

## 16. Cleanup

Maintain `CLEANUP.md`. At the end of the entire project, identify everything installed
specifically for this project. Remove only project-specific additions. Do not remove
pre-existing tools or software. Document the cleanup process before performing it.

## 17. Continuous Documentation

Maintain one separate document for every phase: `docs/PHASE_1_REPORT.md`,
`docs/PHASE_2_REPORT.md`, … Do NOT wait until a phase is finished to document it. Update the
current phase report continuously.

Document: what was implemented · what was tested · architecture decisions · model decisions ·
dependencies · experiments · measurements · problems · solutions · failed approaches ·
important discoveries · limitations · deferred decisions · remaining work.

Documentation must describe what actually happened. Distinguish clearly between
`PLANNED` · `IMPLEMENTED` · `TESTED` · `MEASURED` · `OBSERVED` · `ASSUMED` · `DEFERRED`.

**Never convert an assumption into a fact.**

## 18. Required Project Documents

| File | Purpose |
|---|---|
| `README.md` | High-level explanation, architecture, setup and usage |
| `PROJECT_STATE.md` | Short current state: what works, what is in progress, what is broken, decisions, blockers, next steps |
| `ENVIRONMENT.md` | Complete environment/install inventory |
| `CLEANUP.md` | Final cleanup instructions |
| `docs/PHASE_X_REPORT.md` | Detailed historical record for each phase |

## 19. Phase Start

Before beginning a new phase:

1. Read the previous phase report.
2. Read `PROJECT_STATE.md`.
3. Read `ENVIRONMENT.md`.
4. Review unresolved issues.
5. Review failed approaches.
6. Review previous experiments.
7. Update Graphify if necessary.
8. Create the new phase report.
9. Record the starting state.
10. Begin implementation.

## 20. Phase Completion

Before marking a phase complete:

1. Run relevant tests.
2. Verify requirements.
3. Record actual measurements.
4. Document failures and limitations.
5. Update the phase report.
6. Update `PROJECT_STATE.md`.
7. Update `ENVIRONMENT.md`.
8. Update `CLEANUP.md` if necessary.
9. Update Graphify.
10. Commit the completed work with Git.

Never mark something complete merely because the code was written. It should be implemented
and tested where applicable.

## 21. Git

Use Git throughout development. Prefer meaningful commits such as:

```text
phase-1: initialize Android project
phase-1: add camera pipeline
phase-1: integrate face detection
phase-1: integrate embedding model
phase-1: add local embedding database
phase-1: implement recognition decision engine
phase-1: add registration workflow
phase-1: add identification workflow
phase-1: add evaluation logging
phase-1: complete prototype
```

Avoid one enormous commit for an entire phase.

## 22. Decision Making

Claude Code may recommend implementation approaches, but significant ML decisions should be
supported by evidence. Do not assume:

- One model is universally best.
- One threshold works for every environment.
- More embeddings always improve recognition.
- A high benchmark score guarantees performance on this application.
- Performance on a generic dataset represents Indian users.
- A nearest-neighbor result is automatically the correct identity.

When uncertain, implement a measurable experiment rather than making an unsupported
assumption.

## 23. Future Phases

Future phases may investigate: recognition experiments · threshold calibration · FAR/FRR
evaluation · larger galleries · ANN search · mobile optimization · Indian-specific evaluation
· model fine-tuning if justified · distributed synchronization · security ·
liveness/anti-spoofing · production architecture · official ABHA/ABDM integration.

Do not implement these merely because they are listed here. Each future phase must be
justified by findings from previous phases.

## 24. Final Principle

```text
BUILD → TEST → MEASURE → DOCUMENT → LEARN → IMPROVE
```

Evidence from one phase should determine what is changed in the next phase. Build the
simplest system that can answer the current engineering question.

---

## Appendix A — Operational facts for this machine

These are facts discovered during setup. Verify before relying on them if much time has
passed; the authoritative record is `ENVIRONMENT.md`.

**Nothing is on PATH by design.** No global environment variables were set, so set them per
command:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
export ANDROID_HOME="/c/Users/jassu/Android/Sdk"
```

| Tool | Location |
|---|---|
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` |
| Android SDK root | `C:\Users\jassu\Android\Sdk` (non-default path, on purpose) |
| adb | `$ANDROID_HOME/platform-tools/adb.exe` |
| sdkmanager / avdmanager | `$ANDROID_HOME/cmdline-tools/latest/bin/*.bat` |
| emulator | `$ANDROID_HOME/emulator/emulator.exe` |
| Graphify | `./.venv/Scripts/graphify.exe` (project-local venv) |

Gotchas already hit:

- `sdkmanager --licenses` must be fed with `yes |` from bash. A PowerShell string pipe does
  not reach its stdin, and packages then silently refuse to install.
- Piping a long `sdkmanager` install into `tail` truncated the install. Redirect to a log file
  instead.
- The host CPU is AMD, so the emulator needs WHPX, not HAXM.
- The canonical LFW host (`vis-www.cs.umass.edu`) is unreachable from this machine; use the
  Hugging Face mirror `logasja/lfw`.

## Appendix B — Graphify operational notes

Graphify lives in the project-local `.venv`, **not** on PATH and **not** in the system
Python. Invoke it by path:

```bash
./.venv/Scripts/graphify.exe update .            # rebuild code graph (local AST, no LLM)
./.venv/Scripts/graphify.exe query "<question>"  # scoped subgraph for a question
./.venv/Scripts/graphify.exe path "A" "B"        # how two components connect
./.venv/Scripts/graphify.exe explain "X"         # a node and its neighbours
./.venv/Scripts/graphify.exe affected "X"        # impact analysis before a refactor
./.venv/Scripts/graphify.exe god-nodes           # most-connected architectural hubs
```

Output goes to `graphify-out/` (`graph.json`, `GRAPH_REPORT.md`, `graph.html`), which is
gitignored because it is fully regenerable.

**Use it when it pays off, not by reflex.** Graphify fits architectural, dependency and
impact questions. For a single known file or symbol, reading it directly is cheaper.

**No LLM calls, verified 2026-09-24.** `graphify update` is tree-sitter AST extraction.
Community labelling would call an LLM only if an API key is present, and
`graphify.llm.detect_backend()` returned `None` on this machine. The `claude-cli` backend is
opt-in and never auto-selected. Do **not** pass `--backend`, and do not run the `/graphify`
skill's semantic doc extraction, without asking the user: both send content to an LLM.

When to rebuild: after significant structural changes (new modules, moved/renamed
classes, changed interfaces), and at every phase completion (section 20, step 9).
