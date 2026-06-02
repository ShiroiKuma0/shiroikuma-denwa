---
name: upstream-new-version
description: Rebase our fork onto a new upstream release of FossifyOrg/Phone. Use when the user says a new upstream version is out, asks to update/sync to upstream, bump to the new Phone release, or rebase custom onto the latest upstream.
---

# Rebase the fork onto a new upstream release

This codifies the "new upstream version" half of the fork workflow. The goal: move `main` to the new
upstream release, replay our `custom` customizations on top of it, and produce a fresh `+1` build.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** Same hard rules as everyday
> development (see CLAUDE.md). After the rebase + build you stop and let the user test; you only
> `git push` when they explicitly say **"Push"**.

## Background — how versioning works here

- `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` **track upstream**.
- `BUILD_NUMBER` is **our** fork increment. It **resets to `1`** on each new upstream version.
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER>"`, `versionCode` = `VERSION_CODE * 10000 + BUILD_NUMBER`.
- This fork builds against our **patched Fossify Commons** (`commons = "<ver>-sk1"` from `mavenLocal`, the
  `~/git/shiroikuma-commons` fork, which strips Commons' anti-tamper "fake version" / sideloading nags).
  When upstream bumps Commons, the patched fork must be re-cut at the new version — see step 4.

So when upstream's `versionCode` climbs, our fork's codes for the new line all exceed the previous
line's, keeping upgrades monotonic.

## Steps

1. **Fetch upstream:**
   - `git fetch upstream --tags`
   - Identify the new release tag/commit, e.g. `git tag --sort=-creatordate | head` or check
     `upstream/main`. Confirm the new `VERSION_NAME` / `VERSION_CODE` from upstream's `gradle.properties`
     at that tag: `git show <tag>:gradle.properties | grep -E 'VERSION_NAME|VERSION_CODE'`.

2. **Advance `main` to the new upstream release** (it mirrors upstream, no fork work lives there):
   - `git checkout main`
   - `git merge --ff-only upstream/main` (or `git reset --hard <tag>` if tracking an exact tag).

3. **Rebase `custom` onto the new `main`:**
   - `git checkout custom`
   - `git rebase main`
   - Resolve conflicts so **all** our customizations survive (see the table below). The conflict-prone
     files are `gradle.properties`, `app/build.gradle.kts`, and `values*/strings.xml`.

4. **Update the patched Commons if upstream bumped it.** Check whether the rebase changed the Commons
   version in `gradle/libs.versions.toml` (our `custom` commit pins `commons = "<old>-sk1"`; upstream may
   have moved to a newer Commons, e.g. `6.1.6` → `6.2.0`). If the underlying Commons version changed:
   - In `~/git/shiroikuma-commons`: `git fetch upstream --tags`, check out the new tag onto `custom`,
     re-apply the strip patch (remove the modded-app / sideloading checks — see that repo's CLAUDE.md),
     then `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :commons:publishToMavenLocal -PVERSION=<newver>-sk1`.
   - Resolve the `commons` pin conflict in favour of **our `-sk1` suffix at the new version**
     (`<newver>-sk1`), never the bare upstream value.
   If Commons is unchanged, keep the existing `<ver>-sk1` pin (re-publish only if `~/.m2` was cleared).

5. **Update versioning in `gradle.properties`:**
   - Set `VERSION_NAME` / `VERSION_CODE` to the **new upstream** values.
   - **Reset `BUILD_NUMBER` to `1`.**

6. **Verify our customizations are intact** (after resolving the rebase):

   | What | Expected value | Where |
   | --- | --- | --- |
   | Installed app ID | `shiroikuma.denwa` | `gradle.properties` → `APP_ID` |
   | Code namespace | `org.fossify.phone` (unchanged from upstream) | `gradle.properties` → `APP_NAMESPACE` |
   | App launcher label | `白い熊 電話` | `app_launcher_name` in `values/strings.xml` + `values-ja/strings.xml` |
   | Patched Commons pin | `commons = "<ver>-sk1"` (from `mavenLocal`) | `gradle/libs.versions.toml` |
   | Fork version logic | `forkVersionName` / `forkVersionCode` + `buildFoss` task | `app/build.gradle.kts` |
   | `namespace = APP_NAMESPACE` | not `APP_ID` | `app/build.gradle.kts` |

   Sanity check: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleFossRelease --dry-run`
   or a config-only task, to confirm the build script still evaluates.

7. **Build the new `+1`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null`), then **ask** before
   any `adb push`. This is the first build of the new upstream line (`<newVersion>+1`). If the build can't
   resolve `org.fossify:commons:<ver>-sk1`, publish the patched Commons first (step 4).

8. **Stop.** Let the user test. Commit/push only on their explicit **"Push"** (force-push may be needed
   for `custom` since rebasing rewrites history: `git push --force-with-lease origin custom`; `main` is a
   fast-forward). The patched Commons fork (`~/git/shiroikuma-commons`) is pushed separately if it changed.

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history) over
  merging, so the customization set stays easy to audit and replay.
- If upstream restructures a file we customize, port our change to the new structure rather than forcing
  the old diff.
