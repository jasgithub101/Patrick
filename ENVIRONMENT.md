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
                    .claude/settings.json      (two PreToolUse hooks - see open issue below)
                    CLAUDE.md                  (a `## graphify` section; since replaced by the
                                                project CLAUDE.md, Appendix B)
How to remove it:   .venv/Scripts/graphify.exe uninstall --purge   (removes skill, hooks,
                    graphify-out/), then delete .venv if nothing else uses it.
Current status:     Installed and registered. Initial graph NOT yet generated (see below).
Classification:     PROJECT-INSTALLED
```

**Open issue — hook command cannot resolve `graphify`.** For project-scoped installs Graphify
deliberately writes the bare command `graphify hook-guard search|read` so the committed config
stays portable. Here `graphify` is inside `.venv` and not on PATH, so these PreToolUse hooks
cannot run. They are non-blocking (non-strict mode always returns `decision: allow`), so the
expected effect is a failed hook and a little overhead on each Bash/Grep/Read/Glob call, not
blocked work. A fix that points the hooks at `$CLAUDE_PROJECT_DIR/.venv/...` and fails
silently when the venv is absent was prepared, but editing `.claude/settings.json` and running
the first `graphify update` were both **denied by the auto-mode safety classifier as
self-modification**. They await a user decision. `.claude/settings.json` is intentionally
left uncommitted until then.

---

## 3. Project-local dependencies (not system installations)

Declared in Gradle and resolved into caches — these do **not** modify the system and go away
with the project directory plus the Gradle cache.

| Dependency | Version | Purpose |
|---|---|---|
| Android Gradle Plugin | 8.13.2 | Android build |
| Gradle (via wrapper) | 8.14.3 | Build tool; downloaded to `~/.gradle`, not installed system-wide |
| Kotlin | 2.1.21 | Language / compiler |
| KSP | 2.1.21-2.0.2 | Room annotation processing |
| ONNX Runtime Android | 1.30.0 | On-device inference for detector + embedder |
| CameraX | 1.6.2 | Camera capture |
| Room | 2.8.5 | SQLite persistence |
| Jetpack Compose | BOM 2026.09.00 | UI |
| DataStore Preferences | 1.2.1 | Configurable thresholds |

Gradle's own caches live in `C:\Users\jassu\.gradle` — see `CLEANUP.md`.

---

## 4. Model files

| File | Size | Source | Status |
|---|---|---|---|
| `det_500m.onnx` | 2.52 MB | InsightFace `buffalo_sc.zip`, release v0.7 | Fetched by the `fetchModels` Gradle task into `app/src/main/assets/models/`; **gitignored** |
| `w600k_mbf.onnx` | 13.62 MB | same | same |

Removed by deleting `app/src/main/assets/models/`. Not committed to git.

---

## 5. Not yet installed (recorded here when they happen)

- Python packages for `tools/prepare_dataset.py` (expected `pyarrow`, `pillow`) — will be
  installed into the existing project `.venv` (section 2.6), not the system Python.
- LFW dataset cache (~188 MB parquet from the Hugging Face mirror).

---

## Change log

| Date | Change |
|---|---|
| 2026-09-24 | Baseline recorded. JDK 17, Android cmdline-tools, SDK licences installed. SDK component install started. |
| 2026-09-24 | SDK components verified installed (API 36, build-tools 36.0.0, emulator 37.1.11.0, platform-tools 37.0.1). |
| 2026-09-24 | Project `.venv` created; Graphify 0.9.67 installed into it and registered project-scoped. Hook path issue open. |
