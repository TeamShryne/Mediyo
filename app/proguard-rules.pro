-keep class com.teamshryne.mediyo.** { *; }
# gomobile (mediyo-core AAR) reaches its natives through go.Seq refs —
# keep the generated proxies and the runtime.
-keep class mediyo.** { *; }
-keep class go.** { *; }
-dontwarn go.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**
# Rhino (bundled with NewPipe Extractor) references desktop-only java.beans /
# javax.script APIs that don't exist on Android and are never loaded at runtime.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
