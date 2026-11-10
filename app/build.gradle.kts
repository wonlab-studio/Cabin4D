plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.cabin4d"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cabin4d"
        // 차량 사이버보안 요건(서명 키 로테이션) 때문에 Pleos 앱은 28 이상이어야 함
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            // Vehicle SDK를 리플렉션으로 부르므로 난독화는 끈다(타입 호출로 바꾼 뒤 켜도 됨)
            isMinifyEnabled = false
            // 공모전 제출용 release APK를 바로 설치할 수 있게 디버그 키로 서명.
            // 앱 마켓에 낼 때는 반드시 정식 키스토어로 바꿀 것.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // CarUxRestrictionsManager(주행 규제 상태) 사용
    useLibrary("android.car")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.vehicle.sdk)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
