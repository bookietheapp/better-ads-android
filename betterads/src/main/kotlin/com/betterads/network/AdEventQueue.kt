package com.betterads.network

import com.betterads.model.AdEvent
import com.betterads.model.BetterAdsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

internal typealias AdEventFlushScheduler = (suspend () -> Unit) -> Unit

internal data class EventsPostResult(
    val accepted: Int,
    val rejected: List<RejectedEventItem>,
)

internal data class RejectedEventItem(
    val index: Int,
    val eventId: String?,
    val errors: List<String>,
)

/** Buffers ad events locally and flushes batches to `POST /api/v1/events`. */
internal class AdEventQueue(
    private val store: AdEventStore,
    private val postEvents: suspend (List<AdEvent>) -> EventsPostResult,
    private val flushScheduler: AdEventFlushScheduler = { operation ->
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { operation() }
        Unit
    },
    private val logger: Logger = Logger.getLogger("com.betterads.AdEventQueue"),
) {
    private val lock = Any()
    private var isFlushing = false
    private var authPaused = false

    fun enqueue(event: AdEvent) {
        synchronized(lock) {
            if (authPaused) {
                logger.warning("Skipping event flush — API key rejected (401)")
            }
        }
        append(event)
        scheduleFlush()
    }

    suspend fun flushNow() = flush()

    fun resumeAfterAuthFailure() {
        synchronized(lock) { authPaused = false }
        scheduleFlush()
    }

    val pendingCount: Int
        get() = store.load().size

    private fun append(event: AdEvent) {
        val pending = store.load().toMutableList()
        pending += event
        store.save(pending)
    }

    private fun scheduleFlush() {
        flushScheduler { flush() }
    }

    private suspend fun flush() {
        synchronized(lock) {
            if (isFlushing || authPaused) return
            isFlushing = true
        }

        try {
            while (true) {
                val batch = nextBatch()
                if (batch.isEmpty()) return

                try {
                    val result = postEvents(batch)
                    applySuccess(batch, result)
                } catch (e: BetterAdsError.HttpStatus) {
                    when (e.code) {
                        401 -> {
                            synchronized(lock) { authPaused = true }
                            logger.severe("Events flush paused — invalid App API key (401)")
                            return
                        }
                        in 400..499 -> {
                            logger.severe("Events flush dropped batch — client error ${e.code}")
                            removeEvents(batch)
                            return
                        }
                        else -> {
                            logger.log(Level.WARNING, "Events flush failed — will retry", e)
                            return
                        }
                    }
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Events flush failed — will retry", e)
                    return
                }

                if (nextBatch().isEmpty()) return
            }
        } finally {
            synchronized(lock) { isFlushing = false }
        }
    }

    private fun nextBatch(): List<AdEvent> = store.load().take(MAX_BATCH_SIZE)

    private fun applySuccess(batch: List<AdEvent>, result: EventsPostResult) {
        val rejectedIndices = result.rejected.map { it.index }.toSet()
        val rejectedIds = result.rejected.mapNotNull { it.eventId?.lowercase() }.toSet()

        val toRemove = batch.filterIndexed { index, event ->
            index !in rejectedIndices &&
                event.eventId.lowercase() !in rejectedIds
        }
        removeEvents(toRemove)

        result.rejected.forEach { rejection ->
            logger.warning(
                "Rejected event at index ${rejection.index}: ${rejection.errors.joinToString()}",
            )
        }
    }

    private fun removeEvents(events: List<AdEvent>) {
        if (events.isEmpty()) return
        val removeIds = events.map { it.eventId }.toSet()
        val pending = store.load().filterNot { it.eventId in removeIds }
        store.save(pending)
    }

    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}
