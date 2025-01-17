package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.domino.logic.DominoTileState.Created
import net.corda.lifecycle.domino.logic.DominoTileState.Started
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedByParent
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToBadConfig
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToChildStopped
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToError
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A class encapsulating the domino logic for subscriptions.
 * When [start] is invoked, it will first start all [managedChildren].
 * It will then wait until all [dependentChildren] have fully started and afterwards it will start the subscription.
 * In the event that any of the [dependentChildren] goes down, it will stop the subscription. It will start it again if they all recover.
 * If the subscription goes down ([LifecycleStatus.DOWN] or [LifecycleStatus.ERROR]), it will propagate the error upstream.
 *
 * @param subscriptionGenerator lambda to generate the subscriptions that will be controlled (started or regenerated) by this class.
 * @param subscriptionConfig configuration object for the subscription. Should be the same as the one used inside the subscriptionGenerator
 * lambda.
 * @param dependentChildren the children the subscription will depend on for processing messages (it will be processing messages only if
 * they are all up).
 * @param managedChildren the children that the class will start, when it is started.
 */
abstract class SubscriptionDominoTileBase(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionGenerator: () -> SubscriptionBase,
    private val subscriptionConfig: SubscriptionConfig,
    final override val dependentChildren: Collection<LifecycleCoordinatorName>,
    final override val managedChildren: Collection<NamedLifecycle>
): DominoTile() {

    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
        @VisibleForTesting
        internal const val SUBSCRIPTION = "SUBSCRIPTION"
    }

    final override val coordinatorName: LifecycleCoordinatorName by lazy {
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-${subscriptionConfig.eventTopic}-subscription-tile",
            instancesIndex.compute(this::class.java.simpleName) { _, last ->
                if (last == null) {
                    1
                } else {
                    last + 1
                }
            }.toString()
        )
    }

    override val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

    private val currentState = AtomicReference(Created)

    private val isOpen = AtomicBoolean(true)

    private val internalState: DominoTileState
        get() = currentState.get()
    override val isRunning: Boolean
        get() = internalState == Started

    private val dependentChildrenRegistration = coordinator.followStatusChangesByName(dependentChildren.toSet())
    private var subscriptionRegistration = AtomicReference<RegistrationHandle>(null)

    private val logger = LoggerFactory.getLogger(coordinatorName.toString())

    override fun start() {
        coordinator.start()
    }

    private fun startTile() {
        managedChildren.forEach { it.lifecycle.start() }
        if (dependentChildren.isEmpty()) {
            createAndStartSubscription()
        }
    }

    override fun stop() {
        managedChildren.forEach { it.lifecycle.stop() }
    }

    override fun close() {
        stop()
        isOpen.set(false)
    }

    private fun updateState(newState: DominoTileState) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                Started -> LifecycleStatus.UP
                StoppedDueToBadConfig, StoppedByParent, StoppedDueToChildStopped -> LifecycleStatus.DOWN
                StoppedDueToError -> LifecycleStatus.ERROR
                Created -> null
            }
            status?.let { coordinator.updateStatus(it) }
            logger.info("State updated from $oldState to $newState")
        }
    }

    private fun createAndStartSubscription() {
        subscriptionRegistration.get()?.close()
        subscriptionRegistration.set(null)
        coordinator.createManagedResource(SUBSCRIPTION, subscriptionGenerator)
        val subscriptionName = coordinator.getManagedResource<SubscriptionBase>(SUBSCRIPTION)?.subscriptionName
            ?: throw CordaRuntimeException("Subscription could not be extracted from the lifecycle coordinator.")
        subscriptionRegistration.set(coordinator.followStatusChangesByName(setOf(subscriptionName)))
        coordinator.getManagedResource<SubscriptionBase>(SUBSCRIPTION)?.start()
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            if (!isOpen.get()) {
                return
            }

            handleEvent(event)
        }

        private fun handleEvent(event: LifecycleEvent) {
            when(event) {
                is StartEvent -> {
                    startTile()
                }
                is RegistrationStatusChangeEvent -> {
                    when(event.registration) {
                        subscriptionRegistration.get() -> {
                            when(event.status) {
                                LifecycleStatus.UP -> {
                                    updateState(Started)
                                }
                                LifecycleStatus.DOWN -> {
                                    updateState(StoppedDueToChildStopped)
                                }
                                LifecycleStatus.ERROR -> {
                                    updateState(StoppedDueToError)
                                }
                            }
                        }
                        dependentChildrenRegistration -> {
                            when(event.status) {
                                LifecycleStatus.UP -> {
                                    logger.info("All dependencies are started now, starting subscription.")
                                    createAndStartSubscription()
                                }
                                LifecycleStatus.DOWN -> {
                                    logger.info("One of the dependencies went down, stopping subscription.")
                                    subscriptionRegistration.get()?.close()
                                    subscriptionRegistration.set(null)
                                    updateState(StoppedDueToChildStopped)
                                    coordinator.getManagedResource<Resource>(SUBSCRIPTION)?.close()
                                }
                                LifecycleStatus.ERROR -> {
                                    logger.info("One of the dependencies had an error, stopping subscription.")
                                    subscriptionRegistration.get()?.close()
                                    subscriptionRegistration.set(null)
                                    coordinator.getManagedResource<Resource>(SUBSCRIPTION)?.close()
                                    updateState(StoppedDueToError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
