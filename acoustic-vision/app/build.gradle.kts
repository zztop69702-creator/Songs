import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---- 버전·서명 (V3: 업데이트 시 데이터 보존) ----
// CI(GitHub Actions)는 CI_BUILD_NUMBER(=run_number)를 넘긴다. versionCode는 항상 증가해야 기존 앱 위에 업데이트된다.
// 드라이브 배포(정식): RELEASE_SEQ(배포 순번) → versionCode 2000+순번, versionName x.y.z-r순번 (tools/build-release.sh)
val ciBuildNumber: Int? = System.getenv("CI_BUILD_NUMBER")?.toIntOrNull()
val releaseSeq: Int? = System.getenv("RELEASE_SEQ")?.toIntOrNull()
val baseVersionName = "0.4.0"
val appVersionCode = when {
    releaseSeq != null -> 2000 + releaseSeq
    ciBuildNumber != null -> 1000 + ciBuildNumber
    else -> 4
}
val appVersionName = when {
    releaseSeq != null -> "$baseVersionName-r$releaseSeq"
    ciBuildNumber != null -> "$baseVersionName-b$ciBuildNumber"
    else -> "$baseVersionName-local"
}
// release 서명키: 저장소에 넣지 않는다. CI secret → 환경변수로만 전달 (값은 로그에 출력하지 않음)
val releaseKeystore: String? = System.getenv("ANDROID_KEYSTORE_FILE")
val releaseSigningAvailable = !releaseKeystore.isNullOrBlank() && file(releaseKeystore).exists() &&
    listOf("ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.youngil.acousticvision"
    compileSdk = 36

    defaultConfig {
        applicationId = "kr.youngil.acousticvision"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        resValue("string", "app_name", "영일 음향비전")
        buildConfigField("int", "CI_BUILD_NUMBER", (ciBuildNumber ?: 0).toString())
        // 실제 휴대폰(ARM)만 포함: ML Kit 네이티브 라이브러리 용량 절감 (x86 에뮬레이터 제외)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 현장용: 고정 서명키(release). 키가 없으면 서명되지 않은 APK가 생성되며 설치용으로 배포하지 않는다.
            isMinifyEnabled = false
            if (releaseSigningAvailable) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 설치 시험용: 빌드 환경마다 디버그 서명이 달라 업데이트가 안 될 수 있으므로 별도 앱으로 설치
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "영일 음향비전 (시험)")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // 스마트 비교보정 V2: 소음계 결과 화면 OCR (번들 모델, 오프라인 동작)
    implementation(libs.mlkit.text.recognition)

    // 시험 전용 (APK 미포함): JVM에서 화면 흐름 스모크 시험
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
