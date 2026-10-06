# Attributions

Wishy Clip is licensed under the Apache License 2.0 (see `LICENSE`).

## Libraries (all linked via Gradle, none vendored)

| Library | License |
|---|---|
| AndroidX (Core, Lifecycle, Activity, Navigation, Compose, Room, DataStore, Graphics Core, Input MotionPrediction, Metrics Performance) | Apache 2.0 |
| Media3 ExoPlayer (audio playback & sync) | Apache 2.0 |
| LZ4 Java (`net.jpountz.lz4:lz4`) | Apache 2.0 |
| Coil | Apache 2.0 |
| kotlinx.coroutines | Apache 2.0 |
| Gradle wrapper (`gradlew`, `gradle-wrapper.jar`, from gradle/gradle v8.9.0) | Apache 2.0 |

Not used: FFmpegKit (retired), Krita or any other GPL code. `androidx.graphics:graphics-core` and
skydoves ColorPicker are allowed by the project rules but are not used yet (see PROGRESS.md).

## Icons

All UI icons use permissive open-source vector icon systems:

| Pack | License | Repository / License URL | Usage |
|---|---|---|---|
| **Tabler Icons** | MIT | https://tabler.io/license | Primary drawing toolbar, navigation, and frame action icons |
| **Lucide Icons** | ISC / MIT | https://lucide.dev/license | Secondary tools (ruler, mirror, audio track, transport controls) |
| **Material Symbols** | Apache 2.0 | https://fonts.google.com/icons | Fallback system overflow menu icons |
