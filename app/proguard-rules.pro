# Kotlin & kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.timeedit.camera.domain.**$$serializer { *; }
-keepclassmembers class com.timeedit.camera.domain.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.timeedit.camera.core.time.**$$serializer { *; }
-keepclassmembers class com.timeedit.camera.core.time.** {
    *** Companion;
}

# Preserve enum values used in DataStore / JSON persistence
-keepclassmembers enum com.timeedit.camera.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# osmdroid map tiles & reflection safety
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-dontwarn org.metalev.multitouch.**

# CameraX / AndroidX
-keep class androidx.camera.** { *; }
