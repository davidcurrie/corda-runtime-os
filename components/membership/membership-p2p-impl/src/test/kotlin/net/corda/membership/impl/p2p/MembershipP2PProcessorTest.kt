package net.corda.membership.impl.p2p

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.sync.BloomFilter
import net.corda.membership.impl.p2p.MembershipP2PProcessor.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.Schemas.Membership.Companion.SYNCHRONISATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class MembershipP2PProcessorTest {

    private companion object {
        const val TOPIC = "foo"
        const val KEY = "bar"

        val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private fun String.toByteBuffer() = ByteBuffer.wrap(toByteArray())
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()

    private val memberContext = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
    private val testSig =
        CryptoSignatureWithKey("ABC".toByteBuffer(), "DEF".toByteBuffer(), KeyValuePairList(emptyList()))
    private val registrationId = UUID.randomUUID().toString()
    private val registrationRequest = MembershipRegistrationRequest(
        registrationId,
        memberContext,
        testSig
    )
    private val registrationReqMsgPayload = registrationRequest.toByteBuffer()

    private val groupId = "1f5e558c-dd87-438f-a57f-21e69c1e0b88"
    private val member = HoldingIdentity("C=GB, L=London, O=Alice", groupId)
    private val mgm = HoldingIdentity("C=GB, L=London, O=MGM", groupId)

    private val verificationRequest = VerificationRequest(
        registrationId,
        KeyValuePairList(listOf(KeyValuePair("A", "B")))
    )

    private val verificationReqMsgPayload = verificationRequest.toByteBuffer()

    private val verificationResponse = VerificationResponse(
        registrationId,
        KeyValuePairList(listOf(KeyValuePair("A", "B")))
    )

    private val verificationRespMsgPayload = verificationResponse.toByteBuffer()

    private val byteBuffer = "1234".toByteBuffer()
    private val secureHash = SecureHash("algorithm", byteBuffer)
    private val syncId = UUID(1, 2).toString()
    private val syncRequest = MembershipSyncRequest(
        DistributionMetaData(
            syncId,
            clock.instant()
        ),
        secureHash, BloomFilter(1, 1, 1, byteBuffer), secureHash, secureHash
    )
    private val syncRequestMsgPayload = syncRequest.toByteBuffer()

    private lateinit var membershipP2PProcessor: MembershipP2PProcessor

    @BeforeEach
    fun setUp() {
        membershipP2PProcessor = MembershipP2PProcessor(avroSchemaRegistry)
    }

    @Test
    fun `empty input results in empty output`() {
        val result = membershipP2PProcessor.onNext(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `null value in input record results in empty output`() {
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, null)))
        assertThat(result).isEmpty()
    }

    @Test
    fun `Registration request as unauthenticated message is processed as expected`() {
        val appMessage = with(registrationReqMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        with(result) {
            assertSoftly {
                it.assertThat(this)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(this.first().topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                it.assertThat(this.first().value).isInstanceOf(RegistrationCommand::class.java)
                it.assertThat(this.first().key).isEqualTo("$registrationId-${mgm.toCorda().shortHash}")

                val value = this.first().value as RegistrationCommand
                it.assertThat(value.command).isInstanceOf(StartRegistration::class.java)
                val command = value.command as StartRegistration
                it.assertThat(command.destination).isEqualTo(mgm)
                it.assertThat(command.source).isEqualTo(member)
                it.assertThat(command.memberRegistrationRequest).isEqualTo(registrationRequest)
            }
        }
    }

    @Test
    fun `Registration request on a non-membership subsystem returns no output records`() {
        val appMessage = with(registrationReqMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload(mgm, member, "BAD_SUBSYSTEM")
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `Registration request as authenticated message throws exception`() {
        val appMessage = with(registrationReqMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload()
        }
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))
        }

    }

    @Test
    fun `Message payload with no handler returns no output records`() {
        val appMessage = with("badPayload".toByteBuffer()) {
            whenever(avroSchemaRegistry.getClassType(eq(this))).thenReturn(String::class.java)
            asUnauthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `Verification request as authenticated message is processed as expected`() {
        val appMessage = with(verificationReqMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload(member, mgm)
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        with(result) {
            assertSoftly {
                it.assertThat(this)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(this.first().topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val command = this.first().value as? RegistrationCommand
                it.assertThat(command?.command).isInstanceOf(ProcessMemberVerificationRequest::class.java)
                it.assertThat(this.first().key).isEqualTo("$registrationId-${member.toCorda().shortHash}")
                val request = command?.command as ProcessMemberVerificationRequest
                it.assertThat(request.verificationRequest).isEqualTo(verificationRequest)
                it.assertThat(request.destination).isEqualTo(member)
                it.assertThat(request.source).isEqualTo(mgm)
            }
        }
    }

    @Test
    fun `Verification request as unauthenticated message throws exception`() {
        val appMessage = with(verificationReqMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload(member, mgm)
        }
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))
        }
    }

    @Test
    fun `Verification response as authenticated message is processed as expected`() {
        val appMessage = with(verificationRespMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        with(result) {
            assertSoftly {
                it.assertThat(this)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(this.first().topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val command = this.first().value as? RegistrationCommand
                it.assertThat(command?.command).isInstanceOf(ProcessMemberVerificationResponse::class.java)
                it.assertThat(this.first().key).isEqualTo("$registrationId-${mgm.toCorda().shortHash}")
                val response = command?.command as ProcessMemberVerificationResponse
                it.assertThat(response.verificationResponse).isEqualTo(verificationResponse)
            }
        }
    }

    @Test
    fun `Verification response as unauthenticated message throws exception`() {
        val appMessage = with(verificationRespMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload()
        }
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))
        }
    }

    @Test
    fun `Sync request as authenticated message is processed as expected`() {
        val appMessage = with(syncRequestMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        with(result) {
            assertSoftly {
                it.assertThat(this)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(this.first().topic).isEqualTo(SYNCHRONISATION_TOPIC)
                it.assertThat(this.first().value).isInstanceOf(SynchronisationCommand::class.java)
                val command = this.first().value as SynchronisationCommand
                it.assertThat(command.command).isInstanceOf(ProcessSyncRequest::class.java)
                val request = command.command as ProcessSyncRequest
                it.assertThat(request.synchronisationMetaData.mgm).isEqualTo(mgm)
                it.assertThat(request.synchronisationMetaData.member).isEqualTo(member)
                it.assertThat(request.syncRequest).isEqualTo(syncRequest)
                it.assertThat(this.first().key).isEqualTo("$syncId-${member.toCorda().shortHash}")
            }
        }
    }

    @Test
    fun `Sync request as unauthenticated message throws exception`() {
        val appMessage = with(syncRequestMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload()
        }
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))
        }
    }

    private fun ByteBuffer.asUnauthenticatedAppMessagePayload(
        destination: HoldingIdentity = mgm,
        source: HoldingIdentity = member,
        subsystem: String = MEMBERSHIP_P2P_SUBSYSTEM
    ): AppMessage {
        return AppMessage(
            UnauthenticatedMessage(
                UnauthenticatedMessageHeader(
                    destination, source, subsystem
                ),
                this
            )
        )
    }

    private fun ByteBuffer.asAuthenticatedAppMessagePayload(
        destination: HoldingIdentity = mgm,
        source: HoldingIdentity = member
    ): AppMessage {
        return AppMessage(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    destination,
                    source,
                    clock.instant().plusMillis(300000L),
                    "mid",
                    null,
                    MEMBERSHIP_P2P_SUBSYSTEM
                ),
                this
            )
        )
    }

    private fun mockPayloadDeserialization() {
        whenever(avroSchemaRegistry.getClassType(eq(registrationReqMsgPayload))).thenReturn(MembershipRegistrationRequest::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(registrationReqMsgPayload),
                eq(MembershipRegistrationRequest::class.java),
                eq(null)
            )
        ).thenReturn(registrationRequest)
        whenever(avroSchemaRegistry.getClassType(eq(verificationReqMsgPayload))).thenReturn(VerificationRequest::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(verificationReqMsgPayload),
                eq(VerificationRequest::class.java),
                eq(null)
            )
        ).thenReturn(verificationRequest)
        whenever(avroSchemaRegistry.getClassType(eq(verificationRespMsgPayload))).thenReturn(VerificationResponse::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(verificationRespMsgPayload),
                eq(VerificationResponse::class.java),
                eq(null)
            )
        ).thenReturn(verificationResponse)
        whenever(avroSchemaRegistry.getClassType(eq(syncRequestMsgPayload))).thenReturn(MembershipSyncRequest::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(syncRequestMsgPayload),
                eq(MembershipSyncRequest::class.java),
                eq(null)
            )
        ).thenReturn(syncRequest)
    }
}
