# Void — R8 / ProGuard rules
#
# The release build shrinks the app, so everything that is looked up
# reflectively or through a ServiceLoader has to survive.

# --- kotlinx.serialization -------------------------------------------------
# The generated serializers are only reachable through reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.eivoid.** {
    *** Companion;
}
-keepclasseswithmembers class com.eivoid.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class com.eivoid.**$* {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Tink (behind androidx.security:security-crypto) -----------------------
# Tink references Error Prone annotations that are compile-time only.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.services.**
-dontwarn org.joda.time.**
-keep class com.google.crypto.tink.** { *; }

# --- JGit ------------------------------------------------------------------
# JGit discovers file systems and extensions through ServiceLoader, and some
# of its classes are resolved by name.
-keep class * implements org.eclipse.jgit.util.FS
-keep class * implements org.eclipse.jgit.extensions.*
-keepnames class org.eclipse.jgit.**
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.lib.** { *; }
-keep class org.eclipse.jgit.transport.** { *; }

# --- Ktor ------------------------------------------------------------------
# Client and server plugins are found through ServiceLoader.
-keep class io.ktor.client.plugins.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# --- Room ------------------------------------------------------------------
# The generated implementations are referenced by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Kotlin coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**

# --- Our own code ----------------------------------------------------------
# The model ids and tool names are read from resources and JSON, never by
# reflection, but the data classes are kept for readability in crash logs.
-keepnames class com.eivoid.core.ai.** { *; }
