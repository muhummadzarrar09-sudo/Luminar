# Luminar Architecture Documentation

## Architectural Overview
Luminar follows Clean Architecture principles combined with the Model-View-ViewModel (MVVM) presentation pattern. The codebase enforces strict separation of concerns across presentation, domain, and data layers, utilizing unidirectional data flow (UDF) and reactive Kotlin Coroutines/Flows.

## Package Structure
```
com.luminar.reader
├── data
│   ├── local
│   │   ├── datastore     # DataStore preferences backed storage for user settings and reader flags
│   │   └── db            # Room SQLite database setup, DAOs, and type converters
│   ├── model             # Core domain entities and database models (Book, ReadingProgress, BookInsight)
│   └── repository        # Repository implementations handling local storage, PDF rendering, and file IO
├── di                    # Hilt dependency injection modules providing singleton app dependencies
├── domain
│   └── usecase           # Encapsulated single-responsibility business logic actions
├── navigation            # Type-safe Jetpack Compose navigation host, screens, and route definitions
├── network               # Retrofit client definitions and endpoints for local Ollama LLM integration
├── presentation
│   ├── library           # Library grid UI, state management, and file import handling
│   ├── reader            # Reader overlay controls, hardware key binding, and PDF view composables
│   ├── settings          # User settings screen configuring AI host, themes, and reader behavior
│   └── theme             # Material3 design system color schemes, typography, and theme extensions
└── worker                # WorkManager background jobs for asynchronous document text indexing and LLM analysis
```

## Data Flow Diagram
```
+-------------------------------------------------------------------------+
|                        PRESENTATION LAYER (Compose UI)                  |
|  +-------------------+      +-------------------+      +-------------+  |
|  |   LibraryScreen   |      |    ReaderScreen   |      | Search/Toc  |  |
|  +---------+---------+      +---------+---------+      +------+------+  |
|            |                          |                       |         |
|            v                          v                       v         |
|  +-------------------+      +-------------------+      +-------------+  |
|  | LibraryViewModel  |      |  ReaderViewModel  |      | ViewModels  |  |
|  +---------+---------+      +---------+---------+      +------+------+  |
+------------|--------------------------|-----------------------|---------+
             |                          |                       |          
             v                          v                       v          
+-------------------------------------------------------------------------+
|                           DOMAIN LAYER (Use Cases)                      |
|  +-------------------+      +-------------------+      +-------------+  |
|  | ImportBookUseCase |      | SaveProgressUseCase|     | GetBooks... |  |
|  +---------+---------+      +---------+---------+      +------+------+  |
+------------|--------------------------|-----------------------|---------+
             |                          |                       |          
             +--------------------------+-----------------------+          
                                        |                                  
                                        v                                  
+-------------------------------------------------------------------------+
|                           DATA LAYER (Repositories)                     |
|                   +---------------------------------------+             |
|                   |          BookRepositoryImpl           |             |
|                   +-----+---------------------------+-----+             |
|                         |                           |                   |
|                         v                           v                   |
|              +--------------------+      +--------------------+         |
|              |      Room DB       |      | PreferencesDataStore|        |
|              | (BookDao, Fts, Toc)|      | (Theme, KeepAlive) |         |
|              +--------------------+      +--------------------+         |
+-------------------------------------------------------------------------+
```

## Room Database Schema

### Entities
1. **`books` (`Book`)**
   - `id`: Long (Primary Key, Auto-generate)
   - `title`: String
   - `filePath`: String (Unique Index)
   - `coverPath`: String?
   - `format`: BookFormat (Enum: PDF, EPUB)
   - `totalPages`: Int
   - `addedAt`: Long
   - `lastOpenedAt`: Long?
   - `isAnalyzed`: Boolean

2. **`reading_progress` (`ReadingProgress`)**
   - `bookId`: Long (Primary Key, Foreign Key -> `books.id` CASCADE)
   - `currentPage`: Int
   - `scrollOffset`: Float
   - `lastReadAt`: Long

3. **`book_insights` (`BookInsight`)**
   - `id`: Long (Primary Key, Auto-generate)
   - `bookId`: Long (Foreign Key -> `books.id` CASCADE)
   - `topic`: String
   - `pageRefs`: String
   - `summary`: String
   - `createdAt`: Long

### Entity Relationships
- **Book (1) : (0..1) ReadingProgress** (Linked via `bookId`)
- **Book (1) : (0..N) BookInsight** (Linked via `bookId`)

## Key Architectural Patterns
- **Unidirectional Data Flow (UDF)**: UI state is modeled as immutable `StateFlow` classes emitted by ViewModels. User interactions trigger discrete `Event` sealed classes passed into `onEvent()` handlers.
- **Repository Pattern**: Hides internal storage details (Room DAOs, local Filesystem, Retrofit network calls) behind domain interfaces.
- **Use Cases**: Encapsulate individual business transactions, making them reusable across multiple ViewModels and testable in isolation.
