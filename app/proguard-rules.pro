# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.shreenipane.orbit.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shreenipane.orbit.**$$serializer { *; }

# jsoup ships optional re2j support
-dontwarn com.google.re2j.**

# Annotation-only dependencies that aren't on the runtime classpath
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# kotlinx.serialization: keep generated serializers for every @Serializable class in the app,
# including private nested ones (PastPapers' API responses).
-keepclassmembers class com.shreenipane.orbit.** {
    *** Companion;
}
-keepclasseswithmembers class com.shreenipane.orbit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
