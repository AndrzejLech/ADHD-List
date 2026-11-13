package com.example.adhdlist.domain.database.lists

import com.example.adhdlist.data.room.list.ListRepository
import javax.inject.Inject

class JoinListUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    suspend fun execute(code: String) = listRepository.joinList(code)
}
