package com.example.adhdlist.data.model

import androidx.room.Embedded

data class ListWithProgress(
    @Embedded val list: TaskList,
    val total: Int,
    val done: Int
)
