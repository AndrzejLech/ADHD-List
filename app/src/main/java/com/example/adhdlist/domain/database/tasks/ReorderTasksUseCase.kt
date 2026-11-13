package com.example.adhdlist.domain.database.tasks

import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.room.task.TaskRepository
import javax.inject.Inject

class ReorderTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend fun execute(listId: Int, ordered: List<Task>) = taskRepository.reorderTasks(listId, ordered)
}
