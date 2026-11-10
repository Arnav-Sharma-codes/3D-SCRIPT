plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines)
    // SQLite JDBC for dataset storage
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    // ONNX Runtime will be added in Phase 3:
    // implementation("com.microsoft.onnxruntime:onnxruntime:1.17.0")
}
