# UniFFI and JNA perform dynamic native binding; keep their generated/runtime APIs intact.
-keep class uniffi.chimera_ffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * {
    native <methods>;
}

-dontwarn java.awt.**
