// ============================================================================
// FILE: build.gradle.kts (Project Level)
// Location: /build.gradle.kts
// ============================================================================
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

// ============================================================================
// FILE: app/build.gradle.kts (App Level)
// Location: /app/build.gradle.kts
// ============================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.companion.Jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.companion.Jarvis"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    
    // WebSocket
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ============================================================================
// FILE: AndroidManifest.xml
// Location: /app/src/main/AndroidManifest.xml
// ============================================================================
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".CompanionApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Companion Jarvis"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CompanionJarvis"
        tools:targetApi="34">
        
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.WebSocketService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
            
    </application>

</manifest>

// ============================================================================
// FILE: CompanionApplication.kt
// Location: /app/src/main/java/com/companion/Jarvis/CompanionApplication.kt
// ============================================================================
package com.companion.Jarvis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CompanionApplication : Application() {
    
    companion object {
        const val CHANNEL_ID = "companion_Jarvis_channel"
        const val CHANNEL_NAME = "Companion Jarvis Service"
        const val NOTIFICATION_ID = 1001
        
        const val AUTH_TOKEN = "your_auth_token_here"
        const val WEBSOCKET_URL = "wss://iron-man-shell--factshift923.replit.app/ws?token=$AUTH_TOKEN"
        
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val BASE_RECONNECT_DELAY_MS = 1000L
        const val MAX_RECONNECT_DELAY_MS = 30000L
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for Companion Jarvis WebSocket service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// ============================================================================
// FILE: PermissionManager.kt
// Location: /app/src/main/java/com/companion/Jarvis/permission/PermissionManager.kt
// ============================================================================
package com.companion.Jarvis.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.companion.Jarvis.R

class PermissionManager(
    private val activity: FragmentActivity,
    private val onPermissionsGranted: (() -> Unit)? = null,
    private val onPermissionsDenied: ((List<String>) -> Unit)? = null
) {
    
    companion object {
        const val REQUEST_CODE_PERMISSIONS = 2001
        
        val ESSENTIAL_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        ).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                remove(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val OPTIONAL_PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
    }
    
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResult(permissions)
        }
    
    private val deniedPermissions = mutableListOf<String>()
    private var pendingPermissions = listOf<String>()
    
    fun requestEssentialPermissions() {
        val permissionsToRequest = ESSENTIAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            onPermissionsGranted?.invoke()
            return
        }
        
        if (shouldShowRationale(permissionsToRequest)) {
            showPermissionRationaleDialog(permissionsToRequest) {
                launchPermissionRequest(permissionsToRequest)
            }
        } else {
            launchPermissionRequest(permissionsToRequest)
        }
    }
    
    fun requestOptionalPermissions() {
        val permissionsToRequest = OPTIONAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            return
        }
        
        launchPermissionRequest(permissionsToRequest)
    }
    
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun checkMultiplePermissions(permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { checkPermission(it) }
    }
    
    fun isMicrophonePermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.RECORD_AUDIO)
    }
    
    fun isCameraPermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.CAMERA)
    }
    
    fun isContactsPermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.READ_CONTACTS)
    }
    
    fun isPhonePermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.CALL_PHONE)
    }
    
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
    
    private fun launchPermissionRequest(permissions: List<String>) {
        pendingPermissions = permissions
        deniedPermissions.clear()
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val permanentlyDenied = mutableListOf<String>()
        val temporarilyDenied = mutableListOf<String>()
        
        for ((permission, granted) in permissions) {
            if (!granted) {
                if (!shouldShowRationale(listOf(permission))) {
                    permanentlyDenied.add(permission)
                } else {
                    temporarilyDenied.add(permission)
                }
            }
        }
        
        if (temporarilyDenied.isNotEmpty()) {
            showPermissionRetryDialog(temporarilyDenied) {
                launchPermissionRequest(temporarilyDenied)
            }
        } else if (permanentlyDenied.isNotEmpty()) {
            showSettingsRedirectDialog(permanentlyDenied)
        } else {
            onPermissionsGranted?.invoke()
        }
    }
    
    private fun shouldShowRationale(permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true
            }
        }
        return false
    }
    
    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.CALL_PHONE -> "Phone"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substringAfterLast(".")
        }
    }
    
    private fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> 
                "The microphone is needed to capture your voice commands and enable voice interaction with the companion Jarvis."
            Manifest.permission.CAMERA -> 
                "Camera access allows the Jarvis to see and process visual information for enhanced assistance."
            Manifest.permission.READ_CONTACTS -> 
                "Contacts access enables the Jarvis to help manage your contacts and perform communication tasks."
            Manifest.permission.CALL_PHONE -> 
                "Phone permission allows the Jarvis to initiate calls on your behalf when requested."
            Manifest.permission.POST_NOTIFICATIONS -> 
                "Notifications are required to keep you informed about Jarvis status and important updates."
            else -> "This permission is required for full functionality."
        }
    }
    
    private fun showPermissionRationaleDialog(
        permissions: List<String>,
        onAccept: () -> Unit
    ) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        val descriptions = permissions.joinToString("\n\n") { 
            "• ${getPermissionDisplayName(it)}: ${getPermissionDescription(it)}"
        }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("The following permissions are needed for the Companion Jarvis to function properly:\n\n$descriptions")
            .setPositiveButton("Grant Permissions") { dialog, _ ->
                dialog.dismiss()
                onAccept()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun showPermissionRetryDialog(
        permissions: List<String>,
        onRetry: () -> Unit
    ) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Needed")
            .setMessage("$permissionNames permission(s) are required. Would you like to try again?")
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun showSettingsRedirectDialog(permissions: List<String>) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Permanently Denied")
            .setMessage(
                "$permissionNames permission(s) have been permanently denied. " +
                "Please enable them in the app settings to use all features."
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    fun onResume() {
        if (pendingPermissions.isNotEmpty()) {
            val stillMissing = pendingPermissions.filter {
                !checkPermission(it)
            }
            if (stillMissing.isEmpty()) {
                pendingPermissions = emptyList()
                onPermissionsGranted?.invoke()
            }
        }
    }
}

// ============================================================================
// FILE: VoiceInteractionManager.kt
// Location: /app/src/main/java/com/companion/Jarvis/voice/VoiceInteractionManager.kt
// ============================================================================
package com.companion.Jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class VoiceInteractionManager(
    private val context: Context,
    private val onVoiceCommand: (String) -> Unit,
    private val onStateChanged: (VoiceState) -> Unit
) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isTtsInitialized = false
    private var isRecognitionAvailable = false
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val ttsQueue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var silenceTimeoutJob: Job? = null
    private var partialResults = StringBuilder()
    
    enum class VoiceState {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR,
        UNAVAILABLE
    }
    
    data class VoiceResult(
        val text: String,
        val isFinal: Boolean,
        val confidence: Float
    )
    
    fun initialize() {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            updateState(VoiceState.UNAVAILABLE)
            onStateChanged(VoiceState.UNAVAILABLE)
            return
        }
        
        isRecognitionAvailable = true
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateState(VoiceState.LISTENING)
                onStateChanged(VoiceState.LISTENING)
            }
            
            override fun onBeginningOfSpeech() {
                partialResults.clear()
                silenceTimeoutJob?.cancel()
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                silenceTimeoutJob?.cancel()
                silenceTimeoutJob = scope.launch {
                    delay(2000)
                    if (isListening) {
                        stopListening()
                    }
                }
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                updateState(VoiceState.PROCESSING)
                onStateChanged(VoiceState.PROCESSING)
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ER
                    // ============================================================================
// FILE: build.gradle.kts (Project Level)
// Location: /build.gradle.kts
// ============================================================================
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

// ============================================================================
// FILE: app/build.gradle.kts (App Level)
// Location: /app/build.gradle.kts
// ============================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.companion.Jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.companion.Jarvis"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    
    // WebSocket
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Fragment KTX for ActivityResultContracts
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ============================================================================
// FILE: AndroidManifest.xml
// Location: /app/src/main/AndroidManifest.xml
// ============================================================================
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".CompanionApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Companion Jarvis"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CompanionJarvis"
        tools:targetApi="34">
        
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.WebSocketService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
            
    </application>

</manifest>

// ============================================================================
// FILE: CompanionApplication.kt
// Location: /app/src/main/java/com/companion/Jarvis/CompanionApplication.kt
// ============================================================================
package com.companion.Jarvis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CompanionApplication : Application() {
    
    companion object {
        const val CHANNEL_ID = "companion_Jarvis_channel"
        const val CHANNEL_NAME = "Companion Jarvis Service"
        const val NOTIFICATION_ID = 1001
        
        const val AUTH_TOKEN = "your_auth_token_here"
        const val WEBSOCKET_URL = "wss://your-project.replit.dev/ws?token=$AUTH_TOKEN"
        
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val BASE_RECONNECT_DELAY_MS = 1000L
        const val MAX_RECONNECT_DELAY_MS = 30000L
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for Companion Jarvis WebSocket service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// ============================================================================
// FILE: PermissionManager.kt
// Location: /app/src/main/java/com/companion/Jarvis/permission/PermissionManager.kt
// ============================================================================
package com.companion.Jarvis.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.companion.Jarvis.R

class PermissionManager(
    private val activity: FragmentActivity,
    private val onPermissionsGranted: (() -> Unit)? = null,
    private val onPermissionsDenied: ((List<String>) -> Unit)? = null
) {
    
    companion object {
        const val REQUEST_CODE_PERMISSIONS = 2001
        
        val ESSENTIAL_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        ).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                remove(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val OPTIONAL_PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
    }
    
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResult(permissions)
        }
    
    private val deniedPermissions = mutableListOf<String>()
    private var pendingPermissions = listOf<String>()
    
    fun requestEssentialPermissions() {
        val permissionsToRequest = ESSENTIAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            onPermissionsGranted?.invoke()
            return
        }
        
        if (shouldShowRationale(permissionsToRequest)) {
            showPermissionRationaleDialog(permissionsToRequest) {
                launchPermissionRequest(permissionsToRequest)
            }
        } else {
            launchPermissionRequest(permissionsToRequest)
        }
    }
    
    fun requestOptionalPermissions() {
        val permissionsToRequest = OPTIONAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            return
        }
        
        launchPermissionRequest(permissionsToRequest)
    }
    
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun checkMultiplePermissions(permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { checkPermission(it) }
    }
    
    fun isMicrophonePermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.RECORD_AUDIO)
    }
    
    fun isCameraPermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.CAMERA)
    }
    
    fun isContactsPermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.READ_CONTACTS)
    }
    
    fun isPhonePermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.CALL_PHONE)
    }
    
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
    
    private fun launchPermissionRequest(permissions: List<String>) {
        pendingPermissions = permissions
        deniedPermissions.clear()
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val permanentlyDenied = mutableListOf<String>()
        val temporarilyDenied = mutableListOf<String>()
        
        for ((permission, granted) in permissions) {
            if (!granted) {
                if (!shouldShowRationale(listOf(permission))) {
                    permanentlyDenied.add(permission)
                } else {
                    temporarilyDenied.add(permission)
                }
            }
        }
        
        if (temporarilyDenied.isNotEmpty()) {
            showPermissionRetryDialog(temporarilyDenied) {
                launchPermissionRequest(temporarilyDenied)
            }
        } else if (permanentlyDenied.isNotEmpty()) {
            showSettingsRedirectDialog(permanentlyDenied)
        } else {
            onPermissionsGranted?.invoke()
        }
    }
    
    private fun shouldShowRationale(permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true
            }
        }
        return false
    }
    
    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.CALL_PHONE -> "Phone"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substringAfterLast(".")
        }
    }
    
    private fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> 
                "The microphone is needed to capture your voice commands and enable voice interaction with the companion Jarvis."
            Manifest.permission.CAMERA -> 
                "Camera access allows the Jarvis to see and process visual information for enhanced assistance."
            Manifest.permission.READ_CONTACTS -> 
                "Contacts access enables the Jarvis to help manage your contacts and perform communication tasks."
            Manifest.permission.CALL_PHONE -> 
                "Phone permission allows the Jarvis to initiate calls on your behalf when requested."
            Manifest.permission.POST_NOTIFICATIONS -> 
                "Notifications are required to keep you informed about Jarvis status and important updates."
            else -> "This permission is required for full functionality."
        }
    }
    
    private fun showPermissionRationaleDialog(
        permissions: List<String>,
        onAccept: () -> Unit
    ) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        val descriptions = permissions.joinToString("\n\n") { 
            "• ${getPermissionDisplayName(it)}: ${getPermissionDescription(it)}"
        }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("The following permissions are needed for the Companion Jarvis to function properly:\n\n$descriptions")
            .setPositiveButton("Grant Permissions") { dialog, _ ->
                dialog.dismiss()
                onAccept()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun showPermissionRetryDialog(
        permissions: List<String>,
        onRetry: () -> Unit
    ) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Needed")
            .setMessage("$permissionNames permission(s) are required. Would you like to try again?")
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun showSettingsRedirectDialog(permissions: List<String>) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Permanently Denied")
            .setMessage(
                "$permissionNames permission(s) have been permanently denied. " +
                "Please enable them in the app settings to use all features."
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    fun onResume() {
        if (pendingPermissions.isNotEmpty()) {
            val stillMissing = pendingPermissions.filter {
                !checkPermission(it)
            }
            if (stillMissing.isEmpty()) {
                pendingPermissions = emptyList()
                onPermissionsGranted?.invoke()
            }
        }
    }
}

// ============================================================================
// FILE: VoiceInteractionManager.kt
// Location: /app/src/main/java/com/companion/Jarvis/voice/VoiceInteractionManager.kt
// ============================================================================
package com.companion.Jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class VoiceInteractionManager(
    private val context: Context,
    private val onVoiceCommand: (String) -> Unit,
    private val onStateChanged: (VoiceState) -> Unit
) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isTtsInitialized = false
    private var isRecognitionAvailable = false
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val ttsQueue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var silenceTimeoutJob: Job? = null
    private var partialResults = StringBuilder()
    
    enum class VoiceState {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR,
        UNAVAILABLE
    }
    
    data class VoiceResult(
        val text: String,
        val isFinal: Boolean,
        val confidence: Float
    )
    
    fun initialize() {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            updateState(VoiceState.UNAVAILABLE)
            onStateChanged(VoiceState.UNAVAILABLE)
            return
        }
        
        isRecognitionAvailable = true
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateState(VoiceState.LISTENING)
                onStateChanged(VoiceState.LISTENING)
            }
            
            override fun onBeginningOfSpeech() {
                partialResults.clear()
                silenceTimeoutJob?.cancel()
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                silenceTimeoutJob?.cancel()
                silenceTimeoutJob = scope.launch {
                    delay(2000)
                    if (isListening) {
                        stopListening()
                    }
                }
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                updateStat
                // ============================================================================
// FILE: build.gradle.kts (Project Level)
// Location: /build.gradle.kts
// ============================================================================
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

// ============================================================================
// FILE: app/build.gradle.kts (App Level)
// Location: /app/build.gradle.kts
// ============================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.companion.Jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.companion.Jarvis"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    
    // WebSocket
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Fragment KTX for ActivityResultContracts
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ============================================================================
// FILE: AndroidManifest.xml
// Location: /app/src/main/AndroidManifest.xml
// ============================================================================
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".CompanionApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Companion Jarvis"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CompanionJarvis"
        tools:targetApi="34">
        
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.WebSocketService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
            
    </application>

</manifest>

// ============================================================================
// FILE: CompanionApplication.kt
// Location: /app/src/main/java/com/companion/Jarvis/CompanionApplication.kt
// ============================================================================
package com.companion.Jarvis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CompanionApplication : Application() {
    
    companion object {
        const val CHANNEL_ID = "companion_Jarvis_channel"
        const val CHANNEL_NAME = "Companion Jarvis Service"
        const val NOTIFICATION_ID = 1001
        
        const val AUTH_TOKEN = "your_auth_token_here"
        const val WEBSOCKET_URL = "wss://your-project.replit.dev/ws?token=$AUTH_TOKEN"
        
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val BASE_RECONNECT_DELAY_MS = 1000L
        const val MAX_RECONNECT_DELAY_MS = 30000L
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for Companion Jarvis WebSocket service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// ============================================================================
// FILE: PermissionManager.kt
// Location: /app/src/main/java/com/companion/Jarvis/permission/PermissionManager.kt
// ============================================================================
package com.companion.Jarvis.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.companion.Jarvis.R

class PermissionManager(
    private val activity: FragmentActivity,
    private val onPermissionsGranted: (() -> Unit)? = null,
    private val onPermissionsDenied: ((List<String>) -> Unit)? = null
) {
    
    companion object {
        const val REQUEST_CODE_PERMISSIONS = 2001
        
        val ESSENTIAL_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        ).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                remove(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val OPTIONAL_PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
    }
    
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResult(permissions)
        }
    
    private val deniedPermissions = mutableListOf<String>()
    private var pendingPermissions = listOf<String>()
    
    fun requestEssentialPermissions() {
        val permissionsToRequest = ESSENTIAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            onPermissionsGranted?.invoke()
            return
        }
        
        if (shouldShowRationale(permissionsToRequest)) {
            showPermissionRationaleDialog(permissionsToRequest) {
                launchPermissionRequest(permissionsToRequest)
            }
        } else {
            launchPermissionRequest(permissionsToRequest)
        }
    }
    
    fun requestOptionalPermissions() {
        val permissionsToRequest = OPTIONAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            return
        }
        
        launchPermissionRequest(permissionsToRequest)
    }
    
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun checkMultiplePermissions(permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { checkPermission(it) }
    }
    
    fun isMicrophonePermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.RECORD_AUDIO)
    }
    
    fun isCameraPermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.CAMERA)
    }
    
    fun isContactsPermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.READ_CONTACTS)
    }
    
    fun isPhonePermissionGranted(): Boolean {
        return checkPermission(Manifest.permission.CALL_PHONE)
    }
    
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
    
    private fun launchPermissionRequest(permissions: List<String>) {
        pendingPermissions = permissions
        deniedPermissions.clear()
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val permanentlyDenied = mutableListOf<String>()
        val temporarilyDenied = mutableListOf<String>()
        
        for ((permission, granted) in permissions) {
            if (!granted) {
                if (!shouldShowRationale(listOf(permission))) {
                    permanentlyDenied.add(permission)
                } else {
                    temporarilyDenied.add(permission)
                }
            }
        }
        
        if (temporarilyDenied.isNotEmpty()) {
            showPermissionRetryDialog(temporarilyDenied) {
                launchPermissionRequest(temporarilyDenied)
            }
        } else if (permanentlyDenied.isNotEmpty()) {
            showSettingsRedirectDialog(permanentlyDenied)
        } else {
            onPermissionsGranted?.invoke()
        }
    }
    
    private fun shouldShowRationale(permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true
            }
        }
        return false
    }
    
    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.CALL_PHONE -> "Phone"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substringAfterLast(".")
        }
    }
    
    private fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> 
                "The microphone is needed to capture your voice commands and enable voice interaction with the companion Jarvis."
            Manifest.permission.CAMERA -> 
                "Camera access allows the Jarvis to see and process visual information for enhanced assistance."
            Manifest.permission.READ_CONTACTS -> 
                "Contacts access enables the Jarvis to help manage your contacts and perform communication tasks."
            Manifest.permission.CALL_PHONE -> 
                "Phone permission allows the Jarvis to initiate calls on your behalf when requested."
            Manifest.permission.POST_NOTIFICATIONS -> 
                "Notifications are required to keep you informed about Jarvis status and important updates."
            else -> "This permission is required for full functionality."
        }
    }
    
    private fun showPermissionRationaleDialog(
        permissions: List<String>,
        onAccept: () -> Unit
    ) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        val descriptions = permissions.joinToString("\n\n") { 
            "• ${getPermissionDisplayName(it)}: ${getPermissionDescription(it)}"
        }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("The following permissions are needed for the Companion Jarvis to function properly:\n\n$descriptions")
            .setPositiveButton("Grant Permissions") { dialog, _ ->
                dialog.dismiss()
                onAccept()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun showPermissionRetryDialog(
        permissions: List<String>,
        onRetry: () -> Unit
    ) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Needed")
            .setMessage("$permissionNames permission(s) are required. Would you like to try again?")
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun showSettingsRedirectDialog(permissions: List<String>) {
        val permissionNames = permissions.joinToString(", ") { getPermissionDisplayName(it) }
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Permanently Denied")
            .setMessage(
                "$permissionNames permission(s) have been permanently denied. " +
                "Please enable them in the app settings to use all features."
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onPermissionsDenied?.invoke(permissions)
            }
            .setCancelable(false)
            .create()
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    fun onResume() {
        if (pendingPermissions.isNotEmpty()) {
            val stillMissing = pendingPermissions.filter {
                !checkPermission(it)
            }
            if (stillMissing.isEmpty()) {
                pendingPermissions = emptyList()
                onPermissionsGranted?.invoke()
            }
        }
    }
}

// ============================================================================
// FILE: VoiceInteractionManager.kt
// Location: /app/src/main/java/com/companion/Jarvis/voice/VoiceInteractionManager.kt
// ============================================================================
package com.companion.Jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class VoiceInteractionManager(
    private val context: Context,
    private val onVoiceCommand: (String) -> Unit,
    private val onStateChanged: (VoiceState) -> Unit
) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isTtsInitialized = false
    private var isRecognitionAvailable = false
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val ttsQueue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var silenceTimeoutJob: Job? = null
    private var partialResults = StringBuilder()
    
    enum class VoiceState {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR,
        UNAVAILABLE
    }
    
    data class VoiceResult(
        val text: String,
        val isFinal: Boolean,
        val confidence: Float
    )
    
    fun initialize() {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            updateState(VoiceState.UNAVAILABLE)
            onStateChanged(VoiceState.UNAVAILABLE)
            return
        }
        
        isRecognitionAvailable = true
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateState(VoiceState.LISTENING)
                onStateChanged(VoiceState.LISTENING)
            }
            
            override fun onBeginningOfSpeech() {
                partialResults.clear()
                silenceTimeoutJob?.cancel()
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                silenceTimeoutJob?.cancel()
                silenceTimeoutJob = scope.launch {
                    delay(2000)
                    if (isListening) {
                        stopListening()
                    }
                }
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                updateState(VoiceState.PROCESSING)
                onStateChanged(VoiceState.PROCESSING)
            }
       