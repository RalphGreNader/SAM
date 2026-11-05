# kotlinx.serialization: keep generated serializers for API models
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class com.samvedamanikam.sam.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.samvedamanikam.sam.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces (generic signatures are needed for suspend functions)
-keep,allowobfuscation,allowshrinking interface com.samvedamanikam.sam.data.api.SamApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
