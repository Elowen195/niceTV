# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preservation of line numbers for debugging
-keepattributes SourceFile, LineNumberTable

# Room database rules
-keep class com.elowen.niceTV.data.db.entity.** { *; }
-keep class com.elowen.niceTV.data.db.dao.** { *; }
-keep class com.elowen.niceTV.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.elowen.niceTV.data.model.** { *; }
-keep class com.elowen.niceTV.data.backend.** { *; }

# Jsoup optional dependencies (Re2j)
-dontwarn com.google.re2j.**

# sing-box (libbox) rules
-keep class io.nekohasekai.libbox.** { *; }
-dontwarn io.nekohasekai.libbox.**

# Proxy data models
-keep class com.elowen.niceTV.data.models.** { *; }
-keep class com.elowen.niceTV.data.db.NodeEntity { *; }
-keep class com.elowen.niceTV.data.db.SubscriptionEntity { *; }
