package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.consensual.flow.impl.persistence.TransactionStatus
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransactionVerifier
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
class ConsensualReceiveFinalityFlowTest {

    private companion object {
        val MEMBER = MemberX500Name("Alice", "London", "GB")
        val ID = SecureHash("algo", byteArrayOf(1, 2, 3))
    }

    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<ConsensualLedgerPersistenceService>()
    private val serializationService = mock<SerializationService>()

    private val session = mock<FlowSession>()

    private val memberInfo = mock<MemberInfo>()

    private val publicKey1 = mock<PublicKey>()
    private val publicKey2 = mock<PublicKey>()

    private val signature1 = digitalSignatureAndMetadata(publicKey1)
    private val signature2 = digitalSignatureAndMetadata(publicKey2)

    private val signedTransaction = mock<ConsensualSignedTransaction>()

    @BeforeEach
    fun beforeEach() {
        whenever(session.counterparty).thenReturn(MEMBER)
        whenever(session.receive(ConsensualSignedTransaction::class.java)).thenReturn(signedTransaction)
        whenever(session.receive(Unit::class.java)).thenReturn(Unit)

        whenever(memberLookup.myInfo()).thenReturn(memberInfo)

        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKey1, publicKey2))

        whenever(signedTransaction.id).thenReturn(ID)
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2))
        whenever(signedTransaction.addSignature(publicKey1)).thenReturn(signedTransaction to signature1)
        whenever(signedTransaction.addSignature(publicKey2)).thenReturn(signedTransaction to signature2)

        whenever(serializationService.serialize(any())).thenReturn(SerializedBytes(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `receiving a transaction that passes verification is signed and recorded`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))

        callReceiveFinalityFlow()

        verify(signedTransaction).addSignature(publicKey1)
        verify(signedTransaction).addSignature(publicKey2)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
        verify(session).send(Unit)
    }

    @Test
    fun `the received transaction is only signed with ledger keys in the transaction's missing signatories`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, mock()))

        callReceiveFinalityFlow()

        verify(signedTransaction).addSignature(publicKey1)
        verify(signedTransaction, never()).addSignature(publicKey2)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1)))
        verify(session).send(Unit)
    }

    @Test
    fun `receiving a transaction that fails verification with an IllegalArgumentException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalArgumentException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction verification failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction that fails verification with an IllegalStateException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalStateException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction verification failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction that fails verification with a CordaRuntimeException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw CordaRuntimeException("") } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction verification failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction that throws an unexpected exception during verification throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw FileNotFoundException("message!") } }
            .isInstanceOf(FileNotFoundException::class.java)
            .hasMessage("message!")

        verify(session, never()).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `the received transaction is not signed if the member does not have any ledger keys`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(memberInfo.ledgerKeys).thenReturn(emptyList())

        // [ConsensualFinalityFlow] will return a session error to this flow if no keys are sent to it. Failing on this receive mimics the
        // real behaviour of the flows.
        var called = false
        whenever(session.receive(ConsensualSignedTransaction::class.java)).then {
            if (!called) {
                called = true
                signedTransaction
            } else {
                throw CordaRuntimeException("session error")
            }
        }

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(signedTransaction, never()).addSignature(any<PublicKey>())
        verify(session).send(Payload.Success(emptyList<DigitalSignatureAndMetadata>()))
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a different transaction to record compared to the one that was signed throws an exception`() {
        val wrongId = SecureHash("wrong", byteArrayOf(1, 2, 3))
        val wrongSignedTransaction = mock<ConsensualSignedTransaction>()

        whenever(wrongSignedTransaction.id).thenReturn(wrongId)
        whenever(session.receive(ConsensualSignedTransaction::class.java)).thenReturn(signedTransaction, wrongSignedTransaction)

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Expected to received transaction $ID from $MEMBER to finalise but received $wrongId instead")

        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction to record that is not fully signed throws an exception`() {
        whenever(signedTransaction.verifySignatures()).thenThrow(TransactionVerificationException(ID, "There are missing signatures", null))

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("There are missing signatures")

        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    private fun callReceiveFinalityFlow(verifier: ConsensualSignedTransactionVerifier = ConsensualSignedTransactionVerifier { }) {
        val flow = ConsensualReceiveFinalityFlow(session, verifier)
        flow.memberLookup = memberLookup
        flow.persistenceService = persistenceService
        flow.serializationService = serializationService
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKey(publicKey, byteArrayOf(1, 2, 3), emptyMap()),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )
    }
}