package com.sedsoftware.blinktracker.components.preferences

import com.sedsoftware.blinktracker.components.preferences.model.PermissionStateNotification
import kotlinx.coroutines.flow.Flow

interface BlinkPreferences {

    val models: Flow<Model>
    val initial: Model

    fun onMinimalThresholdChanged(value: Float)
    fun onNotifySoundChanged(value: Boolean)
    fun onNotifyVibrationChanged(value: Boolean)
    fun onLaunchMinimizedChanged(value: Boolean)
    fun onReplacePipChanged(value: Boolean)
    fun onPermissionGranted()
    fun onPermissionDenied()

    data class Model(
        val selectedThreshold: Float,
        val notifySoundChecked: Boolean,
        val notifyVibrationChecked: Boolean,
        val launchMinimized: Boolean,
        val replacePip: Boolean,
        val permissionState: PermissionStateNotification,
        val showRationale: Boolean,
    )

    sealed class Output {
        data class ErrorCaught(val throwable: Throwable) : Output()
    }
}
