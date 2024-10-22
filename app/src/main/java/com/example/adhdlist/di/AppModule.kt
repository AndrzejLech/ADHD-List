package com.example.adhdlist.di

import android.content.Context
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.data.room.task.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideTaskRepository(
        @ApplicationContext context: Context
    ): TaskRepository {
        return TaskRepository(context)
    }

    @Provides
    @Singleton
    fun provideListRepository(
        @ApplicationContext context: Context
    ): ListRepository {
        return ListRepository(context)
    }


    @Provides
    @Singleton
    fun provideNavigation(): NavigationRepository {
        return NavigationRepository()
    }
}