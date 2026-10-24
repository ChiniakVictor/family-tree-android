import java.util.Properties
import java.time.ZonedDateTime

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Family data is bundled at build time. Defaults to the fictional sample-data/ folder; point it at your own data with -PfamilyData=/path.
val familyDataDir: File = file(
    (project.findProperty("familyData") as String?) ?: "${rootDir}/sample-data"
)
val generatedAssets = layout.buildDirectory.dir("generated/familyAssets")

val syncFamilyData by tasks.registering(Sync::class) {
    description = "Copies the latest family JSON into the app assets."
    from(familyDataDir) { include("people.json", "families.json", "events.json", "sources.json", "places.json") }
    into(generatedAssets.map { it.dir("data") })
    inputs.dir(familyDataDir)
    doFirst {
        val required = listOf("people.json", "families.json", "events.json", "sources.json", "places.json")
        val missing = required.filterNot { File(familyDataDir, it).exists() }
        if ("people.json" in missing) throw GradleException("people.json not found in $familyDataDir")
        if (missing.isNotEmpty()) logger.warn("Family data: missing optional files $missing")
    }
    doLast {
        val stamp = ZonedDateTime.now().toString()
        File(destinationDir, "bundle.json").writeText("{\"bundled_at\":\"$stamp\",\"source_dir\":\"${familyDataDir.absolutePath}\"}")
    }
}

android {
    namespace = "com.victor.familytree"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.victor.familytree"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2"
    }

    sourceSets["main"].assets.srcDir(generatedAssets)

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

tasks.named("preBuild") { dependsOn(syncFamilyData) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
