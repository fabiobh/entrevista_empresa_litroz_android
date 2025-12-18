package com.fabiocunha.todoapplitroz.domain.monitor

import kotlinx.coroutines.flow.Flow

/**
 * Interface for monitoring network connectivity
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}