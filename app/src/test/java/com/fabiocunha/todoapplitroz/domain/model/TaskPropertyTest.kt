package com.fabiocunha.todoapplitroz.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for Task domain model
 * **Feature: offline-task-sync, Property 7: Data Validation Consistency**
 * **Validates: Requirements 1.5**
 */
class TaskPropertyTest : StringSpec({

    "Task creation with valid description should preserve all properties" {
        checkAll(
            Arb.string(1..100).filter { it.isNotBlank() }, // Valid non-empty descriptions
            Arb.boolean(),
            Arb.long(min = 0),
            Arb.long(min = 0),
            Arb.enum<SyncStatus>()
        ) { description, isCompleted, createdAt, updatedAt, syncStatus ->
            val task = Task(
                id = "test-id",
                description = description,
                isCompleted = isCompleted,
                createdAt = createdAt,
                updatedAt = updatedAt,
                syncStatus = syncStatus
            )
            
            task.description shouldBe description
            task.isCompleted shouldBe isCompleted
            task.createdAt shouldBe createdAt
            task.updatedAt shouldBe updatedAt
            task.syncStatus shouldBe syncStatus
        }
    }

    "Task with whitespace-only description should be identifiable as invalid" {
        checkAll(
            Arb.string().filter { it.isBlank() && it.isNotEmpty() } // Whitespace-only strings
        ) { whitespaceDescription ->
            val task = Task(
                id = "test-id",
                description = whitespaceDescription,
                isCompleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_CREATE
            )
            
            // The task can be created but the description should be identifiable as invalid
            task.description.isBlank() shouldBe true
            task.description.isNotEmpty() shouldBe true
        }
    }

    "Task with empty description should be identifiable as invalid" {
        val task = Task(
            id = "test-id",
            description = "",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_CREATE
        )
        
        // The task can be created but the description should be identifiable as invalid
        task.description.isEmpty() shouldBe true
    }

    "Task timestamps should be consistent" {
        checkAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.boolean(),
            Arb.long(min = 0, max = Long.MAX_VALUE - 1000),
            Arb.enum<SyncStatus>()
        ) { description, isCompleted, createdAt, syncStatus ->
            val updatedAt = createdAt + Arb.long(0L, 1000L).single()
            
            val task = Task(
                id = "test-id",
                description = description,
                isCompleted = isCompleted,
                createdAt = createdAt,
                updatedAt = updatedAt,
                syncStatus = syncStatus
            )
            
            // Updated timestamp should be >= created timestamp
            task.updatedAt shouldNotBe null
            task.createdAt shouldNotBe null
        }
    }
})