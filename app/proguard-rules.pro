# Add project specific ProGuard rules here.
-dontwarn org.osmdroid.**
-dontwarn org.mapsforge.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-keep class org.osmdroid.** { *; }
-keep class org.mapsforge.** { *; }
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.hellboy.tehrannav.** {
    @kotlinx.serialization.SerialName <fields>;
}