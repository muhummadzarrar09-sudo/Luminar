package dev.recto.reader.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A shelf. Kindle calls these Collections; the idea is the same - a named
 * group a book can belong to, and a book can be in several at once.
 */
@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Many-to-many join. Both foreign keys cascade, so deleting a book or a
 * collection cleans up its memberships automatically rather than leaving
 * orphan rows behind.
 */
@Entity(
    tableName = "book_collections",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId"), Index("bookId")]
)
data class BookCollectionCrossRef(
    val bookId: Long,
    val collectionId: Long
)

/** A collection plus how many books are in it, for the filter chips. */
data class CollectionWithCount(
    val id: Long,
    val name: String,
    val bookCount: Int
)

@Dao
interface CollectionDao {

    @Query(
        """
        SELECT c.id AS id, c.name AS name,
               (SELECT COUNT(*) FROM book_collections bc WHERE bc.collectionId = c.id) AS bookCount
        FROM collections c
        ORDER BY c.name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<CollectionWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): CollectionEntity?

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBook(ref: BookCollectionCrossRef)

    @Query("DELETE FROM book_collections WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBook(bookId: Long, collectionId: Long)

    @Query("SELECT collectionId FROM book_collections WHERE bookId = :bookId")
    suspend fun collectionIdsFor(bookId: Long): List<Long>

    @Query(
        """
        SELECT b.* FROM books b
        INNER JOIN book_collections bc ON bc.bookId = b.id
        WHERE bc.collectionId = :collectionId
        ORDER BY COALESCE(b.lastOpenedAt, b.addedAt) DESC
        """
    )
    fun observeBooksIn(collectionId: Long): Flow<List<BookEntity>>
}
