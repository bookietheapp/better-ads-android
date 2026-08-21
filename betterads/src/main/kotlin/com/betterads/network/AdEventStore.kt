package com.betterads.network

import android.content.Context
import com.betterads.model.AdEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface AdEventStore {
    fun load(): List<AdEvent>
    fun save(events: List<AdEvent>)
}

internal class InMemoryAdEventStore : AdEventStore {
    private val lock = ReentrantLock()
    private var events: List<AdEvent> = emptyList()

    override fun load(): List<AdEvent> = lock.withLock { events }

    override fun save(events: List<AdEvent>) {
        lock.withLock { this.events = events }
    }
}

internal class SharedPreferencesAdEventStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AdEventStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = ReentrantLock()

    override fun load(): List<AdEvent> = lock.withLock {
        val raw = prefs.getString(EVENTS_KEY, null) ?: return emptyList()
        runCatching { json.decodeFromString<List<AdEvent>>(raw) }.getOrDefault(emptyList())
    }

    override fun save(events: List<AdEvent>) {
        lock.withLock {
            prefs.edit().putString(EVENTS_KEY, json.encodeToString(events)).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "com.betterads.events"
        private const val EVENTS_KEY = "pending_events"
    }
}
