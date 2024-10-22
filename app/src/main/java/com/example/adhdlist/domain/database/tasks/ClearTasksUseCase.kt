package com.example.adhdlist.domain.database.tasks

import com.example.adhdlist.data.room.task.TaskRepository
import javax.inject.Inject

class ClearTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend fun execute(listId: Int) = taskRepository.clearAllTasksFromList(listId)
}