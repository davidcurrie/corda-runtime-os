package net.corda.application.impl.flow.session

import net.corda.application.internal.flow.session.FlowSessionFactory
import net.corda.flow.manager.fiber.FlowFiberService
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowSessionFactory::class])
class FlowSessionFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowSessionFactory {

    override fun create(sessionId: String, x500Name: MemberX500Name, initiated: Boolean): FlowSession {
        return FlowSessionImpl(
            counterparty = x500Name,
            sourceSessionId = sessionId,
            flowFiberService = flowFiberService,
            state = if (initiated) FlowSessionImpl.State.INITIATED else FlowSessionImpl.State.UNINITIATED
        )
    }
}