package com.example.adhdlist.domain.database.lists

import com.example.adhdlist.data.room.list.ListRepository
import javax.inject.Inject

class ClearDatabaseBeforeTestsUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
     fun execute() = listRepository.cleanDatabaseForTests()
}