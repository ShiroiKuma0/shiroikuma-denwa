---
name: build-apk
description: Build the signed foss release APK with the buildFoss Gradle task, then deliver it automatically via the global /after-build skill (adb push if a phone is connected, else scp to skhw — no prompt). Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the foss release APK and optionally send to phone

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-denwa_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).

2. **Build:**
   - `./gradlew buildFoss < /dev/null`  (the `< /dev/null` guarantees it never blocks on stdin — see caveat)
   - This runs `assembleFossRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> ~/tmp/<apk name>`; use that line to confirm the exact filename, and confirm `BUILD SUCCESSFUL`.

3. **Deliver via `/after-build`** — do NOT ask how to transfer. After a successful build leaves the signed APK in `~/tmp/`, invoke the global **`/after-build`** skill: it runs **`/adb-check`** UNSANDBOXED (a sandboxed check wrongly reports no device), then **`/adb-push`** to `/sdcard/tmp/` if a phone is connected, otherwise **`/scp`** to `skhw:~/tmp/`, announcing the filename that landed. Never prompt "Scp or adb push?" / "Phone connected?" — `/after-build` decides on its own. (Do NOT rely on the buildFoss task's own prompt — see caveat.)

4. **What `/after-build` does** (for reference — you don't run these by hand): `/adb-check` lists devices UNSANDBOXED; if a phone is connected, `/adb-push` copies the newest `~/tmp/*.apk` to `/sdcard/tmp/`; otherwise `/scp` copies it to `skhw:~/tmp/`. Never `adb install` — the user installs manually from `/sdcard/tmp/`.

## Caveat — why transfer directly instead of via the task

The `buildFoss` task (`app/build.gradle.kts`) has an interactive `read -p "Push to phone? (y/n)"` prompt, but it runs in a subprocess of the **Gradle daemon**, whose stdin/stdout are not connected to Claude's Bash tool. Piping `y`/`n` into `./gradlew buildFoss` does not reach the prompt — the daemon subprocess gets EOF, silently skips the push, and its output is invisible. So the task's prompt is effectively dead under this tooling: delivering the APK via the `/after-build` skill is Claude's job.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from `keystore.properties` (falling back to `SIGNING_*` env vars). If neither is present the build is unsigned and the APK will not install.

## Prerequisite — patched Commons in mavenLocal

This app builds against our patched Fossify Commons (`commons = "6.1.6-sk2"` in
`gradle/libs.versions.toml`), resolved from `mavenLocal()` (`~/.m2`). On this machine it is already
published, so `buildFoss` just works. **On a fresh machine, or if `~/.m2` was cleared**, the build fails
with `Could not resolve org.fossify:commons:6.1.6-sk2` — publish it first:

```bash
cd ~/git/shiroikuma-commons && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-sk2
```

See the `shiroikuma-commons` repo's CLAUDE.md for the patch details.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
