# CI workflow (needs one manual step)

`github-build.yml` is a GitHub Actions workflow that builds the debug APK on
every push and uploads it as a downloadable artifact.

**It is parked here instead of in `.github/workflows/` because the automation
credentials for this session lack the `workflows` permission — GitHub refuses
pushes that create or modify workflow files.**

## Why you want it

This sandbox has no JDK and no Android SDK, so I cannot compile anything here.
CI gives us a real compiler: every push gets built on a clean Ubuntu runner with
JDK 17, and the resulting APK is downloadable from the Actions tab. That means
build breakage is caught without you having to run anything locally, and you can
grab an APK straight from GitHub on your phone.

## Enabling it (30 seconds)

```bash
mkdir -p .github/workflows
git mv ci/github-build.yml .github/workflows/build.yml
git commit -m "Enable CI"
git push
```

Then open the **Actions** tab on GitHub. The first run takes ~5 minutes; later
runs are faster thanks to the Gradle cache.

Note the workflow runs `gradle wrapper --gradle-version 9.5.0` before building,
because `gradle-wrapper.jar` is not committed to this repo.
