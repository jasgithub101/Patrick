# ENVIRONMENT.md

Complete environment and installation inventory for the Patrick face-identification project.

**Host inspected:** 2026-09-24
**Machine:** Windows 11 Home Single Language, build 10.0.26200
**Working directory:** `C:\Users\jassu\Downloads\Patrick`

Every entry is classified as one of:

| Class | Meaning |
|---|---|
| `PRE-EXISTING` | Present before this project began. **Never remove during cleanup.** |
| `PROJECT-INSTALLED` | Installed specifically for this project. Safe to remove at project end. |
| `PROJECT-MODIFIED` | Pre-existing, but configuration changed by this project. Revert, don't remove. |
| `UNKNOWN` | Provenance unclear. **Must not be removed automatically.** |

---

## 1. Baseline environment (inspected before any installation)

Recorded exactly as found on 2026-09-24, prior to the first installation.

### Hardware

| Item | Value |
|---|---|
| CPU | AMD Ryzen 7 250 w/ Radeon 780M Graphics — 8 cores |
| RAM | 15.3 GB |
| GPU | NVIDIA GeForce RTX 5050 Laptop GPU; AMD Radeon 780M (integrated) |
| Storage | C: 475 GB total, 205 GB free |
| Camera | Integrated Camera (present) |
| Virtualization | `VirtualizationFirmwareEnabled = True`, `HypervisorPresent = True` |

This is an **AMD** CPU, so Intel HAXM does not apply — the Android emulator must use
**WHPX** (Windows Hypervisor Platform).

### Toolchain present at baseline

| Component | Version | Class | Notes |
|---|---|---|---|
| Windows | 11 Home Single Language 10.0.26200 | `PRE-EXISTING` | |
| Python | 3.11.9 | `PRE-EXISTING` | `C:\Users\jassu\AppData\Local\Programs\Python\Python311` |
| pip | 24.0 | `PRE-EXISTING` | |
| Node.js | v24.16.0 | `PRE-EXISTING` | Not used by this project |
| git | 2.54.0.windows.1 | `PRE-EXISTING` | |
| winget | v1.29.380 | `PRE-EXISTING` | Used as the installer for the JDK |

### Toolchain ABSENT at baseline (verified missing)

| Component | Verification |
|---|---|
| Java / JDK | `java -version` -> command not found; `C:\Program Files\Java` and `C:\Program Files\Eclipse Adoptium` absent |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` absent |
| Android Studio | `C:\Program Files\Android` absent |
| Gradle | `gradle --version` -> command not found |
| adb | `adb version` -> command not found |
| GitHub CLI (`gh`) | `gh --version` -> command not found |
| Chocolatey / Scoop | both absent |

### Pre-existing Python packages (27 total, unrelated to this project)

`certifi 2026.4.22`, `charset-normalizer 3.4.7`, `colorama 0.4.6`, `filelock 3.29.0`,
`fsspec 2026.4.0`, `idna 3.13`, `Jinja2 3.1.6`, `llvmlite 0.47.0`, `lxml 6.1.1`,
`MarkupSafe 3.0.3`, `more-itertools 11.0.2`, `mpmath 1.3.0`, `networkx 3.6.1`,
`numba 0.65.1`, `numpy 2.4.4`, `openai-whisper 20250625`, `pip 24.0`, `python-docx 1.2.0`,
`regex 2026.4.4`, `requests 2.33.1`, `setuptools 65.5.0`, `sympy 1.14.0`, `tiktoken 0.12.0`,
`torch 2.11.0+cpu`, `tqdm 4.67.3`, `typing_extensions 4.15.0`, `urllib3 2.6.3`

All `PRE-EXISTING`. This appears to be a Whisper/transcription environment. **`torch` is
CPU-only** (`torch.cuda.is_available()` returned `False`), so the RTX 5050 is not usable
from Python without reinstalling torch. This project does not require it.

### Network reachability (verified with curl — relevant to dependency sourcing)

| Host | Result |
|---|---|
| `pypi.org` | 200 |
| `github.com` | 200 |
| `huggingface.co` | 200 |
| `dl.google.com/android/repository` | reachable (artifacts return 200) |
| `repo1.maven.org` | 200 |
| **`vis-www.cs.umass.edu`** (canonical LFW host) | **000 — unreachable** |
| `datasets-server.huggingface.co` | unreachable |

The unreachable LFW host is why `sklearn.datasets.fetch_lfw_people` cannot be used here; a
Hugging Face mirror is used instead. See `docs/PHASE_1_REPORT.md`.

---

## 2. Installations made by this project

### 2.1 Eclipse Temurin JDK 17

```
Component:          Eclipse Temurin JDK (OpenJDK)
Version:            17.0.20.1+1  (reported: openjdk 17.0.20.1, 2026-08-18)
Reason:             Required by the Android Gradle Plugin to build the app.
                    No JDK existed on the machine.
