import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
