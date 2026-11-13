package com.example.adhdlist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

// Every column is a constructor property so equals() covers all of them: StateFlow and Compose
// skip "equal" values, and a list that only gained a remoteId or a new position must still update.
@Entity(tableName = "task_list_table")
data class TaskList(
    var name: String,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "list_id")
    var id: Int = 0,
    // Order on the lists screen; local to this device even for shared lists.
    @ColumnInfo(name = "position", defaultValue = "0")
    var position: Int = 0,
    // Firestore document id, which is also the join code; null for lists that are not shared.
    @ColumnInfo(name = "remote_id")
    var remoteId: String? = null
) : Serializable
