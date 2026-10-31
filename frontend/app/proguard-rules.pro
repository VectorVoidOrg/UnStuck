# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep model classes serialized by Gson and Retrofit
-keepclassmembers class com.ufo.fraudobserver.data.remote.** { *; }
-keepclassmembers class com.ufo.fraudobserver.feature.unstuck.data.model.** { *; }


