# CLEANUP.md

How to remove everything this project added to the machine, without touching anything that
existed beforehand.

**Do not run any of this until the project is finished.**

This file is derived from `ENVIRONMENT.md` and must be updated whenever the environment
changes. It is maintained continuously so that cleanup is never reconstructed from memory.

---

## Safety rules

1. **Never remove anything classified `PRE-EXISTING`.**
2. **Never automatically remove anything classified `UNKNOWN`** — ask first.
3. Work top to bottom; each step is independent and reversible by reinstalling.
4. Before removing the SDK, confirm no other project on this machine depends on it. The SDK
   was installed at a **non-default path** specifically to make this easy to reason about —
   Android Studio would have used `%LOCALAPPDATA%\Android\Sdk`, which this project never
   created or touched.

---

## Inventory classification summary

### PRE-EXISTING — preserve

| Component | Note |
|---|---|
| Windows 11 Home Single Language 10.0.26200 | OS |
| Python 3.11.9 + its 27 packages | Whisper/transcription environment, unrelated |
| `torch 2.11.0+cpu`, `numpy 2.4.4`, `openai-whisper`, `numba`, … | part of the above |
| Node.js v24.16.0 | unrelated |
| git 2.54.0.windows.1 | unrelated |
| winget v1.29.380 | OS component |
| Global git configuration | **never modified** by this project |

### PROJECT-INSTALLED — safe to remove

| Component | Location | Approx size |
|---|---|---|
| Eclipse Temurin JDK 17.0.20.1 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` | ~300 MB |
| Android SDK (all components) | `C:\Users\jassu\Android\Sdk` | ~5.8 GB |
| Android SDK licence files | `C:\Users\jassu\Android\Sdk\licenses` | 11 KB |
| Gradle distribution + caches | `C:\Users\jassu\.gradle` | ~1–2 GB once built |
| AVD images and config | `C:\Users\jassu\.android\avd` | ~2–8 GB once created |
| ONNX model assets | `app\src\main\assets\models\` | 16.1 MB |
| LFW dataset cache | `tools\.cache\`, `tools\dataset_out\` | ~200–400 MB |
| Project-local Python virtualenv (holds Graphify + its 32 packages) | `.venv\` | ~100 MB |
| Graphify skill, CLAUDE.md pointer | `.claude\skills\graphify\`, `.claude\CLAUDE.md` (hooks already removed by user) | ~90 KB |
| Graphify output | `graphify-out\` | small; regenerable |
| Downloaded installer temp | `C:\Users\jassu\Android\sdk-tmp` | ~143 MB |

### PROJECT-MODIFIED — revert, don't remove

| Component | Modification | How to revert |
|---|---|---|
| (none so far) | | |

Git identity was set **repository-locally**, not globally, so there is nothing to revert.

### UNKNOWN — do not touch

| Component | Why unclear |
|---|---|
| (none identified) | |

---

## Cleanup procedure

Run from a normal shell. Each block is independent.

### 0. Remove Graphify (do this first, while its uninstaller still exists)

`uninstall --purge` removes the skill, its PreToolUse hooks and `graphify-out/`. Run it
**before** deleting `.venv`, because the uninstaller lives inside the venv:

```bash
cd "C:/Users/jassu/Downloads/Patrick"
./.venv/Scripts/graphify.exe uninstall --purge
```

Then confirm `.claude/settings.json` no longer contains `hook-guard`, and delete the venv:

```bash
rm -rf "C:/Users/jassu/Downloads/Patrick/.venv"
```

Graphify was installed **only** into this venv. The system Python was never touched, so
there is no `pip uninstall` to run against it.

### 1. Remove the temporary SDK installer download

```bash
rm -rf "C:/Users/jassu/Android/sdk-tmp"
```

### 2. Remove AVDs created by this project

List first, and only delete AVDs this project created:

```bash
"C:/Users/jassu/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat" list avd
```

```bash
"C:/Users/jassu/Android/Sdk/cmdline-tools/latest/bin/avdmanager.bat" delete avd -n patrick_api36
```

### 3. Remove the Android SDK

Only if no other project uses it:

```bash
rm -rf "C:/Users/jassu/Android"
```

### 4. Remove the JDK

Only if nothing else on the machine needs a JDK — check first, since Java is a common
shared dependency:

```bash
winget uninstall --id EclipseAdoptium.Temurin.17.JDK
```

### 5. Remove Gradle caches

These are shared by **all** Gradle projects on the machine. Remove only if this was the
only one:

```bash
rm -rf "C:/Users/jassu/.gradle"
```

### 6. Remove project-local artefacts

Removed automatically if the project directory itself is deleted:

```bash
rm -rf "C:/Users/jassu/Downloads/Patrick/app/build" "C:/Users/jassu/Downloads/Patrick/build"
rm -rf "C:/Users/jassu/Downloads/Patrick/app/src/main/assets/models"
rm -rf "C:/Users/jassu/Downloads/Patrick/tools/.cache" "C:/Users/jassu/Downloads/Patrick/tools/dataset_out"
rm -rf "C:/Users/jassu/Downloads/Patrick/.venv"
rm -rf "C:/Users/jassu/Downloads/Patrick/graphify-out"
```

### 7. Remove data pushed to the test device

If a physical phone was used, the pushed dataset and the app itself remain on it:

```bash
adb shell rm -rf /sdcard/Android/data/com.patrick.faceid
adb uninstall com.patrick.faceid
```

### 8. Environment variables

This project set `JAVA_HOME` and `ANDROID_HOME` **per-command only**. No persistent user or
system environment variable was created. If any were added manually later, remove them here.

---

## Verification after cleanup

```bash
java -version        # expect: not found (if step 4 was run)
adb version          # expect: not found
python --version     # expect: 3.11.9  <- MUST still work, it is PRE-EXISTING
node --version       # expect: v24.16.0 <- MUST still work, it is PRE-EXISTING
git --version        # expect: 2.54.0   <- MUST still work, it is PRE-EXISTING
```

The last three confirm that cleanup did not overreach.

---

## Change log

| Date | Change |
|---|---|
| 2026-09-24 | Created alongside the M0 toolchain install. |
| 2026-09-24 | Added Graphify + project `.venv` removal (step 0); corrected venv path from `tools\.venv` to `.venv`. |
