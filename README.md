# Cirro

An Android music player. The mobile counterpart to the desktop player
[Netune](https://github.com/ThripleQ/Netune), sharing the same NetEase Cloud data gateway,
[libnetease](https://github.com/ThripleQ/libnetease).

## Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 — **动态取色是关的**（见下）
- **Playback**: AndroidX Media3 (ExoPlayer + Session)
- **Networking**: OkHttp — the Kotlin layer that replaces libnetease's built-in curl transport
- **Native bridge**: libnetease integrated via JNI + transport injection

## Project layout

```
app/
  src/main/java/com/thripleq/cirro/
    MainActivity.kt        # single activity, Compose entry (@AndroidEntryPoint)
    CirroApp.kt             # root UI + type-safe navigation graph
    di/                    # Hilt wiring (AppModule provides the gateway)
    ui/                    # Compose 屏 + 各功能 ViewModel/UiState
      playerbar/           # 常驻 dock + 全屏播放页合体（PlayerDock）、队列/设置/睡眠定时浮层
      components/          # 跨功能通用组件（ExpandableShell 伸展壳；CoverUrls 封面地址的唯一出口）
      menu/                # 曲目 ⋮ 菜单 —— 全站共用一份
      splash/              # 开屏（Compose 画的那层「准备缓冲」）
    core/
      model/               # 领域模型的唯一去处（零 Android/Compose 依赖）
      net/                 # libnetease gateway + OkHttp 传输注入
      repo/                # 各端点取数 + 编排（AuthRepository 管登录态进出）
      playback/            # Media3（ExoPlayer / 队列持久化 / URL 惰性解析）
      db/                  # Room 收藏缓存
      startup/             # StartupGate：开屏的放行判据与「一次进程只演一次」的名额
      util/                # 冷却门等零依赖小工具
  src/main/cpp/            # JNI bridge to libnetease (CMake + NDK)
```

## Documentation

- **[docs/architecture.md](docs/architecture.md)** — 架构地图（分层/数据流/决策/路线图），唯一事实源
- **[docs/navigation-map.md](docs/navigation-map.md)** — 找东西的心智地图（三桶归位 / 命名 / 怎么搜）
- **[docs/change-map.md](docs/change-map.md)** — 改动点速查（想改 X 去改哪个文件）
- **[docs/composing-code.md](docs/composing-code.md)** — 编码纪律（依赖规矩 / 职责守恒 / type-safe 导航）

## Architecture direction

Full design: **[docs/architecture.md](docs/architecture.md)**.

libnetease acts as the data gateway exactly as it does in Netune: it produces the request
parameters (weapi/eapi encryption, device-id pool, …) and parses responses, while the actual HTTP
I/O runs in Kotlin via OkHttp. The bridge uses **transport injection**: libnetease is built without
curl (`NE_USE_CURL=OFF`) and, when it needs to send a request, calls an injected transport that
jumps over JNI to a Kotlin OkHttp layer. All real I/O lives in Kotlin; the C side keeps a single
narrow transport callback.

Playback and caching lean on Media3 end-to-end: `ExoPlayer` + `MediaSessionService` for playback,
`SimpleCache`+`CacheDataSource` for format-agnostic byte caching (we chose to depend on Media3
rather than hand-roll Netune's segment cache — see the architecture doc for the trade-off).

**Dynamic color is deliberately off** (`CirroTheme(dynamicColor = false)`). Material You would
derive the whole scheme from the wallpaper and overwrite the brand red, and on some wallpapers the
derived `onSurface` is dark enough that unstyled text disappears in dark theme. The shipped palette
is contrast-checked pair by pair instead. Flip that flag to follow the wallpaper.

**Login state lives in the C layer**, not in Kotlin: libnetease owns the cookie jar (persisted to an
app-private file). Login imports a cookie string from the in-app WebView; logout **deletes that file
and calls `ne_jar_reload()`** — the jar is created once per process, so deleting the file alone
would leave the in-memory `MUSIC_U` intact and the next request would still be authenticated.

## Building

```bash
./gradlew assembleDebug     # dev build (no R8/AOT — expect jank; see note)
./gradlew assembleRelease    # optimized (R8 + resource shrinking)
```

> **Benchmark against release, never debug.** Debug Compose (no R8/AOT, `debuggable`) runs the UI
> thread ~1.5–1.6× slower — enough to drop frames on a trivial list at 90 Hz. Measured on device:
> release ≈ 12–14 ms 90th / ~1% janky vs debug ≈ 18–22 ms / 3–9%.
>
> **Both variants are installable as-is.** Release is signed with the keystore committed at
> `app/debug.keystore` — the *same cert* as debug, deliberately: a locally built APK can overwrite a
> CI-built one (and vice versa) without uninstalling, and CI needs no signing secret. Rationale is in
> the `signingConfigs` comment inside `app/build.gradle.kts`.

CI (GitHub Actions) — two workflows:

- **[build.yml](.github/workflows/build.yml)** — every push to `main`/`beta` (plus manual runs):
  runs the unit tests, then lint, then uploads `cirro-debug`, `cirro-release` and the test/lint
  reports as artifacts. Artifacts expire after 7 days and need a GitHub login to download.
- **[release.yml](.github/workflows/release.yml)** — a `v*` tag (or manual run): builds only the
  release APK and attaches it as `cirro-<tag>.apk` to a **GitHub Release**, which is login-free and
  permanent.

Both workflows need `fetch-depth: 0` on checkout: `versionCode` is `git rev-list --count HEAD`
(see `app/build.gradle.kts`), and a shallow clone makes every build number 1 — which only shows up
as *"can't install, version downgrade"* on device, never in CI.

### Tests

```bash
./gradlew :app:testDebugUnitTest     # pure JVM — no device, no emulator
```

Covers the JSON→model parsers, the pure rules (pay/VIP badge, cover URL sizing), the refresh
orchestration and the splash "ready" gate (**101 tests**). Both workflows run them *before*
packaging — a parser regression shows up as silently missing badges, never as a crash, so these
tests are the only guardrail.

```bash
./gradlew :app:lintDebug            # static checks; runs in CI too (blocks on Error only)
```

Lint is the only thing that catches the "compiles fine, crashes on old devices" class of bug —
e.g. `startForeground`'s three-arg overload is API 29 while `minSdk` is 26. Warnings are not
gated (dependency-update notices are not ours to fix); see `app/lint.xml` for the one check we
deliberately downgraded.

### Performance & diagnostics

- **Compose compiler reports** → `app/build/compose-reports/` + `app/build/compose-metrics/`
  (class stability / composable skippability).
- **JankStats** — starts/stops with the activity lifecycle; logs janky frames in debug and is the
  hook for production frame-jank reporting.
- **LeakCanary / OkHttp logging / StrictMode** — debug only.
- **Baseline Profile** — `./gradlew :app:generateReleaseBaselineProfile` (macrobenchmark-driven,
  see the `:baselineprofile` module). Some ROMs (e.g. vivo) block UTP installs behind a
  confirmation dialog, so generation needs a friendlier device/emulator.
- **Gradle configuration/build cache** enabled in `gradle.properties`.

## Roadmap

Status and next steps live in **[docs/architecture.md](docs/architecture.md) → 路线图**.