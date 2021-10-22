package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.Gateway.Companion.CONFIG_KEY
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.v5.base.util.contextLogger

class ReconfigurableConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    val configurationReaderService: ConfigurationReadService,
    listener: HttpEventListener,
    private val managerFactory: (SslConfiguration) -> ConnectionManager = { ConnectionManager(it, listener) }
) : Lifecycle {
    @Volatile
    private var manager: ConnectionManager? = null

    val dominoTile = DominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, configurationChangeHandler = ConnectionManagerConfigChangeHandler())

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    companion object {
        private val logger = contextLogger()
    }

    fun acquire(destinationInfo: DestinationInfo): HttpClient {
        return dominoTile.withLifecycleLock {
            if (manager == null) {
                throw IllegalStateException("Manager is not ready")
            }
            manager!!.acquire(destinationInfo)
        }
    }

    inner class ConnectionManagerConfigChangeHandler : ConfigurationChangeHandler<GatewayConfiguration>(
        configurationReaderService,
        CONFIG_KEY,
        {it.toGatewayConfiguration()}
    ) {
        override fun applyNewConfiguration(
            newConfiguration: GatewayConfiguration,
            oldConfiguration: GatewayConfiguration?,
            resources: ResourcesHolder
        ) {
            if (newConfiguration.sslConfig != oldConfiguration?.sslConfig) {
                logger.info("New SSL configuration, clients for ${this@ReconfigurableConnectionManager::class.java.simpleName} will be" +
                        " reconnected")
                val newManager = managerFactory(newConfiguration.sslConfig)
                resources.keep(newManager)
                val oldManager = manager
                manager = null
                oldManager?.close()
                manager = newManager
            }
        }
    }
    override fun start() {
        if (!isRunning) {
            dominoTile.start()
        }
    }

    override fun stop() {
        if (isRunning) {
            dominoTile.stop()
        }
    }
}
