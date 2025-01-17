package net.corda.simulator.runtime.messaging

import net.corda.simulator.exceptions.NoInitiatingFlowAnnotationException
import net.corda.simulator.exceptions.NoRegisteredResponderException
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * FlowMessaging is responsible for sending messages and from other "virtual nodes".
 *
 * ConcurrentFlowMessaging implements a single thread per responder flow (keeping the initiating flow on the calling
 * thread). It creates a pair of sessions for initiator and responder. Each pair of sessions has two shared queues;
 * one for outgoing messages, and one for incoming messages, allowing initiator and responder to be started in any
 * order.
 *
 * Note that the "fiber" must be the same instance for all nodes; it acts as the equivalent of the message bus,
 * allowing nodes to communicate with each other.
 *
 * @initiator The initiating flow
 * @flowClass The class of the initiating flow; used for looking up the protocol
 * @protocolLookUp The "fiber" in which Simulator registered responder flow classes or instances and persistence
 * @injector The injector for @CordaInject flow services
 * @flowFactory The factory which will initialize and inject services into the responder flow.
 */
class ConcurrentFlowMessaging(
    private val initiator: MemberX500Name,
    private val flowClass: Class<out Flow>,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory
) : FlowMessaging {

    override fun initiateFlow(x500Name: MemberX500Name): FlowSession {
        val protocol = flowClass.getAnnotation(InitiatingFlow::class.java)?.protocol
            ?: throw NoInitiatingFlowAnnotationException(flowClass)

        val responderClass = fiber.lookUpResponderClass(x500Name, protocol)
        val responderFlow = if (responderClass == null) {
            fiber.lookUpResponderInstance(x500Name, protocol)
                ?: throw NoRegisteredResponderException(x500Name, protocol)
        } else {
            flowFactory.createResponderFlow(x500Name, responderClass)
        }

        injector.injectServices(responderFlow, x500Name, fiber, flowFactory)

        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val initiatorSession = BlockingQueueFlowSession(
            x500Name,
            fromInitiatorToResponder,
            fromResponderToInitiator
        )
        val recipientSession = BlockingQueueFlowSession(
            initiator,
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        thread { responderFlow.call(recipientSession) }
        return initiatorSession
    }

    override fun initiateFlow(
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder
    ): FlowSession {
        TODO("Not yet implemented")
    }
}
