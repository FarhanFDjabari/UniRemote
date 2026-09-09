package dev.djabari.uniremote.session.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dev.djabari.uniremote.session.DefaultRemoteSession
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.session.TargetRepository
import dev.djabari.uniremote.session.TargetStore
import dev.djabari.uniremote.session.TransportSelector
import dev.djabari.uniremote.transport.RemoteTransport
import dev.djabari.uniremote.transport.bthid.BluetoothHidTransport
import dev.djabari.uniremote.transport.bthid.RealHidDeviceProxy
import dev.djabari.uniremote.transport.network.NetworkTransport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    @Singleton
    abstract fun bindRemoteSession(impl: DefaultRemoteSession): RemoteSession

    @Binds
    @Singleton
    abstract fun bindTargetStore(impl: TargetRepository): TargetStore

    companion object {

        @Provides
        @Singleton
        fun provideCoroutineScope(): kotlinx.coroutines.CoroutineScope =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

        @Provides
        @Singleton
        fun provideRealHidDeviceProxy(): RealHidDeviceProxy = RealHidDeviceProxy()

        @Provides
        @Singleton
        fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        }

        @Provides
        @Singleton
        fun provideBluetoothHidTransport(
            proxy: RealHidDeviceProxy,
            adapter: BluetoothAdapter?,
        ): BluetoothHidTransport = BluetoothHidTransport(proxy, adapter)

        @Provides
        @Singleton
        fun provideTransportSelector(
            btHidTransport: BluetoothHidTransport,
            networkTransport: NetworkTransport,
        ): TransportSelector = TransportSelector(listOf(btHidTransport, networkTransport))
    }
}
