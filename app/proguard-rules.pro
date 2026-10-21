# Brome ProGuard / R8 Configuration Rules
# Stage A: Shrink only (temporary -dontobfuscate)

# Temporarily disable obfuscation during Stage A to isolate shrinking
-dontobfuscate

# Preserve line numbers and source file names for readable stack traces in Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep enum constant names and reflection methods so enumValueOf<T>() and DataStore string matching work
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public **[] $VALUES;
    public *;
}

# WorkManager Workers (instantiated via reflection with Context and WorkerParameters)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.ripple.chat.data.AutoDeleteWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# AndroidX Lifecycle ViewModels (instantiated via ViewModelProvider reflection factories)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# Coroutines internal dispatchers and handlers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
