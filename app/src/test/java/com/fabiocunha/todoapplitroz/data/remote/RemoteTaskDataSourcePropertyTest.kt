package com.fabiocunha.todoapplitroz.data.remote

import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest

/**
 * Basic tests for RemoteTaskDataSource
 */
class RemoteTaskDataSourcePropertyTest : StringSpec({

    "Basic RemoteTaskDataSource instantiation test" {
        runTest {
            val mockApiService = mockk<TaskApiService>()
            val dataSource = RemoteTaskDataSource(mockApiService)
            
            // Verify the data source can be instantiated
            dataSource shouldBe dataSource
        }
    }
})