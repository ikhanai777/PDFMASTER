package com.pdfmaster.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/** One document in the on-device library. [path] is an absolute path inside app storage. */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val path: String,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedAt: Long,
    val starred: Boolean = false,
    val encrypted: Boolean = false,
    val tags: String = "",
)

/** Full-text index of each document's text, for search across every PDF. */
@Fts4
@Entity(tableName = "document_text")
data class DocumentTextEntity(
    val path: String,
    val content: String,
)
