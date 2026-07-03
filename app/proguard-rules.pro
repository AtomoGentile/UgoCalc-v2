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
-dontwarn org.osgi.framework.**
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**

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
