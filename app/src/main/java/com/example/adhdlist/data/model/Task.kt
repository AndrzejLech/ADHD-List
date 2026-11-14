package com.example.adhdlist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.io.Serializable

// Every column is a constructor property so equals() and copy() cover all of them (see TaskList).
@Entity(tableName = "task_table", foreignKeys =  [ForeignKey(
    entity = TaskList::class,
    parentColumns = arrayOf("list_id"),
    childColumns = arrayOf("list_id"),
    onDelete = ForeignKey.CASCADE
)])
data class Task(
    @ColumnInfo(name = "list_id")
    var listId: Int = 0,
    val message: String,
    @ColumnInfo(name = "state")
    var state: Boolean = false,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "task_id")
    var id: Int = 0,
    @ColumnInfo(name = "position", defaultValue = "0")
    var position: Int = 0,
    // Firestore document id; set only while the task's list is shared.
    @ColumnInfo(name = "remote_id")
    var remoteId: String? = null
) : Serializable
