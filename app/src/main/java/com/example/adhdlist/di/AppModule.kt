package com.example.adhdlist.di

import android.content.Context
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.room.category.CategoryRepository
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.data.room.task.TaskRepository
import com.example.adhdlist.data.sync.SharedListsSync
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
    fun provideSharedListsRemote(
        @ApplicationContext context: Context
    ): SharedListsRemote {
        return SharedListsRemote(context)
    }

    @Provides
    @Singleton
    fun provideSharedListsSync(
        @ApplicationContext context: Context,
        remote: SharedListsRemote
    ): SharedListsSync {
        return SharedListsSync(context, remote)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(
        @ApplicationContext context: Context,
        remote: SharedListsRemote
    ): TaskRepository {
        return TaskRepository(context, remote)
    }

    @Provides
    @Singleton
    fun provideCategoryRepository(
        @ApplicationContext context: Context,
        remote: SharedListsRemote
    ): CategoryRepository {
        return CategoryRepository(context, remote)
    }

    @Provides
    @Singleton
    fun provideListRepository(
        @ApplicationContext context: Context,
        remote: SharedListsRemote,
        sync: SharedListsSync
    ): ListRepository {
        return ListRepository(context, remote, sync)
    }


    @Provides
    @Singleton
    fun provideNavigation(): NavigationRepository {
        return NavigationRepository()
    }
}
