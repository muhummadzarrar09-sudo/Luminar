# Build setup: which path, and why

**Short answer: (b) *and* the scripts. They aren't alternatives — they solve
different problems, and you want both.**

---

## Why this isn't an either/or

I framed it as a choice last turn. That was wrong, and looking at your old
`luminar-run.ps1` made it obvious why.

- **Path (b) — Android Studio scaffolding** solves the *bootstrap* problem: producing a
  `gradlew.bat`, a wrapper JAR, `local.properties` and an SDK layout that are known-good
  on your machine. It's a one-time thing.
- **The `.ps1` scripts** solve the *daily loop* problem: build, install to the phone,
  read the logs, understand failures. That's every day, forever.

Your old script was doing both jobs, and it shows — a third of it is
hand-assembling a `gradlew.bat` from string concatenation, because there was no
Studio-generated one to rely on. That's the part that should go away.

## What your old script tells me about your machine

Reading `luminar-run.ps1` from `archive/luminar-v1`:

| Evidence | What it implies |
|---|---|
| It bootstraps `gradlew.bat` from scratch, and even downloads the whole Gradle distribution as a fallback | You probably **don't** have Android Studio generating projects for you |
| It ends with "open Explorer, send the APK via WhatsApp / Drive / cable" | **adb wasn't set up** — you were transferring APKs by hand |
| `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-17...` in `gradle.properties` | You have **Temurin JDK 17**, and you hit the JDK-25 problem and pinned around it |
| `android.builtInKotlin=false` + `android.newDsl=false` | You were on **AGP 9** and hit the KSP incompatibility head-on |
| A `docs/BUILD_FIX.md` telling you to delete three files | The build broke in ways that needed manual archaeology |

So: Windows, Temurin 17, AGP 9, no adb loop, and a history of Gradle pain. That's
exactly who the doctor script is for.

## The two things that bit you last time — now avoidable

### 1. The KSP / AGP 9 fight

Your old `gradle.properties` had this, with an apologetic comment:

```properties
android.builtInKotlin=false
android.newDsl=false
```

Those flags were a real workaround for a real bug: **AGP 9 has built-in Kotlin, and KSP
wasn't compatible with it**, so applying `org.jetbrains.kotlin.android` was the only way
to get Room and Hilt annotation processing to run.

**That's fixed now.** The current compatibility status:

| Component | Minimum version that works with AGP 9 built-in Kotlin |
|---|---|
| KSP | **2.3.1+** (fixed in 2.0.3, reverted in 2.0.4, fixed again in 2.3.1) |
| Hilt / Dagger | **2.59+** |
| Compose Multiplatform | 1.9.3+ (not needed here) |

So Phase 0 will pin KSP ≥ 2.3.1 and Hilt ≥ 2.59, **not** apply the
`org.jetbrains.kotlin.android` plugin at all, and ship a `gradle.properties` with
**none** of those legacy flags. Both are deprecated and stop working in AGP 10, so
carrying them forward would be borrowing trouble.

Note the corollary: with built-in Kotlin, `android.kotlinOptions { jvmTarget = "17" }`
is gone. It becomes `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.

### 2. The JDK version trap

Your comment said it plainly: *"JDK 25 forces a Kotlin JVM_24 fallback."* You solved it
by hard-coding an absolute Windows path into `gradle.properties`.

That works, but it's brittle — it breaks for anyone else, and it breaks when you update
the JDK. The doctor script detects your JDK situation instead, and if a pin is genuinely
needed, it goes in `local.properties` (already git-ignored, machine-specific) rather than
in a committed file.

## Recommended sequence

**Step 1 — run the doctor.** Changes nothing, just looks around.

```powershell
cd path\to\Luminar
powershell -ExecutionPolicy Bypass -File .\scripts\recto-doctor.ps1
```

It checks: OS, disk, JDK (all of them, not just the one on PATH), `JAVA_HOME`,
Android Studio, SDK platforms, build-tools, adb, connected devices, SDK licenses, git.
It writes `scripts\doctor-report.txt`. **Paste that back to me.**

**Step 2 — depends on what the doctor finds:**

- **Android Studio present** → create an "Empty Activity" project in Studio, in a temp
  folder, with package `dev.recto.reader`. Copy its `gradlew`, `gradlew.bat`,
  `gradle/wrapper/` into the repo and push. Then I build everything else on a foundation
  that provably compiles on *your* machine.
- **No Android Studio** → I generate the whole scaffold, wrapper properties included,
  and `recto-build.ps1` fetches the wrapper JAR on first run (same trick your old script
  used, minus the hand-built batch file).

**Step 3 — from then on, the daily loop is one command:**

```powershell
.\scripts\recto-build.ps1
```

## What `recto-build.ps1` fixes vs. the old one

| Old `luminar-run.ps1` | New `recto-build.ps1` |
|---|---|
| Opens Explorer, you transfer the APK by hand | **`adb install` + auto-launch**, with the Explorer path only as fallback |
| Hand-writes `gradlew.bat` via string concatenation | Downloads the real wrapper JAR; expects a committed `gradlew.bat` |
| "Build failed. Check output above." | **Five ranked likely causes**, including the exact AGP 9 / KSP trap you hit |
| No log access | **Tails logcat filtered to the app's PID** after launch |
| Debug only | `-Release`, `-Clean`, `-Offline`, `-NoInstall`, `-NoLogcat` |
| Hardcoded Gradle 9.5.1 | Reads the version from `gradle-wrapper.properties` |
| No environment validation | Separate `recto-doctor.ps1` that diagnoses before you waste 15 minutes |

## Getting adb working (worth 10 minutes)

This is the single biggest quality-of-life upgrade over your old workflow. Instead of
WhatsApp-ing an APK to yourself every build, it's one command to compile, install and
launch.

1. On the phone: **Settings → About phone → tap "Build number" 7 times**.
2. **Settings → System → Developer options → USB debugging → ON**.
3. Plug into the PC with a cable that does data (not a charge-only cable — this trips
   people up constantly).
4. On the phone, tap **Allow** on the "Allow USB debugging?" prompt.
5. Verify: `adb devices` should list a device with status `device`, not `unauthorized`.

Wireless works too on Android 11+ (`adb pair`), if you'd rather not use a cable.

## Version pins for Phase 0 (verified July 2026)

| Component | Version | Note |
|---|---|---|
| AGP | 9.3.0 | needs Gradle 9.5.0, JDK 17, Build-Tools 36 |
| Gradle | 9.5.0 | AGP 9.3's minimum *and* default |
| Kotlin | 2.3.x | built into AGP 9; still declared for the Compose plugin |
| KSP | ≥ 2.3.1 | **the version that fixes your old workaround** |
| Hilt | ≥ 2.59 | first version compatible with AGP 9 |
| Compose BOM | 2026.06.01 | Material3 1.4.0 stable |
| compileSdk / targetSdk | 36 | Compose 1.12+ will later require 37 |
| minSdk | 28 | the `androidx.pdf` backport floor |
| JDK | 17 | 21 also fine; **25 caused your JVM-target fallback** |

## A note on where these scripts came from

I wrote them in a sandbox with **no JDK, no Android SDK, and no network access to
`dl.google.com`**. So they are carefully written and internally consistent, but they have
never been executed. `recto-doctor.ps1` is read-only and safe by construction.
`recto-build.ps1` won't do anything useful until Phase 0 exists — it exits with a clear
message if `settings.gradle.kts` is missing.

If PowerShell blocks them, that's the default execution policy, not a problem with the
files:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\recto-doctor.ps1
```
