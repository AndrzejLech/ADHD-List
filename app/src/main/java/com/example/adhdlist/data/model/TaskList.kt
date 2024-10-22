package com.example.adhdlist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "task_list_table")
data class TaskList(
    var name: String
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "list_id")
    var id: Int = 0
}