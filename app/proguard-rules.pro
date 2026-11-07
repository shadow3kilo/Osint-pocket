# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class com.osintpocket.app.**$$serializer { *; }
-keepclassmembers class com.osintpocket.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.osintpocket.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit (interfaces de API)
-keep,allowobfuscation interface com.osintpocket.app.data.remote.** { *; }

# OkHttp plataformas opcionales
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
