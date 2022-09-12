package net.corda.ledger.common.impl.transaction.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*
import de.javakaffee.kryoserializers.ArraysAsListSerializer

class WireTransactionKryoSerializerTest {
    companion object {
        private val schemeMetadata = CipherSchemeMetadataImpl()

        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
        }
    }

    @Test
    fun `serialization of a Wire Tx object using the kryo default serialization`() {
        val mapper = jacksonObjectMapper()
        val transactionMetaData = TransactionMetaData(
            mapOf(
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
            )
        )
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf(mapper.writeValueAsBytes(transactionMetaData)), // CORE-5940
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val wireTransaction = WireTransaction(
            merkleTreeFactory,
            digestService,
            privacySalt,
            componentGroupLists
        )

        val wireTransactionKryoSerializer = WireTransactionKryoSerializer(merkleTreeFactory, digestService)

        val kryo = Kryo()
        kryo.addDefaultSerializer(PrivacySaltImpl::class.java, PrivacySaltImplKryoSerializer())
        kryo.addDefaultSerializer(Arrays.asList("").javaClass, ArraysAsListSerializer())
        val output = Output(5000)
        wireTransactionKryoSerializer.write(kryo, output, wireTransaction)
        val deserialized = wireTransactionKryoSerializer.read(kryo, Input(output.buffer), WireTransaction::class.java)

        assertThat(deserialized).isEqualTo(wireTransaction)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow{
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}