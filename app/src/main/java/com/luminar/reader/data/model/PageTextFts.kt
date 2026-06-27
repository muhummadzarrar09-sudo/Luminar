package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "page_text_fts")
@Fts4(contentEntity = PageContent::class)
data class PageTextFts(
    val content: String
)
