# ============================================
# Firebase Realtime Database
# ============================================
-keepattributes Signature
-keepattributes *Annotation*

# Keep ALL model/data classes used with Firebase
-keep class ru.bl3xand.pancake.data.model.** { *; }
-keep class ru.bl3xand.pancake.models.** { *; }

# Keep no-arg constructors for Firebase deserialization
-keepclassmembers class ru.bl3xand.pancake.data.model.** {
    <init>();
    <init>(...);
}
-keepclassmembers class ru.bl3xand.pancake.models.** {
    <init>();
    <init>(...);
}

# ============================================
# Firebase / Google
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ============================================
# Google Credential Manager / Sign-In
# ============================================
-keep class androidx.credentials.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ============================================
# Gson
# ============================================
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================
# Retrofit
# ============================================
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ============================================
# Glide
# ============================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ============================================
# ZXing
# ============================================
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }