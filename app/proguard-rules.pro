# Brome ProGuard / R8 Configuration Rules

# Keep all project application classes, methods, and members
-keep class com.ripple.chat.** { *; }
-keepclassmembers class com.ripple.chat.** { *; }
-keepclassmembers enum com.ripple.chat.** { *; }

# Keep all Enum classes, constants, values, valueOf, and name
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public java.lang.String name();
    public int ordinal();
}

# WorkManager Workers (instantiated via reflection)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.ripple.chat.data.AutoDeleteWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# AndroidX Lifecycle, ViewModel, and SavedState
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# AppCompat, Locales & Resources
-keep class androidx.appcompat.app.** { *; }
-keep class androidx.core.os.** { *; }

# Jetpack Compose
-keep class androidx.compose.** { *; }

# DataStore Preferences
-keep class androidx.datastore.** { *; }

# Kotlin Coroutines & Serialization
-keep class kotlinx.serialization.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
