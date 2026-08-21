package com.betterads.network

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Periodically and on app background, flushes the local ad-events queue. */
internal class AdEventFlushCoordinator(
    private val queue: AdEventQueue,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var periodicJob: Job? = null
    private var lifecycleRegistered = false

    fun start() {
        stop()
        periodicJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                queue.flushNow()
            }
        }
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            lifecycleRegistered = true
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
        if (lifecycleRegistered) {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
            }
            lifecycleRegistered = false
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch { queue.flushNow() }
    }

    companion object {
        const val FLUSH_INTERVAL_MS = 30_000L
    }
}
