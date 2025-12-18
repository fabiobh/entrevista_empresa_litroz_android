package com.fabiocunha.todoapplitroz.di

import com.fabiocunha.todoapplitroz.data.monitor.NetworkMonitorImpl
import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Hilt module for network dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    
    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(networkMonitorImpl: NetworkMonitorImpl): NetworkMonitor
    
    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            return OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()
        }
        
        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
            return Retrofit.Builder()
                .baseUrl("https://6941e8b1686bc3ca81683e6d.mockapi.io/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        
        @Provides
        @Singleton
        fun provideTaskApiService(retrofit: Retrofit): TaskApiService {
            return retrofit.create(TaskApiService::class.java)
        }
    }
}