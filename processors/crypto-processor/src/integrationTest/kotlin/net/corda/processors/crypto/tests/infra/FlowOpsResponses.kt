package net.corda.processors.crypto.tests.infra

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.CordaAvroDeserializer
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions

class FlowOpsResponses(
    messagingConfig: SmartConfig,
    subscriptionFactory: SubscriptionFactory,
    private val deserializer: CordaAvroDeserializer<FlowOpsResponse>
) : DurableProcessor<String, FlowEvent>, AutoCloseable {

    private val subscription: Subscription<String, FlowEvent> =
        subscriptionFactory.createDurableSubscription(
            subscriptionConfig = SubscriptionConfig(
                groupName = "TEST",
                eventTopic = Schemas.Flow.FLOW_EVENT_TOPIC
            ),
            processor = this,
            messagingConfig = messagingConfig,
            partitionAssignmentListener = null
        ).also { it.start() }

    private val receivedEvents = ConcurrentHashMap<String, FlowOpsResponse?>()

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<FlowEvent> = FlowEvent::class.java

    override fun onNext(events: List<Record<String, FlowEvent>>): List<Record<*, *>> {
        events.forEach {
            val response = ((it.value as FlowEvent).payload as ExternalEventResponse)
            val flowOpsResponse = deserializer.deserialize(response.payload.array())
            receivedEvents[it.key] = flowOpsResponse
        }
        return emptyList()
    }

    fun waitForResponse(key: String): FlowOpsResponse =
        eventually(duration = Duration.ofSeconds(20)) {
            val event = receivedEvents[key]
            Assertions.assertNotNull(event)
            event!!
        }

    override fun close() {
        subscription.close()
    }
}