package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.RegistrationStatus
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.*

@Suppress("LongParameterList")
internal class MGMRegistrationMemberInfoHandler(
    private val clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val platformInfoProvider: PlatformInfoProvider,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) {

    private companion object {
        const val SERIAL_CONST = "1"
        val logger = contextLogger()
        val keyIdList = listOf(SESSION_KEY_ID, ECDH_KEY_ID)
    }

    private val keyValuePairListSerializer =
        cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
            logger.error("Failed to serialize key value pair list.")
        }

    @Throws(MGMRegistrationMemberInfoHandlingException::class)
    fun buildAndPersist(
        registrationId: UUID,
        holdingIdentity: HoldingIdentity,
        context: Map<String, String>
    ): MemberInfo {
        return buildMgmInfo(holdingIdentity, context).also {
            persistMemberInfo(holdingIdentity, it)
            persistRegistrationRequest(registrationId, holdingIdentity, it)
        }
    }

    private fun getKeyFromId(keyId: String, tenantId: String): PublicKey {
        return cryptoOpsClient.lookup(
            tenantId,
            listOf(keyId)
        ).firstOrNull()?.let {
            try {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            } catch (ex: RuntimeException) {
                throw MGMRegistrationMemberInfoHandlingException(
                    "Could not decode public key for tenant ID: " +
                            "$tenantId under ID: $keyId.", ex
                )
            }
        } ?: throw MGMRegistrationMemberInfoHandlingException(
            "No key found for tenant: $tenantId under ID: $keyId."
        )
    }

    private fun PublicKey.toPem(): String = keyEncodingService.encodeAsString(this)

    private fun persistMemberInfo(holdingIdentity: HoldingIdentity, mgmInfo: MemberInfo) {
        val persistenceResult = membershipPersistenceClient.persistMemberInfo(holdingIdentity, listOf(mgmInfo))
        if (persistenceResult is MembershipPersistenceResult.Failure) {
            throw MGMRegistrationMemberInfoHandlingException(
                "Registration failed, persistence error. Reason: ${persistenceResult.errorMsg}"
            )
        }
    }

    private fun buildMgmInfo(
        holdingIdentity: HoldingIdentity,
        context: Map<String, String>
    ): MemberInfo {
        val cpi = virtualNodeInfoReadService.get(holdingIdentity)?.cpiIdentifier
            ?: throw MGMRegistrationMemberInfoHandlingException(
                "Could not find virtual node info for member ${holdingIdentity.shortHash}"
            )
        val sessionKey = getKeyFromId(context[SESSION_KEY_ID]!!, holdingIdentity.shortHash.value)
        val ecdhKey = getKeyFromId(context[ECDH_KEY_ID]!!, holdingIdentity.shortHash.value)
        val now = clock.instant().toString()
        val optionalContext = cpi.signerSummaryHash?.let {
            mapOf(MEMBER_CPI_SIGNER_HASH to it.toString())
        } ?: emptyMap()
        val memberContext = context.filterKeys {
            !keyIdList.contains(it)
        }.filterKeys {
            !it.startsWith(GROUP_POLICY_PREFIX_WITH_DOT)
        } + mapOf(
            GROUP_ID to holdingIdentity.groupId,
            PARTY_NAME to holdingIdentity.x500Name.toString(),
            PARTY_SESSION_KEY to sessionKey.toPem(),
            SESSION_KEY_HASH to sessionKey.calculateHash().value,
            ECDH_KEY to ecdhKey.toPem(),
            PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
            SOFTWARE_VERSION to platformInfoProvider.localWorkerSoftwareVersion,
            MEMBER_CPI_NAME to cpi.name,
            MEMBER_CPI_VERSION to cpi.version,
            SERIAL to SERIAL_CONST,
        ) + optionalContext
        return memberInfoFactory.create(
            memberContext = memberContext.toSortedMap(),
            mgmContext = sortedMapOf(
                CREATED_TIME to now,
                MODIFIED_TIME to now,
                STATUS to MEMBER_STATUS_ACTIVE,
                IS_MGM to "true"
            )
        )
    }

    private fun persistRegistrationRequest(
        registrationId: UUID,
        holdingIdentity: HoldingIdentity,
        mgmInfo: MemberInfo
    ) {
        val serializedMemberContext = keyValuePairListSerializer.serialize(
            mgmInfo.memberProvidedContext.toWire()
        ) ?: throw MGMRegistrationMemberInfoHandlingException(
            "Failed to serialize the member context for this request."
        )
        val registrationRequestPersistenceResult = membershipPersistenceClient.persistRegistrationRequest(
            viewOwningIdentity = holdingIdentity,
            registrationRequest = RegistrationRequest(
                status = RegistrationStatus.APPROVED,
                registrationId = registrationId.toString(),
                requester = holdingIdentity,
                memberContext = ByteBuffer.wrap(serializedMemberContext),
                signature = CryptoSignatureWithKey(
                    ByteBuffer.wrap(byteArrayOf()),
                    ByteBuffer.wrap(byteArrayOf()),
                    KeyValuePairList(emptyList())
                )
            )
        )
        if (registrationRequestPersistenceResult is MembershipPersistenceResult.Failure) {
            throw MGMRegistrationMemberInfoHandlingException(
                "Registration failed, persistence error. Reason: ${registrationRequestPersistenceResult.errorMsg}"
            )
        }
    }
}

internal class MGMRegistrationMemberInfoHandlingException(
    val reason: String,
    ex: Throwable? = null
) : CordaRuntimeException(reason, ex)