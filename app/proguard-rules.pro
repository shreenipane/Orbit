# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.shreenipane.orbit.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shreenipane.orbit.**$$serializer { *; }

# jsoup ships optional re2j support
-dontwarn com.google.re2j.**
