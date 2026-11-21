package com.eivoid.feature.auth

import com.eivoid.core.common.auth.SignOutHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindSignOutHook(impl: ClerkSignOutHook): SignOutHook
}
