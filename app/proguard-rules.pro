# Brome ProGuard / R8 Configuration Rules
# Full R8 optimization, shrinking, and obfuscation enabled

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

# WorkManager & Room Database (WorkDatabase_Impl is instantiated by Room via Class.forName)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.** { *; }

# AndroidX Lifecycle ViewModels (instantiated via ViewModelProvider reflection factories)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# AppCompat & Locales (used by AppCompatDelegate.setApplicationLocales in MainActivity)
-keep class androidx.appcompat.app.** { *; }
-keep class androidx.core.os.** { *; }

# DataStore Preferences
-keep class androidx.datastore.** { *; }

# Coroutines internal dispatchers and handlers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
