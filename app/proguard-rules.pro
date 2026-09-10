-keep class com.teamshryne.mediyo.** { *; }
# JNA (used by the UniFFI bindings for every Rust FFI call) touches its own
# internals via JNI — e.g. the native `peer` field on Pointer — so R8 must
# leave the whole library alone or every search/home call crashes in release
# with "can't find peer field ID for class com.sun.jna.Pointer".
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
# The generated bindings use Structure subclasses + Callbacks whose fields and
# method signatures JNA resolves reflectively; they live outside our app
# package, so keep them explicitly.
-keep class uniffi.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Union { *; }
-keepclassmembers class * extends com.sun.jna.Callback { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**
# Rhino (bundled with NewPipe Extractor) references desktop-only java.beans /
# javax.script APIs that don't exist on Android and are never loaded at runtime.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
