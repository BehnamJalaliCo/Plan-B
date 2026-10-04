# Plan-B R8 rules. Most libraries ship their own consumer rules (Room, Hilt, Compose,
# kotlinx.serialization, Navigation); only app-specific needs are listed here.

# kotlinx.serialization: keep generated serializers for @Serializable classes (navigation
# routes, backup DTOs, note documents, template payloads).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class com.behnamjalali.planb.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.behnamjalali.planb.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.behnamjalali.planb.**$$serializer { *; }

# Enum names are persisted (Room converters, DataStore, backups) via name()/valueOf().
-keepclassmembers enum com.behnamjalali.planb.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Readable crash stack traces from the uploaded mapping file.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
