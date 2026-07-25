# Apache POI / XMLBeans reflection
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# Transitive deps of Apache POI not present on Android and never actually invoked:
# log4j's optional OSGi/bnd service-loader support, and AWT used only by an unused
# graphbuilder code path.
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn org.osgi.framework.**
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**

# Compression libraries used by Apache POI (optional/not needed for all formats)
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.XZInputStream

# PdfBox-android
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.ugocalc.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ugocalc.app.**$$serializer {
    *** INSTANCE;
}

# General annotations and Kotlin specific warnings
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn kotlinx.parcelize.Parcelize
