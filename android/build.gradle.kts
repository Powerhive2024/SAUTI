plugins {
    id("com.android.application") version "8.13.2" apply false
    // LiteRT-LM 0.19.x ships Kotlin 2.4 metadata, so the app must compile with Kotlin >= 2.4.
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}
