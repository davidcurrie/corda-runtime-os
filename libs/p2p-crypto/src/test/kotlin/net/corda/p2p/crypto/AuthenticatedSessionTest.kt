package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.data.CommonHeader
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.crypto.protocol.api.Mode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.*

class AuthenticatedSessionTest {

    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance("ECDSA", provider)

    private val sessionId = UUID.randomUUID().toString()

    // party A
    private val partyAIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    // party B
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    private val groupId = "some-group-id"

    @Test
    fun `session can be established between two parties and used for transmission of authenticated data successfully`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolB.getSession()

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnA.createMac(payload)
            val initiatorMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnB.validateMac(initiatorMsg.header, initiatorMsg.payload, initiatorMsg.mac)
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnB.createMac(payload)
            val responderMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnA.validateMac(responderMsg.header, responderMsg.payload, responderMsg.mac)
        }
    }

    @Test
    fun `session can be established between two parties and used for transmission of authenticated data successfully with step 2 executed on separate component`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Fronting component of responder sends data downstream so that protocol can be continued.
        val (privateKey, publicKey) = authenticationProtocolB.getDHKeyPair()
        val authenticationProtocolBDownstream = AuthenticationProtocolResponder.fromStep2(sessionId, listOf(Mode.AUTHENTICATION_ONLY), initiatorHelloMsg, responderHelloMsg, privateKey, publicKey)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolBDownstream.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        authenticationProtocolBDownstream.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolBDownstream.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolBDownstream.getSession()

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnA.createMac(payload)
            val initiatorMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnB.validateMac(initiatorMsg.header, initiatorMsg.payload, initiatorMsg.mac)
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnB.createMac(payload)
            val responderMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnA.validateMac(responderMsg.header, responderMsg.payload, responderMsg.mac)
        }
    }

    @Test
    fun `when MAC on data message is altered during transmission, validation fails with an error`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolB.getSession()

        // Data exchange: A sends message to B, B receives corrupted data which fail validation.
        val payload = "ping".toByteArray(Charsets.UTF_8)
        val authenticationResult = authenticatedSessionOnA.createMac(payload)
        val initiatorMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

        assertThatThrownBy { authenticatedSessionOnB.validateMac(initiatorMsg.header, initiatorMsg.payload + "0".toByteArray(Charsets.UTF_8), initiatorMsg.mac) }
            .isInstanceOf(InvalidMac::class.java)
    }

}

data class DataMessage(val header: CommonHeader, val payload: ByteArray, val mac: ByteArray)