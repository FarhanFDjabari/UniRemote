package dev.djabari.uniremote.transport.network.di

import dev.djabari.uniremote.transport.network.BrandAdapter
import dev.djabari.uniremote.transport.network.DataStoreNetworkCredentials
import dev.djabari.uniremote.transport.network.NetworkCredentials
import dev.djabari.uniremote.transport.network.NetworkTransport
import dev.djabari.uniremote.transport.network.adapters.AndroidTvAdapter
import dev.djabari.uniremote.transport.network.adapters.LgWebOsAdapter
import dev.djabari.uniremote.transport.network.adapters.RokuAdapter
import dev.djabari.uniremote.transport.network.adapters.SamsungTizenAdapter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindNetworkCredentials(impl: DataStoreNetworkCredentials): NetworkCredentials

    companion object {

        @Provides
        @Singleton
        fun provideBrandAdapters(credentials: NetworkCredentials): List<@JvmSuppressWildcards BrandAdapter> = listOf(
            RokuAdapter(),
            AndroidTvAdapter(),
            SamsungTizenAdapter(credentials),
            LgWebOsAdapter(credentials),
        )

        @Provides
        @Singleton
        fun provideNetworkTransport(adapters: List<@JvmSuppressWildcards BrandAdapter>): NetworkTransport =
            NetworkTransport(adapters)
    }
}