Installed by:       Phase 1 (M0)
Date:               2026-09-24
Required by:        Gradle, Android Gradle Plugin, Kotlin compiler, sdkmanager, avdmanager
How it was installed:
                    winget install --id EclipseAdoptium.Temurin.17.JDK --exact --silent
                      --accept-package-agreements --accept-source-agreements
Install location:   C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
How to remove it:   winget uninstall --id EclipseAdoptium.Temurin.17.JDK
Current status:     Active
Classification:     PROJECT-INSTALLED
```

JDK **17** specifically (not 21/24) because it is the version the Android Gradle Plugin 8.x
line is built and tested against.

### 2.2 Android SDK command-line tools

```
Component:          Android SDK command-line tools
Version:            13114758 (commandlinetools-win-13114758_latest.zip, 143 MB download)
Reason:             Provides sdkmanager / avdmanager / apkanalyzer. Chosen over Android
                    Studio because the user opted for a command-line-only toolchain
                    (~4-6 GB instead of ~12-15 GB, and fully scriptable from this session).
Installed by:       Phase 1 (M0)
Date:               2026-09-24
Required by:        All Android SDK component management and AVD creation
How it was installed:
                    Downloaded from https://dl.google.com/android/repository/ and extracted
                    into the SDK root.
Install location:   C:\Users\jassu\Android\Sdk\cmdline-tools\latest   (~158 MB)
How to remove it:   Delete C:\Users\jassu\Android\Sdk
Current status:     Active
Classification:     PROJECT-INSTALLED
```

The SDK was deliberately placed at `C:\Users\jassu\Android\Sdk` rather than the Android
Studio default (`%LOCALAPPDATA%\Android\Sdk`) so that it is unambiguously project-installed
and trivially separable at cleanup.

### 2.3 Android SDK components

```
Component:          Android SDK packages
Versions (verified after install):
                    platform-tools       37.0.1   (adb 1.0.41)          17 MB
                    platforms;android-36                                131 MB
                    build-tools;36.0.0                                  138 MB
                    emulator             37.1.11.0                      1.1 GB
                    system-images;android-36;google_apis;x86_64         4.3 GB
                    ------------------------------------------------------------
                    SDK total on disk (incl. cmdline-tools)             ~5.8 GB
Reason:             Compile, install and run the app. The x86_64 system image is required
                    for the emulator, which is the Phase 1 test target.
Installed by:       Phase 1 (M0)
Date:               2026-09-24
Required by:        Gradle build, adb install/test, AVD
How it was installed:
                    sdkmanager.bat "platform-tools" "platforms;android-36" \
                      "build-tools;36.0.0" "emulator" \
                      "system-images;android-36;google_apis;x86_64"
How to remove it:   sdkmanager --uninstall "<package>", or delete the SDK root
Current status:     Active - all five packages verified present
Classification:     PROJECT-INSTALLED
```

### 2.4 Android SDK licence acceptance

```
Component:          Android SDK licence acceptance files
Reason:             sdkmanager refuses to install packages until licences are accepted.
Installed by:       Phase 1 (M0)
Date:               2026-09-24
How it was installed:
                    yes | sdkmanager.bat --licenses
                    A PowerShell string-pipe attempt failed to feed stdin and the packages
                    silently refused to install. Recorded because it cost a build cycle.
Install location:   C:\Users\jassu\Android\Sdk\licenses\  (7 files)
How to remove it:   Deleted along with the SDK root
Current status:     Active
Classification:     PROJECT-INSTALLED
```

### 2.5 Git repository configuration

```
Component:          git repository + local identity
Reason:             Milestone commits, as required by the project instructions.
Installed by:       Phase 1 (M0)
Date:               2026-09-24
How it was installed:
                    git init; git config user.email ...; git config user.name ...
                    Repository-LOCAL config only - the GLOBAL git config was NOT modified.
                    user.email = 93384756+jasgithub101@users.noreply.github.com (GitHub's
                    private-email address) because the repo is public. The two pre-push
                    commits were rewritten to this address before the first push.
