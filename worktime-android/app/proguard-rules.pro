# 保留 Room 实体与 DAO 的注解信息，避免反射型DAO混淆
-keep class com.worktime.pro.data.db.** { *; }

# ML Kit 模型相关类保留
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Kotlin 协程
-dontwarn kotlinx.coroutines.**
