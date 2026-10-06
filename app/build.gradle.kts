import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "com.thripleq.nume"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.thripleq.nume"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true

        // libnetease is compiled without curl; the app installs an OkHttp
        // transport over JNI at runtime (see jni_glue.c / NumeTransport).
        externalNativeBuild {
            cmake {
                arguments += listOf("-DNE_USE_CURL=OFF")
                // Build only what the app ships. Without an explicit target AGP
                // builds every target in the CMake project, dragging libnetease's
                // CLI and test executables into the APK build (13 extra sources
                // per ABI). `netease` comes along as a link dependency.
                targets += "nume_jni"
            }
        }
    }

    signingConfigs {
        // 固定 debug keystore（仓库里的 app/debug.keystore）：AGP 默认会**每台机器
        // 各自生成**一份，于是本机装的 APK 与 CI 产出的 APK 签名不同 —— 手机上装
        // CI 版会直接报 INSTALL_FAILED_UPDATE_INCOMPATIBLE（必须先卸载，丢数据）。
        // debug key 无安全价值（store/key 密码固定 android、别名 androiddebugkey），
        // 把它签进仓库是官方推荐做法，release 签名配置仍不在仓库里。
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // release 复用同一把 keystore（**故意为之**，自用项目）：
        // 1. 手机上现在的 nume 就是这把签的，release 版换新 key 会
        //    INSTALL_FAILED_UPDATE_INCOMPATIBLE —— 必须卸载才装得上，歌单缓存、
        //    登录态全丢。同 key 才能就地覆盖升级。
        // 2. APK 不进 Play，没有「debug key 不被市场接受」的问题。
        // 3. keystore 本来就在仓库里（见上面注释），CI 无需额外 secret 也能签出
        //    可安装的 release 包 —— 否则 CI 只能产出 unsigned，装了没意义。
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            // Local debug only ever runs on the arm64 device; skipping the other
            // two ABIs removes most of the native compile time.
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            // 没这行，assembleRelease 产出的是 `app-release-unsigned.apk` —— 根本
            // 装不上（adb install 直接 INSTALL_PARSE_FAILED_NO_CERTIFICATES）。
            signingConfig = signingConfigs.getByName("release")
            // Release stays ABI-complete.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("17")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            // Builds libnetease (submodule) without curl + our JNI glue as one
            // shared lib. See src/main/cpp/CMakeLists.txt.
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging.resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.kotlinx.coroutines.android)

    // core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose UI / Material 3
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // lifecycle + navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // type-safe navigation serializer
    implementation(libs.kotlinx.serialization.json)

    // media playback (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // networking — the Kotlin/OkHttp layer that replaces libnetease's curl
    implementation(libs.okhttp)

    // image loading
    implementation(libs.coil.compose)

    // shimmer placeholders (skeleton loading)
    implementation(libs.compose.shimmer)

    // persistence — Room offline cache for collection metadata + tracks
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // frame-jank telemetry (JankStats; logs in debug, hook for prod reporting)
    implementation(libs.androidx.metrics.performance)

    // cold-start splash (back-ports the Android 12 splash to API 26+)
    implementation(libs.androidx.core.splashscreen)

    // HTTP logging (activated only in debug via BuildConfig.DEBUG; must be on all
    // variants because NumeTransport in :main references it)
    implementation(libs.okhttp.logging.interceptor)

    // debug-only diagnostics (auto-installed via its own ContentProvider)
    debugImplementation(libs.leakcanary)

    // dependency injection (Hilt)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    // Hilt 2.59.2 bundles a kotlin-metadata-jvm that tops out at metadata 2.3.0;
    // Kotlin 2.4.x emits 2.4.0 metadata and would abort the processor. Bump the
    // metadata reader explicitly until a Hilt release tracks Kotlin 2.4.
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")

    // Baseline Profile generation (macrobenchmark driven; see :baselineprofile).
    baselineProfile(project(":baselineprofile"))

    // ---- 单元测试（JVM，不需要设备）----
    // 只测**纯解析**：JSON → 领域对象。这批判据过去全靠探针实测 + 真机看，一条都没被
    // 固化；改一个键名或漏读一个字段，表现是「徽标时有时无」这类静默错误，不崩、难查。
    // 见 app/src/test/.../core/repo/ 下的 Test。
    testImplementation(libs.junit)
    // 必须**显式**挂真实现：android.jar 里的 org.json 是空壳（方法体 throw），
    // 不挂这份任何 JSONObject 调用都会抛 "not mocked"。
    testImplementation(libs.json)
    testImplementation(libs.truth)
}

// Emit Compose compiler stability/skippability reports + metrics into build/ for
// perf analysis (which composables are restartable/skippable, which classes are unstable).
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}