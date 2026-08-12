import org.gradle.api.Project
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun resolveApiBaseUrl(project: Project): String {
    val configured = findConfiguredProperty(
        project,
        "ALICIA_API_BASE_URL",
        "alicia.apiBaseUrl",
    )

    return (configured ?: "https://windwindwind-alicia.cn").removeSuffix("/")
}

fun resolveRagBaseUrl(project: Project, apiBaseUrl: String): String {
    val configured = findConfiguredProperty(
        project,
        "ALICIA_RAG_BASE_URL",
        "alicia.ragBaseUrl",
    )

    return (configured ?: inferDefaultRagBaseUrl(apiBaseUrl)).removeSuffix("/")
}

fun resolveBooleanProperty(project: Project, defaultValue: Boolean, vararg names: String): Boolean {
    val configured = findConfiguredProperty(project, *names)?.trim()?.lowercase() ?: return defaultValue

    return when (configured) {
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        else -> defaultValue
    }
}

fun findConfiguredProperty(project: Project, vararg names: String): String? {
    names.asSequence()
        .mapNotNull { name -> project.findProperty(name)?.toString()?.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.let { return it }

    val localPropertiesFile = project.rootProject.file("local.properties")
    if (!localPropertiesFile.isFile) {
        return null
    }

    val localProperties = Properties().apply {
        localPropertiesFile.reader(Charsets.UTF_8).use { reader -> load(reader) }
    }

    return names.asSequence()
        .mapNotNull { name -> localProperties.getProperty(name)?.trim() }
        .firstOrNull { it.isNotEmpty() }
}

fun inferDefaultRagBaseUrl(apiBaseUrl: String): String {
    val normalized = apiBaseUrl.trim().removeSuffix("/")

    return when {
        normalized == "http://10.0.2.2:8090" -> "http://10.0.2.2:8091"
        normalized == "http://127.0.0.1:8090" -> "http://10.0.2.2:8091"
        normalized == "http://localhost:8090" -> "http://10.0.2.2:8091"
        normalized.endsWith(":8090") -> normalized.removeSuffix(":8090") + ":8091"
        else -> normalized
    }
}

fun buildConfigStringLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.alicia.cloudstorage.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alicia.cloudstorage.phone.add"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.1.7"

        val apiBaseUrl = resolveApiBaseUrl(project)
        val ragActionExecutionEnabled = resolveBooleanProperty(
            project,
            false,
            "ALICIA_RAG_ACTION_EXECUTION_ENABLED",
            "alicia.ragActionExecutionEnabled",
        )
        val ragConfirmationMessage = findConfiguredProperty(
            project,
            "ALICIA_RAG_CONFIRMATION_MESSAGE",
            "alicia.ragConfirmationMessage",
        ) ?: "确认"
        buildConfigField("String", "DEFAULT_API_BASE_URL", buildConfigStringLiteral(apiBaseUrl))
        buildConfigField("String", "DEFAULT_RAG_BASE_URL", buildConfigStringLiteral(resolveRagBaseUrl(project, apiBaseUrl)))
        buildConfigField("String", "RAG_CONFIRMATION_MESSAGE", buildConfigStringLiteral(ragConfirmationMessage))
        buildConfigField("boolean", "RAG_ACTION_EXECUTION_ENABLED", ragActionExecutionEnabled.toString())
        buildConfigField("boolean", "APP_UPDATE_ENABLED", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
