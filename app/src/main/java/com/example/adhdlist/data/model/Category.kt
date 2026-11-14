package com.example.adhdlist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// A section inside a list (Zakupy → Warzywa → Cukinia). One level only: categories don't nest.
@Entity(
    tableName = "category_table",
    foreignKeys = [ForeignKey(
        entity = TaskList::class,
        parentColumns = ["list_id"],
        childColumns = ["list_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("list_id")]
)
data class Category(
    @ColumnInfo(name = "list_id")
    var listId: Int,
    var name: String,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    var id: Int = 0,
    @ColumnInfo(name = "position", defaultValue = "0")
    var position: Int = 0,
    // Firestore document id; set only while the list is shared.
    @ColumnInfo(name = "remote_id")
    var remoteId: String? = null,
    // Open or closed on this phone. Never synced, so shared lists don't fold for the others.
    @ColumnInfo(name = "expanded", defaultValue = "1")
    var expanded: Boolean = true
)
