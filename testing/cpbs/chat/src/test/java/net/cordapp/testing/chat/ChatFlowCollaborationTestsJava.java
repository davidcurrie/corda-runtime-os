package net.cordapp.testing.chat;

import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.messaging.FlowMessaging;
import net.corda.v5.application.persistence.PersistenceService;
import net.corda.v5.base.types.MemberX500Name;
import net.cordapp.testing.chatframework.FlowMockHelper;
import net.cordapp.testing.chatframework.FlowMockMessageLink;
import net.cordapp.testing.chatframework.InjectableMockServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.stubbing.Answer;

import static net.cordapp.testing.chat.FlowTestUtilsKt.executeConcurrently;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChatFlowCollaborationTestsJava {
    static final String RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB";
    static final String FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB";
    static final String MESSAGE = "chat message";

    static final String DUMMY_FLOW_RETURN = "dummy_flow_return";

    static final FlowMockHelper outgoingFlowMockHelper = FlowMockHelper.fromInjectableServices(
            new InjectableMockServices()
                    .createMockService(FlowMessaging.class)
                    .createMockService(JsonMarshallingService.class)
                    .createMockService(FlowEngine.class)
                    .createMockService(PersistenceService.class));
    static final FlowMockHelper incomingFlowMockHelper = FlowMockHelper.fromInjectableServices(
            new InjectableMockServices()
                    .createMockService(FlowEngine.class)
                    .createMockService(PersistenceService.class));

    static final FlowMockHelper readerFlowMockHelper = FlowMockHelper.fromInjectableServices(
            new InjectableMockServices()
                    .createMockService(FlowEngine.class)
                    .createMockService(JsonMarshallingService.class)
                    .createMockService(PersistenceService.class));

    static final ChatOutgoingFlow outgoingChatFlow = outgoingFlowMockHelper.createFlow(ChatOutgoingFlow.class);
    static final ChatIncomingFlow incomingChatFlow = incomingFlowMockHelper.createFlow(ChatIncomingFlow.class);
    static final ChatReaderFlow readerChatFlow = readerFlowMockHelper.createFlow(ChatReaderFlow.class);

    @BeforeAll
    void setup() {
        FlowEngine outgoingMock = (FlowEngine) outgoingFlowMockHelper.getMockService(FlowEngine.class);
        when(outgoingMock.getVirtualNodeName()).thenReturn(MemberX500Name.parse(FROM_X500_NAME));

        FlowEngine incomingMock = (FlowEngine) incomingFlowMockHelper.getMockService(FlowEngine.class);
        when(incomingMock.getVirtualNodeName()).thenReturn(MemberX500Name.parse(RECIPIENT_X500_NAME));
    }

    @Test
    @Disabled("Disabled due to FlowMockHelper needing additional support for the PersistenceService.")
    void FlowSendsMessage() {
        FlowMockMessageLink messageLink = new FlowMockMessageLink(outgoingFlowMockHelper, incomingFlowMockHelper);

        // When receive is called in the 'to' flow, wait on/return the next message in the queue
        when(messageLink.getToFlowSession().receive(MessageContainer.class)).thenAnswer(
                (Answer<MessageContainer>) invocation ->
                        (MessageContainer)messageLink.messageQueue.getOrWaitForNextMessage()
        );

        RPCRequestData outGoingFlowRequestData = mock(RPCRequestData.class);
        when(outGoingFlowRequestData.getRequestBodyAs(
                (JsonMarshallingService) outgoingFlowMockHelper.getMockService(JsonMarshallingService.class),
                ChatOutgoingFlowParameter.class)
        ).thenReturn(new ChatOutgoingFlowParameter(RECIPIENT_X500_NAME, MESSAGE));

        executeConcurrently(() -> {
                    outgoingChatFlow.call(outGoingFlowRequestData);
                },
                () -> {
                    incomingChatFlow.call(messageLink.toFlowSession);
                });

        messageLink.failIfPendingMessages();

        // TODO verify any output

        RPCRequestData readerFlowRequestData = mock(RPCRequestData.class);
        String messagesJson = readerChatFlow.call(readerFlowRequestData);

        // TODO verify read messages
    }
}
