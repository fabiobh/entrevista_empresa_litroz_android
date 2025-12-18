package com.fabiocunha.todoapplitroz

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import androidx.work.WorkManager
import com.fabiocunha.todoapplitroz.data.sync.AppLifecycleSyncObserver
import com.fabiocunha.todoapplitroz.data.sync.ConnectivitySyncObserver
import com.fabiocunha.todoapplitroz.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class configured for Hilt dependency injection
 */
@HiltAndroidApp
class TodoApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var syncScheduler: SyncScheduler
    
    @Inject
    lateinit var connectivitySyncObserver: ConnectivitySyncObserver
    
    @Inject
    lateinit var appLifecycleSyncObserver: AppLifecycleSyncObserver
    
    override fun onCreate() {
        super.onCreate()
        
        // Schedule periodic background sync
        syncScheduler.schedulePeriodicSync()
        
        // Start observing connectivity changes for immediate sync
        connectivitySyncObserver.startObserving(ProcessLifecycleOwner.get().lifecycleScope)
        
        // Register lifecycle observer for background sync scheduling
        appLifecycleSyncObserver.initialize(ProcessLifecycleOwner.get().lifecycleScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleSyncObserver)
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}