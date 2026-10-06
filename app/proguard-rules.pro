# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class org.wishyclip.app.data.ProjectEntity { *; }
-keep class org.wishyclip.app.data.FrameEntity { *; }
-keep class org.wishyclip.app.data.LayerEntity { *; }
-keep class org.wishyclip.app.data.AudioTrackEntity { *; }

# Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.exoplayer.**

# Kotlin Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlinx.coroutines.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
