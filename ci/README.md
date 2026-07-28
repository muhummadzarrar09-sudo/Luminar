# CI — enable it once, then forget about it

`github-build.yml` compiles the debug APK on every push and uploads it as a
downloadable artifact.

**Why you want this:** the code in this repo is written in an environment with
no JDK and no Android SDK, so nothing is compiled before it reaches you. CI is
the only thing standing between a typo and a failed build on your laptop. It
also means you can download a working APK straight from GitHub on your phone,
without a cable.

**Why it isn't already enabled:** GitHub blocks pushes that create or modify
files under `.github/workflows/` unless the pushing credential has the
`workflow` permission. The automation credential used for this repo doesn't
have it. You do — so this is a one-time move you make locally.

---

## One-time setup

Run these from the repo root: `D:\fun projects out of boredom\Recto`

### 1. Get up to date first

```powershell
git checkout arena/019f9f64-luminar
git pull
```

If `git pull` complains about local changes you don't want, throw them away
with `git checkout -- .` first. If it complains about changes you *do* want,
`git stash`, pull, then `git stash pop`.

### 2. Move the workflow into place

```powershell
mkdir .github\workflows
git mv ci\github-build.yml .github\workflows\build.yml
```

`git mv` rather than plain `move`, so git records it as a rename instead of a
delete plus an untracked file.

### 3. Commit and push

```powershell
git add -A
git commit -m "Enable CI"
git push origin arena/019f9f64-luminar
```

That's it. Open the **Actions** tab on GitHub and you'll see the first run
start. Roughly 5 minutes the first time, faster afterwards once Gradle's
dependency cache warms up.

---

## The everyday loop from here

```powershell
# 1. Get my latest work
git pull

# 2. Build and install on your phone
powershell -ExecutionPolicy Bypass -File .\scripts\recto-build.ps1

# 3. If you changed something and want it saved
git add -A
git commit -m "what you changed"
git push origin arena/019f9f64-luminar
```

**Always `git pull` before you start**, and **always push to
`arena/019f9f64-luminar`** — that's the branch this whole project lives on.
`main` still holds the original Luminar app.

Bare `git push` works too once the branch is tracking, which it already is.

---

## Reading the results

Repo → **Actions** tab → click the most recent run.

- **Green tick** — it compiles. Scroll to **Artifacts** at the bottom for
  `recto-debug-apk`, a zip containing the installable APK.
- **Red cross** — click the failed job, expand **Assemble debug APK**, and the
  Kotlin errors are in there. Paste them to me exactly as they appear.

You can also trigger a run by hand: **Actions → build → Run workflow**.

---

## What the workflow does, and why

```yaml
on:
  push:
    branches: [ "arena/019f9f64-luminar" ]
```
Only our branch. No wasted minutes on `main`, which is the old app.

```yaml
concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true
```
Pushing twice in a row cancels the first run. No point compiling a commit
that's already been replaced.

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    gradle-version: '9.5.0'
```
**This is the important one.** `gradle-wrapper.jar` is a binary and isn't
committed to this repo, so `./gradlew` doesn't exist on a fresh clone — a
workflow that ran `./gradlew assembleDebug` would fail instantly. Installing
Gradle directly sidesteps the wrapper, which is why the build step calls
`gradle` rather than `./gradlew`.

9.5.0 is AGP 9.3.0's minimum. If `gradle/wrapper/gradle-wrapper.properties`
ever changes version, change it here too.

```yaml
- name: Accept Android SDK licences
  run: yes | sdkmanager --licenses ... || true
```
The runner has an Android SDK but not necessarily API 36. Accepting licences
lets AGP download whatever's missing. `|| true` because `sdkmanager` exits
non-zero when every licence is already accepted — not actually a failure.

```yaml
timeout-minutes: 30
```
A hung Gradle daemon would otherwise sit there burning your free minutes for
six hours.

---

## Cost

Public repos: **free, unlimited**. Private repos on the free plan get 2,000
minutes a month; this build is ~5 minutes, so about 400 pushes. Not a concern
at this pace.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `refusing to allow ... without workflow permission` | Your PAT lacks the `workflow` scope. Use GitHub Desktop, or regenerate the token with `workflow` ticked. |
| `mkdir : already exists` | `.github\workflows` is already there. Skip step 2's mkdir and just do the `git mv`. |
| Workflow doesn't appear in Actions | It must be on the default branch *or* the branch you pushed. Check the file is at exactly `.github/workflows/build.yml`. |
| `SDK location not found` on CI | Shouldn't happen — AGP finds `ANDROID_HOME` on the runner. If it does, tell me and I'll add a `local.properties` step. |
