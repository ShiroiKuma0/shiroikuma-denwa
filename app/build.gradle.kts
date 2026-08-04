import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

// The build counter is always written zero-padded to three digits (+001, +050). File lists sort
// lexicographically, and an unpadded counter sorts wrongly — +10 lands before +3, burying the newest
// build in the middle of ~/tmp, of the phone's file manager and of the release list. Padding is text
// only: gradle.properties keeps BUILD_NUMBER a plain integer, and so does versionCode.
// Everything named after a build — versionName, archivesName, the buildFoss APK, and the release tag
// derived from it — comes from this one string, so they can never disagree.
val forkBuildNumber = project.property("BUILD_NUMBER").toString().toInt()
val forkVersionName = "${project.property("VERSION_NAME")}+${"%03d".format(forkBuildNumber)}"

base {
    archivesName = "shiroikuma-denwa_${forkVersionName}_arm64-v8a"
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = forkVersionName
        versionCode = project.property("VERSION_CODE").toString().toInt()
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_NAMESPACE").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

tasks.register("buildFoss") {
    description = "Build foss release APK, copy to ~/tmp, and adb push to phone"
    dependsOn("assembleFossRelease")
    doLast {
        val apkName = "shiroikuma-denwa_${forkVersionName}_arm64-v8a.apk"
        val outputDir = layout.buildDirectory.dir("outputs/apk/foss/release").get().asFile
        val targetDir = File(System.getProperty("user.home"), "tmp")
        targetDir.mkdirs()
        outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            val targetFile = File(targetDir, apkName)
            apk.copyTo(targetFile, overwrite = true)
            println("\u001b[1;36m>>> ~/tmp/$apkName\u001b[0m")

            ProcessBuilder("bash", "-c", """
                for i in 1 2 3; do paplay /usr/share/sounds/freedesktop/stereo/complete.oga; done
                echo -e '\033[1;33;5m>>> CONNECT YOUR PHONE VIA USB AND ENABLE ADB <<<\033[0m'
                read -p ${'$'}'\033[1;33m>>> Push to phone? (y/n) \033[0m' ans
                if [[ "${'$'}ans" =~ ^[Yy]${'$'} ]]; then
                    adb shell mkdir -p /sdcard/tmp
                    adb push '${targetFile.absolutePath}' '/sdcard/tmp/$apkName'
                    echo -e '\033[1;32m>>> Pushed to /sdcard/tmp/$apkName\033[0m'
                else
                    echo "Skipped adb push."
                fi
            """.trimIndent()).inheritIO().start().waitFor()
        }

        // Auto-increment BUILD_NUMBER for next build
        val propsFile = rootProject.file("gradle.properties")
        val currentBuildNumber = project.property("BUILD_NUMBER").toString().toInt()
        val nextBuildNumber = currentBuildNumber + 1
        propsFile.writeText(propsFile.readText().replace(
            "BUILD_NUMBER=$currentBuildNumber",
            "BUILD_NUMBER=$nextBuildNumber"
        ))
        println("\u001b[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber\u001b[0m")
    }
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.androidx.documentfile)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.autofit.text.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.eventbus)
    implementation(libs.libphonenumber)
    implementation(libs.geocoder)
    detektPlugins(libs.compose.detekt)
}
