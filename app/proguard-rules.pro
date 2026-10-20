# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# Hilt / Dagger
-keep,allowobfuscation @interface dagger.hilt.android.*
-dontwarn dagger.hilt.**

# Media3
-dontwarn androidx.media3.**

# Keep model classes used with reflection by Room/Coil
-keep class com.progallery.app.data.model.** { *; }
