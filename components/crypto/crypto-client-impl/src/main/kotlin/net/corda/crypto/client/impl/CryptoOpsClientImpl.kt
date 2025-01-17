package net.corda.crypto.client.impl

import net.corda.crypto.component.impl.retry
import net.corda.crypto.component.impl.toClientException
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.createWireRequestContext
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toWire
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoDerivedSharedSecret
import net.corda.data.crypto.wire.CryptoKeySchemes
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.DeriveSharedSecretCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateWrappingKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.toBase58
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import net.corda.v5.crypto.sha256Bytes
import net.corda.v5.crypto.toStringShort
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Duration
import java.util.UUID

@Suppress("TooManyFunctions")
class CryptoOpsClientImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse>
) {
    companion object {
        private val logger = contextLogger()
    }

    fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        logger.info(
            "Sending '{}'(tenant={},category={})",
            SupportedSchemesRpcQuery::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId = tenantId,
            request = SupportedSchemesRpcQuery(category)
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoKeySchemes::class.java)
        return response!!.codes
    }

    fun filterMyKeys(tenantId: String, candidateKeys: Collection<PublicKey>): Collection<PublicKey> {
        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            candidateKeys.joinToString { it.toStringShort().take(12) + ".." }
        )
        val request = createRequest(
            tenantId = tenantId,
            request = ByIdsRpcQuery(
                candidateKeys.map {
                    publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(it))
                }
            )
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)
        return response!!.keys.map {
            schemeMetadata.decodePublicKey(it.publicKey.array())
        }
    }

    fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys {
        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            candidateKeys.joinToString { it.array().sha256Bytes().toBase58().take(12) + ".." }
        )
        val request = createRequest(
            tenantId = tenantId,
            request = ByIdsRpcQuery(
                candidateKeys.map {
                    publicKeyIdFromBytes(it.array())
                }
            )
        )
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!
    }

    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},category={},alias={})",
            GenerateKeyPairCommand::class.java.simpleName,
            tenantId,
            category,
            alias
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateKeyPairCommand(category, alias, null, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    @Suppress("LongParameterList")
    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},category={},alias={})",
            GenerateKeyPairCommand::class.java.simpleName,
            tenantId,
            category,
            alias
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateKeyPairCommand(category, alias, externalId, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKey(
        tenantId: String,
        category: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(category, null, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},externalId={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId,
            externalId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(category, externalId, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        logger.info(
            "Sending '{}'(tenant={},publicKey={}..,signatureSpec={})",
            SignRpcCommand::class.java.simpleName,
            tenantId,
            publicKey.toStringShort().take(12),
            signatureSpec
        )
        val request = createRequest(
            tenantId,
            SignRpcCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                signatureSpec.toWire(schemeMetadata),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoSignatureWithKey::class.java)
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(response!!.publicKey.array()),
            bytes = response.bytes.array(),
            context = response.context.toMap()
        )
    }

    fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        signatureSpec: CryptoSignatureSpec,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey {
        logger.debug {
            "Sending '${SignRpcCommand::class.java.simpleName}'(tenant=${tenantId}," +
                    "publicKey=${publicKey.array().sha256Bytes().toBase58().take(12)}..)"
        }
        val request = createRequest(
            tenantId,
            SignRpcCommand(
                publicKey,
                signatureSpec,
                data,
                context
            )
        )
        return request.execute(Duration.ofSeconds(20), CryptoSignatureWithKey::class.java)!!
    }

    fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        logger.info(
            "Sending '{}'(hsmId={},failIfExists={},masterKeyAlias={})",
            GenerateWrappingKeyRpcCommand::class.java.simpleName,
            hsmId,
            failIfExists,
            masterKeyAlias
        )
        val request = createRequest(
            CryptoTenants.CRYPTO,
            GenerateWrappingKeyRpcCommand(
                hsmId,
                masterKeyAlias,
                failIfExists,
                context.toWire()
            )
        )
        request.execute(Duration.ofSeconds(20), CryptoNoContentValue::class.java, allowNoContentValue = true)
    }

    fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray {
        logger.info(
            "Sending '{}'(publicKey={},otherPublicKey={})",
            DeriveSharedSecretCommand::class.java.simpleName,
            publicKey.publicKeyId(),
            otherPublicKey.publicKeyId()
        )
        val request = createRequest(
            tenantId,
            DeriveSharedSecretCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(otherPublicKey)),
                context.toWire()
            )
        )
        return request.execute(
            Duration.ofSeconds(20),
            CryptoDerivedSharedSecret::class.java
        )!!.secret.array()
    }

    fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey> {
        logger.debug {
            "Sending '${KeysRpcQuery::class.java.simpleName}'($tenantId, $skip, $take, $orderBy," +
                    " [${filter.map { it }.joinToString { "${it.key}=${it.value}" }}])"
        }
        val request = createRequest(
            tenantId,
            KeysRpcQuery(
                skip,
                take,
                CryptoKeyOrderBy.valueOf(orderBy.name),
                filter.toWire()
            )
        )
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!.keys
    }

    fun lookup(tenantId: String, ids: List<String>): List<CryptoSigningKey> {
        logger.debug {
            "Sending '${ByIdsRpcQuery::class.java.simpleName}'(tenant=$tenantId, ids=[${ids.joinToString()}])"
        }
        require(ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of items exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }
        val request = createRequest(
            tenantId,
            ByIdsRpcQuery(ids)
        )
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!.keys
    }

    private fun createRequest(tenantId: String, request: Any): RpcOpsRequest =
        RpcOpsRequest(
            createWireRequestContext<CryptoOpsClientImpl>(requestId = UUID.randomUUID().toString(), tenantId),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> RpcOpsRequest.execute(
        timeout: Duration,
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false,
        retries: Int = 3
    ): RESPONSE? = try {
        val response = retry(retries, logger) {
            sender.sendRequest(this).getOrThrow(timeout)
        }
        check(
            response.context.requestingComponent == context.requestingComponent &&
                    response.context.tenantId == context.tenantId
        ) {
            "Expected ${context.tenantId} tenant and ${context.requestingComponent} component, but " +
                    "received ${response.response::class.java.name} with ${response.context.tenantId} tenant" +
                    " ${response.context.requestingComponent} component"
        }
        if (response.response::class.java == CryptoNoContentValue::class.java && allowNoContentValue) {
            logger.debug {
                "Received empty response for ${request::class.java.name} for tenant ${context.tenantId}"
            }
            null
        } else {
            check(response.response != null && (response.response::class.java == respClazz)) {
                "Expected ${respClazz.name} for ${context.tenantId} tenant, but " +
                        "received ${response.response::class.java.name} with ${response.context.tenantId} tenant"
            }
            logger.debug {
                "Received response ${respClazz.name} for tenant ${context.tenantId}"
            }
            response.response as RESPONSE
        }
    } catch (e: CordaRPCAPIResponderException) {
        throw e.toClientException()
    } catch (e: Throwable) {
        logger.error("Failed executing ${request::class.java.name} for tenant ${context.tenantId}", e)
        throw e
    }
}