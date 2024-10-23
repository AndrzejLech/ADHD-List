package com.example.adhdlist.domain.database.tasks

import com.example.adhdlist.data.room.task.TaskRepository
import javax.inject.Inject

class RefreshTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    fun execute(listId: Int) = taskRepository.resetAllTasks(listId)
}