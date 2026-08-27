// Top-level build file.
//
// Note what is NOT here: the org.jetbrains.kotlin.android plugin. AGP 9 has
// built-in Kotlin support, and applying the old plugin fails with
// "Cannot add extension with name 'kotlin'". See docs/BUILD_SETUP.md.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
