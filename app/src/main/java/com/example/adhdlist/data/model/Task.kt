package com.example.adhdlist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.io.Serializable

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
    var state: Boolean = false
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "task_id")
    var id: Int = 0
}