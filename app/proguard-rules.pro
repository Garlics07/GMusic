# Add project specific ProGuard rules here.
# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gmusic.app.**$$serializer { *; }
-keepclassmembers class com.gmusic.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.gmusic.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep NewPipeExtractor and Rhino (used internally by NewPipe for JS signature decryption)
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**
