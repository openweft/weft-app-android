plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.openweft.weftapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.openweft.weftapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // SSH local-forward transport (SshForwardBackend).
    implementation("com.hierynomus:sshj:0.38.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // org.json is part of the Android SDK at runtime but stubbed in unit
    // tests; pull the real impl so ConfigTest can parse JSON on the JVM.
    testImplementation("org.json:json:20240303")
    // In-process SSH server for the SshForwardBackend end-to-end test.
    testImplementation("org.apache.sshd:sshd-core:2.12.1")
    testImplementation("org.slf4j:slf4j-nop:2.0.13")
}
