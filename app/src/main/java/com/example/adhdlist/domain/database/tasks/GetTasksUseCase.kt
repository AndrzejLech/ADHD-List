package com.example.adhdlist.domain.database.tasks

import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.room.task.TaskRepository
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    fun execute(listId: Int): Result<Flow<MutableList<Task>>, Exception> = taskRepository.getTasks(listId)
}