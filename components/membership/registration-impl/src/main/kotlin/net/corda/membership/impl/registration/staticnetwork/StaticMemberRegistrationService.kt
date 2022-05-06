package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.toWire
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.GroupPolicy
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoImpl
import net.corda.membership.impl.buildMerkleTree
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.KEY_ALIAS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.staticMembers
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.staticMgm
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.ByteBuffer
import java.security.PublicKey

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService @Activate constructor(
    @Reference(service = GroupPolicyProvider::class)
    val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = KeyEncodingService::class)
    val keyEncodingService: KeyEncodingService,
    @Reference(service = CryptoOpsClient::class)
    val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = LayeredPropertyMapFactory::class)
    val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = DigestService::class)
    val digestService: DigestService
) : MemberRegistrationService {
    companion object {
        private val logger: Logger = contextLogger()
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")
    }

    // Handler for lifecycle events
    private val lifecycleHandler = RegistrationServiceLifecycleHandler(this)

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        lifecycleHandler
    )

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("StaticMemberRegistrationService started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("StaticMemberRegistrationService stopped.")
        coordinator.stop()
    }

    override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult {
        if (!isRunning || coordinator.status == LifecycleStatus.DOWN) {
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            )
        }
        try {
            val updates = lifecycleHandler.publisher.publish(parseMemberTemplate(member))
            updates.forEach { it.get() }
        } catch (e: Exception) {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    e.printStackTrace(pw)
                    logger.warn("Registration failed. Reason: $sw")
                }
            }
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: ${e.message}"
            )
        }
        return MembershipRequestRegistrationResult(SUBMITTED)
    }

    /**
     * Parses the static member list template as SignedMemberInfo objects and creates the records for the
     * kafka publisher.
     */
    private fun parseMemberTemplate(registeringMember: HoldingIdentity): List<Record<String, PersistentMemberInfo>> {
        val members = mutableListOf<Record<String, PersistentMemberInfo>>()

        val policy = try {
            groupPolicyProvider.getGroupPolicy(registeringMember)
        } catch (e: BadGroupPolicyException) {
            logger.error("Creating empty member list since group policy file could not be found for holding identity.")
            return emptyList()
        }
        val groupId = policy.groupId

        val staticMemberList = policy.staticMembers
        require(staticMemberList.isNotEmpty()) { "Static member list inside the group policy file cannot be empty." }

        val processedMembers = mutableListOf<String>()
        @Suppress("SpreadOperator")
        staticMemberList.forEach { staticMember ->
            isValidStaticMemberDeclaration(processedMembers, staticMember)
            val memberName = MemberX500Name.parse(staticMember.name!!).toString()
            val memberId = HoldingIdentity(memberName, groupId).id
            val memberKey = getIdentityKey(staticMember, memberId)
            val encodedMemberKey = keyEncodingService.encodeAsString(memberKey)
            val mgmKey = getMgmIdentityKey(memberId, policy)
            val memberInfo = MemberInfoImpl(
                memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
                    sortedMapOf(
                        PARTY_NAME to memberName,
                        PARTY_OWNING_KEY to encodedMemberKey,
                        GROUP_ID to groupId,
                        *generateIdentityKeys(encodedMemberKey).toTypedArray(),
                        *generateIdentityKeyHashes(memberKey).toTypedArray(),
                        *convertEndpoints(staticMember).toTypedArray(),
                        SOFTWARE_VERSION to staticMember.softwareVersion,
                        PLATFORM_VERSION to staticMember.platformVersion,
                        SERIAL to staticMember.serial,
                    )
                ),
                mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
                    sortedMapOf(
                        STATUS to staticMember.status,
                        MODIFIED_TIME to staticMember.modifiedTime,
                    )
                )
            )
            val signedMemberInfo = signMemberInfo(memberId, memberInfo, memberKey, mgmKey)
            members.add(
                Record(
                    MEMBER_LIST_TOPIC,
                    registeringMember.id + "-" + memberId,
                    PersistentMemberInfo(registeringMember.toAvro(), signedMemberInfo)
                )
            )
            processedMembers.add(memberName)
        }
        return members
    }

    private fun isValidStaticMemberDeclaration(processedMembers: List<String>, member: StaticMember) {
        val memberName = member.name
        require(!memberName.isNullOrBlank()) { "Member's name is not provided." }
        require(!processedMembers.contains(
            MemberX500Name.parse(memberName).toString()
        )) { "Duplicated static member declaration." }
        require(
            member.keys.any { it.startsWith(endpointUrlIdentifier) }
        ) { "Endpoint urls are not provided." }
        require(
            member.keys.any { it.startsWith(endpointProtocolIdentifier) }
        ) { "Endpoint protocols are not provided." }
    }

    /**
     * If the keyAlias is not defined in the static template, we are going to use the id of the HoldingIdentity as default.
     */
    private fun getIdentityKey(
        member: StaticMember,
        memberId: String
    ): PublicKey {
        var keyAlias = member.keyAlias
        if (keyAlias.isNullOrBlank()) {
            keyAlias = memberId
        }
        return getOrGenerateKeyPair(memberId, keyAlias)
    }

    /**
     * Generates the MGM's key used for signing the MemberInfo from the static template.
     */
    private fun getMgmIdentityKey(
        memberId: String,
        policy: GroupPolicy
    ): PublicKey {
        val staticMgm = policy.staticMgm
        require(staticMgm.isNotEmpty()) { "Static mgm inside the group policy file should be defined." }

        val keyAlias: String? = staticMgm[KEY_ALIAS]
        require(!keyAlias.isNullOrBlank()) { "MGM's key alias is not provided." }

        return getOrGenerateKeyPair(memberId, keyAlias)
    }

    private fun getOrGenerateKeyPair(tenantId: String, keyAlias: String): PublicKey {
        return with(cryptoOpsClient) {
            lookup(
                tenantId = tenantId,
                skip = 0,
                take = 10,
                orderBy = CryptoKeyOrderBy.NONE,
                filter = mapOf(
                    ALIAS_FILTER to keyAlias,
                )
            ).firstOrNull()?.let {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            } ?: generateKeyPair(
                tenantId = tenantId,
                category = CryptoConsts.HsmCategories.LEDGER,
                alias = keyAlias
            )
        }
    }


    /**
     * Mapping the keys from the json format to the keys expected in the [MemberInfo].
     */
    private fun convertEndpoints(member: StaticMember): List<Pair<String, String>> {
        val endpoints = mutableListOf<EndpointInfo>()
        member.keys.filter { it.startsWith(endpointUrlIdentifier) }.size.apply {
            for (index in 1..this) {
                endpoints.add(member.getEndpoint(index))
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    /**
     * Only going to contain the owningKey for passing the checks on the MemberInfo creation side.
     * For the static network we don't need the rotated keys.
     */
    private fun generateIdentityKeys(
        owningKey: String
    ): List<Pair<String, String>> {
        val identityKeys = listOf(owningKey)
        return identityKeys.mapIndexed { index, identityKey ->
            String.format(
                MemberInfoExtension.IDENTITY_KEYS_KEY,
                index
            ) to identityKey
        }
    }

    /**
     * Only going to contain hash of the owningKey for passing the checks on the MemberInfo creation side.
     * For the static network we don't need hashes of the rotated keys.
     */
    private fun generateIdentityKeyHashes(
        owningKey: PublicKey
    ): List<Pair<String, String>> {
        val identityKeys = listOf(owningKey)
        return identityKeys.mapIndexed { index, identityKey ->
            val hash = identityKey.calculateHash()
            String.format(
                MemberInfoExtension.IDENTITY_KEY_HASHES_KEY,
                index
            ) to hash.toString()
        }
    }

    /**
     * Creates and returns a [SignedMemberInfo] object.
     */
    private fun signMemberInfo(
        memberId: String,
        memberInfo: MemberInfo,
        memberKey: PublicKey,
        mgmKey: PublicKey,
    ): SignedMemberInfo {
        val memberContextBF = memberInfo.memberProvidedContext.toWire()
        val mgmContextBF = memberInfo.mgmProvidedContext.toWire()
        return SignedMemberInfo(
            memberContextBF,
            mgmContextBF,
            memberContextBF
                .array()
                .toCryptoSignatureWithKey(memberId, memberKey),
            buildMerkleTree(
                mgmContextBF,
                memberContextBF,
                digestService
            ).hash.bytes.toCryptoSignatureWithKey(memberId, mgmKey)
        )
    }

    private fun ByteArray.toByteBuffer(): ByteBuffer = ByteBuffer.wrap(this)
    private fun ByteArray.toCryptoSignatureWithKey(
        memberId: String,
        signingKey: PublicKey
    ) = CryptoSignatureWithKey(
        keyEncodingService.encodeAsByteArray(signingKey).toByteBuffer(),
        cryptoOpsClient.sign(memberId, signingKey, this).bytes.toByteBuffer()
    )
}