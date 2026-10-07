package com.pdfmaster.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC, modifiedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE path = :path")
    suspend fun get(path: String): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun all(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE path = :path")
    suspend fun delete(path: String)

    @Query("UPDATE documents SET starred = :starred WHERE path = :path")
    suspend fun setStarred(path: String, starred: Boolean)

    @Query("UPDATE documents SET lastOpenedAt = :at WHERE path = :path")
    suspend fun touch(path: String, at: Long)

    @Query("UPDATE documents SET tags = :tags WHERE path = :path")
    suspend fun setTags(path: String, tags: String)

    @Insert
    suspend fun insertText(text: DocumentTextEntity)

    @Query("DELETE FROM document_text WHERE path = :path")
    suspend fun deleteText(path: String)

    @Query("SELECT DISTINCT path FROM document_text WHERE document_text MATCH :query")
    suspend fun searchText(query: String): List<String>
}
