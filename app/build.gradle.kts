plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.qingtu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.qingtu"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 启用 R8，移除未使用代码并优化字节码。
            isMinifyEnabled = true
            // 同时移除未使用的 Android 资源。
            isShrinkResources = true
            // 使用 debug 签名，便于从 GitHub Actions 下载后直接安装。
            // CI 每次运行的 debug 密钥可能不同，更新安装时仍可能需要先卸载旧版。
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 保留现有图标依赖，由 R8 在 Release 构建中裁剪未使用的图标代码。
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
