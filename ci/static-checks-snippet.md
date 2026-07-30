# Adding the static check to CI

My credentials cannot modify `.github/workflows/`, so this one is a manual
paste - the same reason you moved `ci/github-build.yml` into place yourself.

Open `.github/workflows/build.yml` and add this step **immediately above**
`- name: Set up JDK 17`:

```yaml
      # Runs before the toolchain is even set up, because it takes under a
      # second and catches the class of mistake that has broken this build
      # four times: a symbol used with no import. Failing here costs 20
      # seconds instead of three minutes.
      - name: Static checks
        run: python3 tools/check_kotlin.py
```

That is the whole change. `python3` is already on the `ubuntu-latest`
runner, so nothing needs installing.

## Why bother, given the compiler exists

Because CI went red at `7310c30` and stayed red for four commits without
either of us noticing, while you kept hitting the errors one at a time on
your own machine. The check runs before the JDK is installed, so a missing
import fails the run in about twenty seconds with the exact line number,
instead of three minutes into a Gradle build.

It also runs locally now - `recto-build.ps1` calls it before Gradle, and
skips it silently if Python is not on PATH.

## Worth doing at the same time

CI failures are currently invisible unless someone opens the Actions tab.
On github.com, go to your avatar -> Settings -> Notifications -> Actions,
and set it to notify on failure only. Then a red build emails you instead
of waiting for the next time you try to compile.
