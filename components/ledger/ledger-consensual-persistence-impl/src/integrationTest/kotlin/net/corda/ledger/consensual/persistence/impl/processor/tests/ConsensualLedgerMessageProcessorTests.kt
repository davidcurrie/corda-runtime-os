package net.corda.ledger.consensual.persistence.impl.processor.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.ledger.consensual.FindTransaction
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.data.persistence.EntityResponse
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getSerializer
import net.corda.db.schema.DbSchema
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackgeSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.signatureWithMetaDataExample
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.ledger.consensual.persistence.impl.processor.ConsensualLedgerMessageProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_SERIALIZER
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.InternalCustomSerializer
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.osgi.framework.BundleContext
import org.osgi.service.component.ComponentContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

/**
 * To use Postgres rather than in-memory (HSQL):
 *
 *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
 *
 *     gradlew integrationTest -PpostgresPort=5432
 *
 * Rather than creating a new serializer in these tests from scratch,
 * we grab a reference to the one in the sandbox and use that to serialize and de-serialize.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsensualLedgerMessageProcessorTests {
    companion object {
        const val INTERNAL_CUSTOM_SERIALIZERS = "internalCustomSerializers"
        const val TOPIC = "consensual-ledger-dummy-topic"
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext(
            "request id", "flow id", KeyValuePairList(listOf(KeyValuePair("corda.account", "test account")))
        )
        private val logger = contextLogger()
    }

    @InjectService
    lateinit var lbm: LiquibaseSchemaMigrator

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    // For sandboxing
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNode: VirtualNodeService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var externalEventResponseFactory: ExternalEventResponseFactory
    private lateinit var deserializer: CordaAvroDeserializer<EntityResponse>

    @InjectService
    lateinit var digestService: DigestService
    @InjectService
    lateinit var merkleTreeProvider: MerkleTreeProvider
    @InjectService
    lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var wireTransactionSerializer: InternalCustomSerializer<WireTransaction>
    private lateinit var publicKeySerializer: InternalCustomSerializer<PublicKey>
    private lateinit var ctx: DbTestContext

    @BeforeAll
    fun setup(
        @InjectService(timeout = 5000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            externalEventResponseFactory = setup.fetchService(timeout = 10000)
            cpiInfoReadService = setup.fetchService(timeout = 10000)
            virtualNode = setup.fetchService(timeout = 10000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 10000)
            wireTransactionSerializer = setup.fetchService(
                "(component.name=net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer)",
                10000
            )
            publicKeySerializer = setup.fetchService(
                "(component.name=net.corda.crypto.impl.serialization.PublicKeySerializer)",
                10000
            )
            deserializer = setup.fetchService<CordaAvroSerializationFactory>(timeout = 10000)
                .createAvroDeserializer({}, EntityResponse::class.java)
        }
    }

    @BeforeEach
    fun beforeEach() {
        ctx = createDbTestContext()
    }

    @Test
    fun `persistTransaction for consensual ledger deserialises the transaction and persists`() {
        val transaction = createTestTransaction()

        // Serialise tx into bytebuffer and add to PersistTransaction payload
        val serializedTransaction = ctx.serialize(transaction)
        val transactionStatus = "V"
        val persistTransaction = PersistTransaction(serializedTransaction, transactionStatus)
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, persistTransaction)

        // Send request to message processor
        val processor = ConsensualLedgerMessageProcessor(
            ctx.entitySandboxService,
            externalEventResponseFactory,
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            this::noOpPayloadCheck
        )
        val requestId = UUID.randomUUID().toString()
        val records = listOf(Record(TOPIC, requestId, request))

        // Process the messages (this should persist transaction to the DB)
        var responses = assertSuccessResponses(processor.onNext(records))
        assertThat(responses.size).isEqualTo(1)

        // Check that we wrote the expected things to the DB
        val findRequest = createRequest(ctx.virtualNodeInfo.holdingIdentity, FindTransaction(transaction.id.toHexString()))
        responses = assertSuccessResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), findRequest))))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        val entityResponse = deserializer.deserialize(response.payload.array())!!
        assertThat(entityResponse.results.size).isEqualTo(1)
        assertThat(entityResponse.results.first()).isEqualTo(serializedTransaction)
        val retrievedTransaction = ctx.deserialize<ConsensualSignedTransactionContainer>(serializedTransaction)
        assertThat(retrievedTransaction).isEqualTo(transaction)
    }

    private fun createTestTransaction(): ConsensualSignedTransactionContainer {
        val consensualTransactionMetaDataExample = TransactionMetaData(linkedMapOf(
            TransactionMetaData.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
            TransactionMetaData.LEDGER_VERSION_KEY to "1.0",
            TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetaData.PLATFORM_VERSION_KEY to 123,
            TransactionMetaData.CPI_METADATA_KEY to cpiPackgeSummaryExample,
            TransactionMetaData.CPK_METADATA_KEY to cpkPackageSummaryListExample
        ))
        val wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            consensualTransactionMetaDataExample
        )
        return  ConsensualSignedTransactionContainer(
            wireTransaction,
            listOf(signatureWithMetaDataExample)
        )
    }

    private fun createDbTestContext(): DbTestContext {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val testId = (0..1000000).random()
        val schemaName = "consensual_ledger_test_$testId"
        val dbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "connection-1")
        val dbConnectionManager = FakeDbConnectionManager(listOf(dbConnection), schemaName)

        val componentContext = Mockito.mock(ComponentContext::class.java)
        whenever(componentContext.locateServices(INTERNAL_CUSTOM_SERIALIZERS))
            .thenReturn(arrayOf(wireTransactionSerializer, publicKeySerializer))

        // set up sandbox
        val entitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                componentContext
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        // migrate DB schema
        val vaultSchema = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/vnode-vault/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )

        lbm.updateDb(dbConnectionManager.getDataSource(dbConnection.first).connection, vaultSchema)

        return DbTestContext(
            virtualNodeInfo,
            entitySandboxService,
            sandbox,
            dbConnectionManager.createEntityManagerFactory(
                dbConnection.first,
                JpaEntitiesSet.create(
                    dbConnection.second,
                    setOf()
                )
            ),
            schemaName
        )
    }

    private fun createRequest(
        holdingId: net.corda.virtualnode.HoldingIdentity,
        request: Any,
        externalEventContext: ExternalEventContext = EXTERNAL_EVENT_CONTEXT
    ): ConsensualLedgerRequest {
        logger.info("Consensual ledger persistence request: ${request.javaClass.simpleName} $request")
        return ConsensualLedgerRequest(Instant.now(), holdingId.toAvro(), request, externalEventContext)
    }

    private fun assertSuccessResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error != null) {
                logger.error("Incorrect error response: ${response.error}")
            }
            assertThat(response.error).isNull()
        }
        return records
    }

    /* Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any) =
        ByteBuffer.wrap(getSerializer(SANDBOX_SERIALIZER).serialize(obj).bytes)

    /* Simple wrapper to serialize bytes correctly during test */
    private fun DbTestContext.serialize(obj: Any) = sandbox.serialize(obj)

    /* Simple wrapper to deserialize */
    private inline fun <reified T : Any> SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializer(SANDBOX_SERIALIZER).deserialize<T>(bytes.array())

    /* Simple wrapper to deserialize */
    private inline fun <reified T : Any> DbTestContext.deserialize(bytes: ByteBuffer) = sandbox.deserialize<T>(bytes)

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes
}