---
name: build-apk
description: Build the signed foss release APK with the buildFoss Gradle task, then always ask whether to push it to the connected phone via adb. Use whenever the user asks to build the app, build the APK, make a release build, or build and push to the phone.
---

# Build the foss release APK and optionally push to phone

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-denwa_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).

2. **Build:**
   - `./gradlew buildFoss < /dev/null`  (the `< /dev/null` guarantees it never blocks on stdin — see caveat)
   - This runs `assembleFossRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> ~/tmp/<apk name>`; use that line to confirm the exact filename, and confirm `BUILD SUCCESSFUL`.

3. **Always ask** (via AskUserQuestion) whether to push the APK to the phone — every build, no assuming. Options: "Yes, push via adb" / "No, just build".

4. **If yes, push directly yourself** (do NOT rely on the buildFoss task's own prompt — see caveat):
   - `adb devices` — confirm a device is connected.
   - `adb shell mkdir -p /sdcard/tmp`
   - `adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>`
   - Verify: `adb shell ls -l /sdcard/tmp/<apk name>` (size should match the local file in `~/tmp`).

## Caveat — why push directly instead of via the task

The `buildFoss` task (`app/build.gradle.kts`) has an interactive `read -p "Push to phone? (y/n)"` prompt, but it runs in a subprocess of the **Gradle daemon**, whose stdin/stdout are not connected to Claude's Bash tool. Piping `y`/`n` into `./gradlew buildFoss` does not reach the prompt — the daemon subprocess gets EOF, silently skips the push, and its output is invisible. So the task's prompt is effectively dead under this tooling: ask the user via AskUserQuestion and run `adb push` yourself.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from `keystore.properties` (falling back to `SIGNING_*` env vars). If neither is present the build is unsigned and the APK will not install.

## Prerequisite — patched Commons in mavenLocal

This app builds against our patched Fossify Commons (`commons = "6.1.6-sk1"` in
`gradle/libs.versions.toml`), resolved from `mavenLocal()` (`~/.m2`). On this machine it is already
published, so `buildFoss` just works. **On a fresh machine, or if `~/.m2` was cleared**, the build fails
with `Could not resolve org.fossify:commons:6.1.6-sk1` — publish it first:

```bash
cd ~/git/shiroikuma-commons && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-sk1
```

See the `shiroikuma-commons` repo's CLAUDE.md for the patch details.
