# ──────────────────────────────────────────────────────────────
# Luminar Reader — ProGuard / R8 Rules
# ──────────────────────────────────────────────────────────────

# ─── Keep Room entities and DAOs ─────────────────────────────
-keep class com.luminar.reader.data.model.** { *; }
-keep class com.luminar.reader.data.local.db.** { *; }
-keep class com.luminar.reader.data.epub.** { *; }

# ─── Keep Hilt generated classes ─────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
    @javax.inject.Inject <init>(...);
}

# ─── Keep Moshi / Retrofit models (for Ollama API) ──────────
-keep class com.luminar.reader.network.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ─── Keep Compose previews in debug ──────────────────────────
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview *;
}

# ─── Keep BuildConfig fields ────────────────────────────────
-keep class com.luminar.reader.BuildConfig { *; }

# ─── Standard Android rules ─────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Don't warn about optional deps ─────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
