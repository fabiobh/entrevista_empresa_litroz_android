# Implementation Plan

- [x] 1. Set up project dependencies and core structure
  - Add Room, Retrofit, Hilt, WorkManager, and Kotest dependencies to build.gradle.kts
  - Configure Hilt for dependency injection
  - Set up basic package structure following Clean Architecture
  - _Requirements: 9.1, 9.4_

- [x] 2. Implement domain layer models and interfaces
  - [x] 2.1 Create Task domain model with SyncStatus enum
    - Define Task data class with id, description, completion status, timestamps, and sync status
    - Create SyncStatus enum with SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE values
    - _Requirements: 1.1, 2.5, 4.5_

  - [x] 2.2 Define repository interface and use cases
    - Create TaskRepository interface with CRUD operations and sync methods
    - Implement CreateTaskUseCase, UpdateTaskUseCase, DeleteTaskUseCase, SyncTasksUseCase
    - _Requirements: 9.2, 9.3_

  - [x] 2.3 Write property test for Task domain model
    - **Property 7: Data Validation Consistency**
    - **Validates: Requirements 1.5**

- [x] 3. Implement data layer - local storage
  - [x] 3.1 Create Room database entities and DAOs
    - Define TaskEntity with Room annotations
    - Create TaskDao with CRUD operations and sync status queries
    - Set up AppDatabase with proper configuration
    - _Requirements: 1.2, 2.1, 3.1, 4.1_

  - [x] 3.2 Implement local data source
    - Create LocalTaskDataSource that wraps TaskDao operations
    - Add error handling and data mapping between entities and domain models
    - _Requirements: 5.1, 5.2_

  - [x] 3.3 Write property test for local data operations
    - **Property 1: Task Operations Persistence**
    - **Validates: Requirements 1.1, 1.2, 2.1, 3.1, 4.1**

- [x] 4. Implement data layer - remote API
  - [x] 4.1 Create API data transfer objects and service interface
    - Define TaskDto for API communication
    - Create TaskApiService with Retrofit annotations for CRUD operations
    - Set up Retrofit configuration with proper error handling
    - _Requirements: 6.1, 6.2_

  - [x] 4.2 Implement remote data source
    - Create RemoteTaskDataSource that wraps API service calls
    - Add network error handling and data mapping between DTOs and domain models
    - _Requirements: 6.5_

  - [x] 4.3 Write property test for API operations
    - **Property 3: Sync Engine Completeness**
    - **Validates: Requirements 6.1, 6.2, 6.5**

- [x] 5. Implement network monitoring
  - [x] 5.1 Create NetworkMonitor implementation
    - Implement NetworkMonitor interface using ConnectivityManager
    - Provide Flow<Boolean> for connectivity state changes
    - _Requirements: 5.3, 8.3_

- [x] 6. Implement repository with sync logic
  - [x] 6.1 Create TaskRepository implementation
    - Coordinate between local and remote data sources
    - Implement offline-first strategy with proper sync status management
    - Handle network availability for online/offline operations
    - _Requirements: 1.3, 2.2, 2.3, 3.2, 3.3, 4.2, 4.3_

  - [x] 6.2 Implement sync engine logic
    - Create sync methods that handle pending operations
    - Implement conflict resolution using last-write-wins strategy
    - Add retry logic with exponential backoff for failed sync operations
    - _Requirements: 6.3, 6.4_

  - [x] 6.3 Write property test for offline operations
    - **Property 2: Offline Operation Consistency**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x] 6.4 Write property test for conflict resolution
    - **Property 4: Conflict Resolution Consistency**
    - **Validates: Requirements 6.3**

  - [x] 6.5 Write property test for error handling
    - **Property 5: Error Handling and Recovery**
    - **Validates: Requirements 1.4, 2.4, 3.4, 4.4**

- [x] 7. Checkpoint - Ensure all tests pass.
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement background synchronization
  - [x] 8.1 Create WorkManager sync worker
    - Implement SyncWorker that handles background synchronization
    - Configure work constraints for network availability
    - Add proper retry policy and error handling
    - _Requirements: 7.1, 7.2, 7.4_

  - [x] 8.2 Integrate background sync scheduling
    - Schedule sync work when app goes to background with pending changes
    - Handle connectivity changes to trigger immediate sync when online
    - _Requirements: 7.3_

  - [x] 8.3 Write property test for background sync
    - **Property 6: Background Sync Reliability**
    - **Validates: Requirements 7.1, 7.2, 7.4**

