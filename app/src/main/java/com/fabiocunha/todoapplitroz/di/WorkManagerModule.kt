package com.fabiocunha.todoapplitroz.di

import android.content.Context
import androidx.work.WorkManager
import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.sync.AppLifecycleSyncObserver
import com.fabiocunha.todoapplitroz.data.sync.ConnectivitySyncObserver
import com.fabiocunha.todoapplitroz.data.sync.SyncScheduler
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for WorkManager dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideSyncScheduler(workManager: WorkManager): SyncScheduler {
        return SyncScheduler(workManager)
    }
    
    @Provides
    @Singleton
    fun provideConnectivitySyncObserver(
        networkMonitor: NetworkMonitor,
        syncScheduler: SyncScheduler
    ): ConnectivitySyncObserver {
        return ConnectivitySyncObserver(networkMonitor, syncScheduler)
    }
    
    @Provides
    @Singleton
    fun provideAppLifecycleSyncObserver(
        localDataSource: LocalTaskDataSource,
        syncScheduler: SyncScheduler
    ): AppLifecycleSyncObserver {
        return AppLifecycleSyncObserver(localDataSource, syncScheduler)
    }
}