# R8 full mode. Our own code is shrunk, optimized and obfuscated; only the OEM edges are kept.

# Firmware Binder edges. OneOS and CarPlay send these Parcelables inside Bundles and Parcels by
# class name (for example MediaData in the MediaCenter bundle), and the hand-written stubs use
# their names as interface descriptors. Keep the classes, names and members exactly as decompiled.
-keep class com.geely.lib.oneosapi.** { *; }
-keep class com.ecarx.xui.adaptapi.** { *; }
-keep class com.autolink.carplay.** { *; }

# Supplied by the head unit at runtime (`:vendor-ecarx-stub` is compileOnly); not in the APK.
-dontwarn ecarx.**

# Readable stack traces in AppLog and Timber reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