Remote:             origin = https://github.com/jasgithub101/Patrick.git (public)
How to remove it:   Delete the .git directory
Current status:     Active
Classification:     PROJECT-INSTALLED (repo); global git config untouched
```

### 2.6 Project-local Python virtualenv

```
Component:          Python virtual environment (.venv)
Version:            CPython 3.11.9 (created from the PRE-EXISTING system interpreter)
Reason:             Isolate project Python tooling from the pre-existing Whisper
                    environment. A plain `pip install` would have added ~30 packages to
                    the system Python; pipx and uv are not installed, and installing them
                    would itself be a new global tool.
Installed by:       Phase 1 (M0)
Date:               2026-09-24
Required by:        Graphify (now); tools/prepare_dataset.py (planned)
How it was installed:
                    python -m venv .venv
                    .venv/Scripts/python.exe -m pip install --upgrade pip
Install location:   C:\Users\jassu\Downloads\Patrick\.venv   (gitignored)
How to remove it:   Delete the .venv directory
Current status:     Active
Classification:     PROJECT-INSTALLED
Verified isolation: system `pip list` still reports 27 packages after install (unchanged)
```

### 2.7 Graphify

```
Component:          Graphify (PyPI package `graphifyy`)
Version:            0.9.67 (released 2026-09-23), Apache-2.0
Reason:             Development-time codebase knowledge graph for architecture,
                    dependency and impact analysis. Requested by the user.
                    NOT an Android runtime dependency; never referenced by the app build.
Installed by:       Phase 1 (M0)
Date:               2026-09-24
Required by:        Development workflow only (see CLAUDE.md Appendix B)
Pre-install check:  `graphify` not on PATH; no graph* pip packages; no graph* npm globals;
                    no ~/.claude/skills directory -> not previously installed.
How it was installed:
                    .venv/Scripts/python.exe -m pip install "graphifyy==0.9.67"
                    .venv/Scripts/graphify.exe install --project --platform claude
Dependencies pulled into .venv (32 packages total): networkx, numpy, rapidfuzz, tree-sitter
                    and ~25 tree-sitter language grammars (incl. kotlin, java, groovy, json).
LLM usage:          None. `graphify update` is local tree-sitter AST extraction.
                    graphify.llm.detect_backend() -> None on this machine (no API keys;
                    ANTHROPIC_BASE_URL alone is not treated as a key; the claude-cli backend
                    is opt-in and never auto-selected).
Files written into the repository by `install --project`:
                    .claude/skills/graphify/SKILL.md + references/ (8 files, ~85 KB)
                    .claude/CLAUDE.md          (3-line pointer to the skill)
                    .claude/settings.json      (two PreToolUse hooks - REMOVED by user, see below)
                    CLAUDE.md                  (a `## graphify` section; since replaced by the
                                                project CLAUDE.md, Appendix B)
How to remove it:   .venv/Scripts/graphify.exe uninstall --purge   (removes skill, hooks,
                    graphify-out/), then delete .venv if nothing else uses it.
Current status:     Active. Skill registered; hooks removed; initial graph generated.
Classification:     PROJECT-INSTALLED
```

**RESOLVED 2026-09-24 — the user removed the hooks** by deleting `.claude/settings.json` (it
contained only Graphify's two hooks). Graphify is now used on demand, per CLAUDE.md
Appendix B, with no always-on hooks. First graph built with `graphify update .`: 178 nodes,
166 edges, 21 communities; `.venv` correctly excluded. Original issue, kept for history:

**Hook command could not resolve `graphify`.** For project-scoped installs Graphify
deliberately writes the bare command `graphify hook-guard search|read` so the committed config
stays portable. Here `graphify` is inside `.venv` and not on PATH, so these PreToolUse hooks
cannot run. They are non-blocking (non-strict mode always returns `decision: allow`), so the
expected effect is a failed hook and a little overhead on each Bash/Grep/Read/Glob call, not
blocked work. A fix that points the hooks at `$CLAUDE_PROJECT_DIR/.venv/...` and fails
silently when the venv is absent was prepared, but editing `.claude/settings.json` and running
the first `graphify update` were both **denied by the auto-mode safety classifier as
self-modification**. They await a user decision. `.claude/settings.json` is intentionally
left uncommitted until then.

### 2.8 Gradle distribution, caches and wrapper

```
Component:          Gradle 8.14.3 (via wrapper) + dependency caches
Reason:             Build tool for the Android project.
Installed by:       Phase 1 (M1)
Date:               2026-09-24
How it was installed:
                    Official gradle-8.14.3-bin.zip downloaded to the session scratchpad,
                    SHA-256 verified against services.gradle.org
                    (bd711022...2c5f3531), used ONCE to generate gradlew/gradlew.bat/
                    gradle-wrapper.jar in an empty temp dir. The wrapper then downloaded
                    its own copy into ~/.gradle/wrapper/dists on the first build.
