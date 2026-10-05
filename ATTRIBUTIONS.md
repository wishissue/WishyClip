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

All files in `app/src/main/res/drawable/ic_*.xml` are **original placeholder vector drawables**
(colored circle + simple glyph) created for this project and released under Apache 2.0.
They are meant to be replaced with a real icon pack. Suggested open packs and their licenses:

| Pack | License | Obligation |
|---|---|---|
| Fluent Emoji (Microsoft) | MIT | Keep the copyright + license notice |
| Noto Emoji / Noto Color Emoji (Google) | Apache 2.0 | Keep license notice |
| OpenMoji | CC BY-SA 4.0 | Attribution **and share-alike** for the icon assets; check compatibility before bundling in the APK |
| Twemoji | CC BY 4.0 | Attribution required (graphics) |
| Kenney assets | CC0 | None (credit appreciated) |

When you swap in assets, add one row per pack here (name, URL, license, which files).
