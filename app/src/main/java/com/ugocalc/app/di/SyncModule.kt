package com.ugocalc.app.di

import com.ugocalc.app.sync.CloudSyncManager
import com.ugocalc.app.sync.RealCloudSyncManager
import com.ugocalc.app.data.remote.aws.CloudSyncClient
import com.ugocalc.app.data.remote.aws.RealCloudSyncClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindCloudSyncManager(impl: RealCloudSyncManager): CloudSyncManager

    @Binds
    @Singleton
    abstract fun bindCloudSyncClient(impl: RealCloudSyncClient): CloudSyncClient
}
