import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright (C) 2026 PoldoKeyboard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
        ))
    }
}

configure<LibraryExtension> {
    namespace = "io.github.donatogranvilla.poldokeyboard.localai"
    compileSdk = providers.gradleProperty("projectCompileSdk").get().toInt()
    // Versione locale facoltativa (gradle.properties dell'utente), altrimenti quella di FlorisBoard
    ndkVersion = providers.gradleProperty("poldo.ndkVersion").getOrElse(tools.versions.ndk.get())

    defaultConfig {
        minSdk = providers.gradleProperty("projectMinSdk").get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                // La libreria JNI con le sue dipendenze, e le varianti del backend CPU che whisper_jni carica
                // a runtime: nessuna libreria le richiede, quindi vanno elencate perché finiscano nell'APK
                targets(
                    "whisper_jni",
                    "whisper",
                    "ggml",
                    "ggml-base",
                    "ggml-cpu-android_armv8.0_1",
                    "ggml-cpu-android_armv8.2_1",
                    "ggml-cpu-android_armv8.2_2",
                    "ggml-cpu-android_armv8.6_1",
                    "ggml-cpu-android_armv9.0_1",
                    "ggml-cpu-android_armv9.2_1",
                    "ggml-cpu-android_armv9.2_2",
                )
            }
        }

        ndk {
            // Solo i telefoni a 64 bit, cioè quasi tutti quelli in uso: altrove il motore risulta non disponibile.
            // Dentro arm64-v8a le varianti del backend CPU coprono tutti i processori, da ARMv8.0 ad ARMv9.2
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            version = providers.gradleProperty("poldo.cmakeVersion").getOrElse(tools.versions.cmake.get())
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("beta") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Estrazione degli archivi .tar.bz2 dei modelli sherpa-onnx
    implementation(libs.apache.commons.compress)
    // api: le classi del catalogo sono @Serializable e l'app le usa
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = 1
    jvmArgs("-Xmx512m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1")
}
