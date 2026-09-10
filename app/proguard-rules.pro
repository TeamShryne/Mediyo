-keep class com.teamshryne.mediyo.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**
# Rhino (bundled with NewPipe Extractor) references desktop-only java.beans /
# javax.script APIs that don't exist on Android and are never loaded at runtime.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
