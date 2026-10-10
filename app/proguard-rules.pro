# libVLC yerel koddan (JNI) Java sınıflarına ad ile erişir; küçültülmemeli.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**
