# PROJECT_STATE.md

Short, always-current state of the project. Updated continuously.

**Last updated:** 2026-09-24
**Current phase:** Phase 1 — on-device face identification prototype
**Current milestone:** Phase 1 closeout — M0-M6 all complete

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
| Face detection (ML Kit) | `IMPLEMENTED` `TESTED` `MEASURED` — 70/70 eval images, E2 |
| Face alignment + embedding (MobileFaceNet 512-d) | `IMPLEMENTED` `TESTED` `MEASURED` — E2 |
| Room database (person / embedding / audit, schema v1 exported) | `IMPLEMENTED` `TESTED` |
| Similarity search (brute-force cosine, per-person max / mean-top-2, model-version guard) | `IMPLEMENTED` `TESTED` `MEASURED` — 10 JVM tests + E2 |
| Decision engine (MATCH/UNCERTAIN/UNKNOWN) | `IMPLEMENTED` `TESTED` `MEASURED` — 12 JVM tests + E2 |
| Registration flow (configurable images, default 5; dummy ABHA IDs) | `IMPLEMENTED` `TESTED` |
| Duplicate detection (warns, never auto-merges) | `IMPLEMENTED` `TESTED` |
| Quality checks (7 gates, actionable messages) | `IMPLEMENTED` `TESTED` `MEASURED` |
| Runtime-configurable thresholds (DataStore) | `IMPLEMENTED` — no UI yet |
| Per-stage latency measurement | `IMPLEMENTED` `MEASURED` (emulator only) |
| CameraX live preview + capture | `IMPLEMENTED` `TESTED` on emulator |
| Compose demo UI (4 screens + Technical Details) | `IMPLEMENTED` `TESTED` on emulator |
| Android Studio 2026.1.4.7 | Installed; reuses existing SDK/JDK/AVD. **Not yet launched** |

## What is currently being worked on?

**Where:** locally on the laptop. A cloud-session move was attempted, but the account has no
cloud environment, and the user chose to continue locally.

**All milestones M0-M6 are complete.** What remains is Phase 1 closeout: a live MATCH
demonstrated on camera by the user, and the final report.

## What works?

- **Recognition works end to end on real faces** (`MEASURED`, emulator, report E2):
  10 enrolled identities x 5 images, rank-1 **10/10** correct on held-out probes, and
  **0 false matches** on 10 never-enrolled people. Same-person similarity mean 0.623 vs
  different-person mean 0.029, with no overlap on this small set.
- **Registration and persistence work**: a person is stored with several embeddings, survives an
  app restart (verified by closing and reopening the real database file), and is still recognised
  afterwards. Embedding vectors round-trip bit-for-bit.
- **Duplicate registration is caught**: a warning is raised, nothing is written, and identities
  are never merged automatically. An operator can override with `force`.
- **Quality gates work on real data**: 67/70 ordinary photos accepted; blurred, dark and
  multi-person inputs rejected with actionable messages; a rejected image is never embedded.
  Thresholds were calibrated from a measured survey (E3) after the first guesses rejected 31% of
  ordinary photos.
- **Full pipeline latency on the emulator: 94 ms** (detect 59, quality 7, align 14, embed 14).
  Not a phone figure.
- **A working demo app**: live camera preview, guided 5-sample registration, identification with
  MATCH / UNCERTAIN / UNKNOWN, an employee list, and an expandable Technical Details panel.
  Registration verified end to end on a real face (report E5): 5 samples accepted with measured
  quality 0.760 and 0.678, saved as DUMMY-42881341226468.
- **A 100-person gallery works** (report E6): 100/100 people enrolled, rank-1 228/228 on
  held-out probes, 0 wrong matches, and all 43 stranger probes correctly refused.
- **97 tests pass** (62 JVM + 35 instrumented), 0 failures.
- Emulator latency: detection 88 ms median, embedding 15 ms median. **Not** phone figures.
- **Not yet built:** quality checks (M4), bulk 100-person enrolment (M5), camera and UI (M6).

## What doesn't work?

Nothing known broken. Untested: the camera path (M6).

**Not yet demonstrated: a live MATCH.** Registration and rejection are verified on camera, but
confirming a MATCH against a real face needs someone in front of the camera.

Known rough edges:
- An over-exposed face is rejected as "no face detected" rather than "move out of direct light",
  because the detector fails before the brightness gate (E4).
- Detection on a full-resolution camera frame took about 1.5 s on the emulator versus 59 ms on
  small LFW images. Worth downscaling before detection; deferred to Phase 2.

The placeholder `matchThreshold` of 0.5 is **too high** for this model: E2 shows it would
false-reject a genuine probe scoring 0.441. Thresholds stay uncalibrated until Phase 2/3.

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

Revised build order (report D13: images first, camera last):

1. ~~**M2:** ML Kit detection, alignment, embedding, matcher, decision engine.~~ **Done** (E2).
2. ~~**M3:** Room database, multi-embedding registration, dummy ABHA IDs, persistence.~~ **Done.**
3. ~~**M4:** quality checks and runtime-configurable thresholds.~~ **Done** (E3, E4).
4. ~~**M5:** bulk-enrol the ~100-person gallery and measure search at scale.~~ **Done** (E6).
5. ~~**M6:** CameraX + guided registration/identify UI.~~ **Done** (E5), brought forward.

**To close Phase 1:** the user demonstrates a live MATCH and a live UNKNOWN on camera, then the
final report is written. Everything else is finished.

## Most important finding

As the gallery grew from 10 to 100 people, the closest a stranger came to being wrongly accepted
rose from 0.242 to **0.315**, against a match threshold of 0.35. The safety margin fell from
0.108 to **0.035**. This is the open-set effect in action: more enrolled people means more chances
some stranger resembles one of them. **Thresholds calibrated at one gallery size do not transfer
to another**, which is now evidence rather than theory, and it shapes Phase 3.

## Other findings

The InsightFace pretrained weights chosen for Phase 1 are licensed for **non-commercial
research only** (`OBSERVED`, InsightFace README). Fine for this prototype. It blocks any real
deployment using these weights, so a replacement model is a hard requirement before
production.

## Honest limitations of the current state

Nothing has been measured. No accuracy, latency or recognition claim exists yet, and none
should be inferred from this document.
