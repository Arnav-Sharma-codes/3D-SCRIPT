import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.desktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.neg)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.pdfbox)
    implementation(libs.logback)
}

// The desktop application launches the engine as a separate local process.
// Include its executable fat JAR in every packaged app instead of requiring a
// checkout (and Gradle) on a reviewer's machine.
tasks.named<ProcessResources>("processResources") {
    dependsOn(":engine:jar")
    from(project(":engine").layout.buildDirectory.file("libs/engine.jar")) {
        rename { "engine.jar" }
    }
}

compose.desktop {
    application {
        mainClass = "com.threescript.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Pkg)
            packageName = "3D Script Studio"
            // macOS installers require a positive numeric bundle version. The
            // GitHub release/tag carries the pre-release identifier instead.
            packageVersion = "1.0.0"
            description = "Narrative Intelligence Platform"
            copyright = "© 2026 3DSCRIPT"
            vendor = "3DSCRIPT"

            macOS {
                bundleID = "com.threescript.studio"
                dockName = "3D Script"
                appCategory = "public.app-category.productivity"
                minimumSystemVersion = "12.0"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSHighResolutionCapable</key><true/>
                        <key>NSRequiresAquaSystemAppearance</key><false/>
                    """
                }
            }
        }
    }
}
