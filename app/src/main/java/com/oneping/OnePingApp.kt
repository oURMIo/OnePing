package com.oneping

import android.app.Application
import android.content.Context
import com.oneping.data.Repository
import com.oneping.engine.CooldownEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

class OnePingApp : Application() {

    lateinit var engine: CooldownEngine
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ioScope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
        engine = CooldownEngine(Repository.get(this), ioScope)
        // Small DataStore file; must be loaded before the first notification is judged.
        runBlocking { engine.load(appScope) }
    }

    companion object {
        const val TAG = "OnePing"

        /** True while the notification listener is bound. */
        val listenerConnected = MutableStateFlow(false)

        fun engine(context: Context): CooldownEngine = (context.applicationContext as OnePingApp).engine
    }
}
