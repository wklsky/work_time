# 保留 Room 实体与 DAO 的注解信息，避免反射型DAO混淆
-keep class com.worktime.pro.data.db.** { *; }

# Kotlin 协程
-dontwarn kotlinx.coroutines.**

# 保留 Jetpack Compose 相关
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 保留 Kotlin 序列化和反射
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.worktime.pro.**$$serializer { *; }
-keepclassmembers class com.worktime.pro.** {
    *** Companion;
}
-keepclasseswithmembers class com.worktime.pro.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 优化配置
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 保留源文件名和行号，方便崩溃日志定位（可选，发布时可移除）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
