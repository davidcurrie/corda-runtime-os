package net.corda.crypto.service.impl.infra

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.v5.base.util.contextLogger

class TestRPCSubscription<REQUEST, RESPONSE>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    override val subscriptionName: LifecycleCoordinatorName =
        LifecycleCoordinatorName("TestRPCSubscription"),
) : RPCSubscription<REQUEST, RESPONSE> {
    companion object {
        private val logger = contextLogger()
    }


    val lifecycleCoordinator = coordinatorFactory.createCoordinator(subscriptionName) { event, coordinator ->
        logger.info("LifecycleEvent received: $event")
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun close() {
        lifecycleCoordinator.close()
    }
}
