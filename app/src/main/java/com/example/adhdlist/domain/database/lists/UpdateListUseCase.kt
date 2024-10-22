package com.example.adhdlist.domain.database.lists

import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListRepository
import javax.inject.Inject

class UpdateListUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    suspend fun execute(list: TaskList) = listRepository.updateList(list)
}