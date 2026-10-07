import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

configure<ApplicationExtension> {
    namespace = "io.github.donatogranvilla.poldokeyboard"
    compileSdk = providers.gradleProperty("projectCompileSdk").get().toInt()
    // La stessa NDK di :lib:localai: senza, il passo che toglie i simboli di debug dalle librerie native usa la
    // versione predefinita del plugin Android e, se la macchina non ce l'ha, le impacchetta intere (+10 MB)
    ndkVersion = providers.gradleProperty("poldo.ndkVersion").getOrElse(tools.versions.ndk.get())
    buildToolsVersion = tools.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.donatogranvilla.poldokeyboard"
        minSdk = providers.gradleProperty("projectMinSdk").get().toInt()
        targetSdk = providers.gradleProperty("projectTargetSdk").get().toInt()
        versionCode = providers.gradleProperty("projectVersionCode").get().toInt()
        versionName = providers.gradleProperty("projectVersionName").get()
    }

    signingConfigs {
        getByName("debug") {
            // Chiave di debug del progetto, la stessa in locale e in CI: l'APK si aggiorna senza disinstallarlo.
            // Le credenziali sono quelle standard di debug di Android, non segrete.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
        }
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines)
    implementation(projects.lib.localai)

    testImplementation(libs.kotlin.test.junit5)
}
