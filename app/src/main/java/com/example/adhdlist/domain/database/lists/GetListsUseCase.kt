package com.example.adhdlist.domain.database.lists

import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetListsUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    fun execute(): Result<Flow<MutableList<TaskList>>, Exception> = listRepository.getLists()
}