# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep model classes serialized by Gson and Retrofit
-keep class com.ufo.fraudobserver.data.remote.** { *; }
-keep class com.ufo.fraudobserver.feature.unstuck.data.model.** { *; }
-keepclassmembers class com.ufo.fraudobserver.data.remote.** { *; }
-keepclassmembers class com.ufo.fraudobserver.feature.unstuck.data.model.** { *; }

# Tink and KeyStore
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Retrofit & OkHttp
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Credentials & Google ID
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }


