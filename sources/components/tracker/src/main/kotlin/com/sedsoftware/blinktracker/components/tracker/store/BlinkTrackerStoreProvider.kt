@file:OptIn(ExperimentalMviKotlinApi::class)

package com.sedsoftware.blinktracker.components.tracker.store

import android.util.Log
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.core.utils.ExperimentalMviKotlinApi
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutorScope
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.sedsoftware.blinktracker.components.tracker.model.VisionFaceData
import com.sedsoftware.blinktracker.components.tracker.store.BlinkTrackerStore.Intent
import com.sedsoftware.blinktracker.components.tracker.store.BlinkTrackerStore.Label
import com.sedsoftware.blinktracker.components.tracker.store.BlinkTrackerStore.State
import com.sedsoftware.blinktracker.settings.Settings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock.System
import kotlin.time.Duration.Companion.milliseconds

internal class BlinkTrackerStoreProvider(
    private val storeFactory: StoreFactory,
    private val settings: Settings,
) {

    fun provide(): BlinkTrackerStore =
        object : BlinkTrackerStore, Store<Intent, State, Label> by storeFactory.create<Intent, Action, Msg, State, Label>(
            name = "BlinkTrackerStore",
            initialState = State(),
            bootstrapper = coroutineBootstrapper {
                launch {
                    dispatch(Action.ObserveThresholdOption)
                    dispatch(Action.ObserveNotifySoundOption)
                    dispatch(Action.ObserveNotifyVibrationOption)

                    (0..Int.MAX_VALUE)
                        .asSequence()
                        .asFlow()
                        .onEach {
                            delay(TIMER_DELAY)
                            dispatch(Action.OnTick)
                        }
                }
            },
            executorFactory = coroutineExecutorFactory {
                onAction<Action.ObserveThresholdOption> {
                    launch(getExceptionHandler(this)) {
                        settings.getPerMinuteThreshold()
                            .onEach { Msg.ObservedThresholdOptionChanged(it) }
                    }
                }
                onAction<Action.ObserveNotifySoundOption> {
                    launch(getExceptionHandler(this)) {
                        settings.getNotifySoundEnabled()
                            .onEach { Msg.ObservedSoundOptionChanged(it) }
                    }
                }
                onAction<Action.ObserveNotifyVibrationOption> {
                    launch(getExceptionHandler(this)) {
                        settings.getNotifyVibrationEnabled()
                            .onEach { Msg.ObservedVibrationOptionChanged(it) }
                    }
                }

                onAction<Action.OnTick> {
                    if (state.active) {
                        val counter = state.timer

                        if (counter % MEASURE_PERIOD_SEC == 0) {
                            if (state.blinkLastMinute < state.threshold) {
                                if (state.notifyWithSound) publish(Label.SoundNotificationTriggered)
                                if (state.notifyWithVibration) publish(Label.VibrationNotificationTriggered)
                            }
                            dispatch(Msg.ResetMinute)
                        }

                        dispatch(Msg.Tick(counter + 1))
                    }
                }

                onIntent<Intent.TrackingStarted> {
                    dispatch(Msg.TrackerStateChangedStarted(true))
                }

                onIntent<Intent.TrackingStopped> {
                    dispatch(Msg.TrackerStateChangedStarted(false))
                }

                onIntent<Intent.FaceDataChanged> {
                    dispatch(Msg.FaceDataAvailable(it.data))

                    if (it.data.hasEyesData() && state.blinkPeriodEnded()) {
                        Log.d("BLINKDEBUG", "BLINK!")
                        dispatch(Msg.Blink)
                    }
                }
            },
            reducer = { msg ->
                when (msg) {
                    is Msg.ObservedThresholdOptionChanged -> {
                        copy(threshold = msg.newValue)
                    }

                    is Msg.ObservedSoundOptionChanged -> {
                        copy(notifyWithSound = msg.newValue)
                    }

                    is Msg.ObservedVibrationOptionChanged -> {
                        copy(notifyWithVibration = msg.newValue)
                    }

                    is Msg.FaceDataAvailable -> {
                        copy(faceDetected = msg.data.faceAvailable)
                    }

                    is Msg.TrackerStateChangedStarted -> {
                        copy(active = msg.started)
                    }

                    is Msg.Tick -> {
                        copy(timer = msg.seconds)
                    }

                    is Msg.Blink -> {
                        copy(
                            blinkLastMinute = this.blinkLastMinute + 1,
                            blinksTotal = this.blinksTotal + 1,
                            lastBlink = System.now(),
                        )
                    }

                    is Msg.ResetMinute -> {
                        copy(
                            blinkLastMinute = 0
                        )
                    }
                }
            }
        ) {}

    private sealed interface Action {
        object ObserveThresholdOption : Action
        object ObserveNotifySoundOption : Action
        object ObserveNotifyVibrationOption : Action
        object OnTick : Action
    }

    private sealed interface Msg {
        data class ObservedThresholdOptionChanged(val newValue: Int) : Msg
        data class ObservedSoundOptionChanged(val newValue: Boolean) : Msg
        data class ObservedVibrationOptionChanged(val newValue: Boolean) : Msg
        data class FaceDataAvailable(val data: VisionFaceData) : Msg
        data class TrackerStateChangedStarted(val started: Boolean) : Msg
        data class Tick(val seconds: Int) : Msg
        object Blink : Msg
        object ResetMinute : Msg
    }

    private fun getExceptionHandler(scope: CoroutineExecutorScope<State, Msg, Label>): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            scope.publish(Label.ErrorCaught(throwable))
        }

    private fun VisionFaceData.hasEyesData(): Boolean =
        this.leftEye != null && this.leftEye < BLINK_THRESHOLD && this.rightEye != null && this.rightEye < BLINK_THRESHOLD

    private fun State.blinkPeriodEnded(): Boolean =
        this.lastBlink < System.now().minus(BLINK_REGISTER_PERIOD_MS.milliseconds)

    private companion object {
        const val TIMER_DELAY = 1000L
        const val BLINK_THRESHOLD = 0.25f
        const val BLINK_REGISTER_PERIOD_MS = 500L
        const val MEASURE_PERIOD_SEC = 60
    }
}
