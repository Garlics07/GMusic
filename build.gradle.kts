plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

// Supporta la custom build directory ad alta velocità se presente sull'host, con fallback trasparente per CI/CD
if (file("C:/AndroidBuilds").exists()) {
    allprojects {
        layout.buildDirectory.set(file("C:/AndroidBuilds/GMusic/${project.name}"))
    }
}

