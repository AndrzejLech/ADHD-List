package com.example.adhdlist.domain.database.lists

import com.example.adhdlist.data.room.list.ListRepository
import javax.inject.Inject

class ShareListUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    suspend fun execute(listId: Int) = listRepository.shareList(listId)
}