Locations:          C:\Users\jassu\.gradle  (wrapper dist, AGP/Kotlin/AndroidX caches, daemon)
                    Scratchpad copy of the distribution is temporary, outside the repo.
How to remove it:   Delete C:\Users\jassu\.gradle - shared by ALL Gradle projects; see CLEANUP.md
Current status:     Active
Classification:     PROJECT-INSTALLED (no ~/.gradle existed at baseline)
```

### 2.9 Android Virtual Device

```
Component:          AVD `patrick_api36`
Version:            Pixel 7 profile, system-images;android-36;google_apis;x86_64
Settings changed from defaults:
                    hw.camera.front=webcam0 (the laptop's Integrated Camera)
                    hw.camera.back=virtualscene, hw.ramSize=4096, hw.keyboard=yes,
                    hw.gpu.enabled=yes, hw.gpu.mode=auto
Acceleration:       `emulator -accel-check` -> "WHPX(10.0.26200) is installed and usable".
                    No Windows feature change or admin step was needed.
Installed by:       Phase 1 (M1)
Date:               2026-09-24
Location:           C:\Users\jassu\.android\avd\patrick_api36.avd (+ patrick_api36.ini)
How to remove it:   avdmanager delete avd -n patrick_api36
Current status:     Active
Classification:     PROJECT-INSTALLED (C:\Users\jassu\.android is created by the SDK tools)
```

### 2.10 Model zip cache

```
Component:          buffalo_sc.zip download cache
Location:           <repo>/.cache/models/buffalo_sc.zip (14.97 MB, gitignored)
Extracted models:   app/build/generated/... (build output; recreated by the build)
How to remove it:   Delete <repo>/.cache
Classification:     PROJECT-INSTALLED
```

### 2.11 Android Studio

```
Component:          Android Studio
Version:            2026.1.4.7 (winget package Google.AndroidStudio)
Reason:             The user needs a professional demo UI to present to their professor, and
                    wants the project openable in the IDE. The command-line toolchain remains
                    fully sufficient to build and test; Studio is additive.
Installed by:       Phase 1 (M6, demo UI)
Date:               2026-09-25
Required by:        Developer convenience only. NOT required by the build: the Gradle wrapper,
                    JDK 17 and the command-line SDK build the APK without it.
Installation method:
                    winget install --id Google.AndroidStudio --exact --silent
                      --accept-package-agreements --accept-source-agreements
Install location:   C:\Program Files\Android\Android Studio   (~3.3 GB)
Removal method:     winget uninstall --id Google.AndroidStudio
                    Then optionally delete %APPDATA%\Google\AndroidStudio*,
                    %LOCALAPPDATA%\Google\AndroidStudio* (settings and caches).
Status:             Installed; not yet launched (first launch runs a GUI setup wizard).
Origin:             PROJECT-INSTALLED
```

**No duplicate SDK, JDK or AVD was created.** Android Studio is pointed at the existing
toolchain rather than installing its own:

| Component | Reused from | How Studio finds it |
|---|---|---|
| Android SDK | `C:\Users\jassu\Android\Sdk` | `local.properties` (`sdk.dir`) plus the `ANDROID_HOME` user variable below |
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` | Gradle JDK setting; Studio also bundles its own JBR, which must NOT be selected |
| Emulator / AVD | `patrick_api36` | Lives in `~/.android/avd`, shared with the command line |
| Gradle / AGP / Kotlin | wrapper 8.14.3 / AGP 8.13.2 / Kotlin 2.2.20 | Studio uses the project's Gradle wrapper by default |

### 2.12 ANDROID_HOME user environment variable

```
Component:          ANDROID_HOME environment variable
Value:              C:\Users\jassu\Android\Sdk
Reason:             Without it, the Android Studio setup wizard offers to download a SECOND
                    Android SDK (~5.8 GB) into its default location. This points it at the
                    existing one.
Date:               2026-09-25
Installation method:
                    [Environment]::SetEnvironmentVariable('ANDROID_HOME', ..., 'User')
Scope:              USER level only. The MACHINE (system) level was deliberately left empty
                    and was verified empty after the change.
Removal method:     [Environment]::SetEnvironmentVariable('ANDROID_HOME', $null, 'User')
Status:             Active
Origin:             PROJECT-MODIFIED (user environment; no pre-existing value was overwritten)
```

---

## 3. Project-local dependencies (not system installations)

Declared in Gradle and resolved into caches — these do **not** modify the system and go away
with the project directory plus the Gradle cache.

| Dependency | Version | Purpose |
|---|---|---|
| Android Gradle Plugin | 8.13.2 | Android build |
| Gradle (via wrapper) | 8.14.3 | Build tool; downloaded to `~/.gradle`, not installed system-wide |
| Kotlin | 2.2.20 | Language / compiler |
| KSP | 2.2.20-2.0.3 | Room annotation processing (applied) |
| ONNX Runtime Android | 1.30.0 | On-device inference for detector + embedder |
| CameraX | 1.5.1 | Camera capture (pinned, not yet used) |
| Room | 2.8.3 | SQLite persistence (in use: person / embedding / audit, schema v1) |
| Jetpack Compose | BOM 2025.10.01 | UI |
| AndroidX core / activity / lifecycle | 1.17.0 / 1.11.0 / 2.9.4 | App framework |
| kotlinx-coroutines | 1.10.2 | Background work |
| DataStore Preferences | 1.1.7 | Runtime-configurable thresholds (in use) |

Why these versions rather than the newest: see `docs/PHASE_1_REPORT.md` decision D11.

Gradle's own caches live in `C:\Users\jassu\.gradle` — see `CLEANUP.md`.

---

## 4. Model files

| File | Size | Source | Status |
|---|---|---|---|
| `det_500m.onnx` | 2.52 MB | InsightFace `buffalo_sc.zip`, release v0.7 | Fetched and SHA-256-verified by `fetch<Variant>Models`; zip cached in `.cache/`, extracted into `app/build/` as a generated assets dir; never committed |
| `w600k_mbf.onnx` | 13.62 MB | same | same |

Removed by deleting `.cache/` and `app/build/`. Licence: **non-commercial research only**.

---

## 5. Not yet installed (recorded here when they happen)

(Nothing pending. Items previously listed here are now recorded below.)

### 5.1 pyarrow (project `.venv`)

```
Component:          pyarrow 21.0.0
Reason:             tools/prepare_dataset.py reads the LFW parquet file
Date:               2026-09-24
Installation method: .venv/Scripts/python.exe -m pip install "pyarrow==21.0.0"
Removal method:     delete .venv (or pip uninstall pyarrow inside it)
Status:             Active
Origin:             PROJECT-INSTALLED; system Python still 27 packages (verified)
```

Pillow was not needed: the parquet stores JPEG bytes, which are written out unchanged.

### 5.2 LFW dataset cache

```
Component:          LFW parquet, Hugging Face logasja/lfw @ 0ee47979927a48dadf11083cb53b51439fa92dc9
Size / checksum:    188,443,388 bytes, SHA-256 40a011f0...2684f91b (verified by the tool)
Derived sets:       .cache/lfw/eval_small/ (70 images), tools/dataset_out/bulk/ (801 images)
Date:               2026-09-24
Removal method:     delete .cache/lfw and tools/dataset_out
Status:             Active; gitignored, never committed
Origin:             PROJECT-INSTALLED
```

---

## Change log

| Date | Change |
|---|---|
| 2026-09-24 | Baseline recorded. JDK 17, Android cmdline-tools, SDK licences installed. SDK component install started. |
| 2026-09-24 | SDK components verified installed (API 36, build-tools 36.0.0, emulator 37.1.11.0, platform-tools 37.0.1). |
| 2026-09-24 | Project `.venv` created; Graphify 0.9.67 installed into it and registered project-scoped. Hook path issue open. |
| 2026-09-25 | M4: quality gates + DataStore settings. Thresholds calibrated from a measured survey (report E3). |
| 2026-09-25 | M2 + M3: ML Kit detection, alignment, embedding, Room persistence, registration. pyarrow + LFW cache recorded in section 5. |
| 2026-09-24 | Gradle 8.14.3 wrapper generated; first successful build; AVD `patrick_api36` created and booted (WHPX usable). |
| 2026-09-24 | User removed Graphify hooks (`.claude/settings.json`). Initial graph built. Git remote `origin` added (public repo `jasgithub101/Patrick`); not yet pushed. |