- [x] 9. Implement presentation layer - ViewModels
  - [x] 9.1 Create TaskListViewModel
    - Manage UI state for task list screen
    - Handle user interactions for CRUD operations
    - Provide loading states and error handling
    - _Requirements: 8.2, 9.3_

  - [x] 9.2 Implement ViewModel state management
    - Create UI state data classes for different screen states
    - Use StateFlow for reactive UI updates
    - Handle threading properly to maintain UI responsiveness
    - _Requirements: 8.3_

  - [x] 9.3 Write property test for threading safety
    - **Property 9: Threading Safety**
    - **Validates: Requirements 8.3**

  - [x] 9.4 Write property test for timestamp management
    - **Property 8: Timestamp Management**
    - **Validates: Requirements 2.5, 4.5**

- [x] 10. Implement UI with Jetpack Compose
  - [x] 10.1 Create task list screen composables
    - Build TaskListScreen with LazyColumn for efficient rendering
    - Create TaskItem composable for individual task display
    - Add loading indicators and error message displays
    - _Requirements: 8.1, 8.2_

  - [x] 10.2 Implement task creation and editing UI
    - Create AddTaskDialog or screen for task creation
    - Implement inline editing for task descriptions
    - Add completion toggle functionality with visual feedback
    - _Requirements: 1.1, 2.1, 4.1_

  - [x] 10.3 Add delete functionality with confirmation
    - Implement swipe-to-delete or delete button
    - Add confirmation dialog for task deletion
    - Provide visual feedback for all user interactions
    - _Requirements: 3.1, 3.5_

  - [x] 10.4 Write UI tests for task operations
    - Create Compose tests for task creation, editing, deletion, and completion
    - Test user interaction flows and state changes
    - _Requirements: 10.3_

- [x] 11. Integrate dependency injection
  - [x] 11.1 Set up Hilt modules
    - Create DatabaseModule for Room database injection
    - Create NetworkModule for Retrofit and API service injection
    - Create RepositoryModule for repository implementation binding
    - _Requirements: 9.4_

  - [x] 11.2 Configure application and activity injection
    - Set up HiltAndroidApp and inject ViewModels
    - Configure proper scoping for singleton and activity-scoped dependencies
    - _Requirements: 9.5_

- [x] 12. Add offline status indication
  - [x] 12.1 Implement connectivity status UI
    - Create NetworkStatusIndicator composable
    - Show offline/online status in the UI
    - Display sync status for pending operations
    - _Requirements: 5.4_

- [x] 13. Final integration and testing
  - [x] 13.1 Wire up complete application flow
    - Connect all layers from UI to data sources
    - Ensure proper error propagation and handling
    - Test complete offline-to-online synchronization scenarios
    - _Requirements: 5.3, 6.1_

  - [x] 13.2 Write integration tests
    - Create end-to-end tests for complete user workflows
    - Test offline/online synchronization scenarios
    - Verify data consistency across app restarts
    - _Requirements: 10.2, 10.4_

- [x] 14. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. Debug API connectivity issues
  - [x] 15.1 Fix TaskDto serialization compatibility
    - Remove kotlinx.serialization annotations and use Gson-compatible data class
    - Ensure TaskDto works properly with GsonConverterFactory
    - _Requirements: 6.1, 6.2_
  
  - [x] 15.2 Add API connectivity debugging
    - Add detailed logging to RemoteTaskDataSource to capture API responses
    - Create a simple API test function to verify mockapi.io connectivity
    - Add error handling for common API issues (404, network errors)
    - _Requirements: 6.1, 6.5_
  
  - [x] 15.3 Verify mockapi.io resource configuration
    - Ensure "tasks" resource is created in mockapi.io dashboard
    - Verify the resource schema matches TaskDto structure
    - Test API endpoints manually if needed
    - _Requirements: 6.1, 6.2_
  
  - [x] 15.4 Fix Hilt WorkManager configuration
    - Configure HiltWorkerFactory in TodoApplication
    - Add proper WorkManager initialization with Hilt
    - Disable default WorkManager initialization in AndroidManifest
    - Add missing Hilt Work kapt processor
    - _Requirements: 7.1, 7.2_
  
  - [x] 15.5 Fix ID compatibility with MockAPI.io Object ID format
    - Replace UUID generation with MongoDB ObjectId-compatible format
    - Add generateObjectId() function to create 24-character hex strings
    - Ensure compatibility with MockAPI.io Object ID field type
    - _Requirements: 6.1, 6.2_
  
  - [x] 15.6 Fix sync logic for PENDING_CREATE tasks
    - Allow immediate sync for tasks with PENDING_CREATE status when updated
    - Ensure tasks are created on server before attempting updates
    - Fix 404 errors when updating non-existent tasks on server
    - _Requirements: 6.1, 6.3_