package net.corda.ledger.common.testkit

import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider

fun getWireTransaction(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService
): WireTransaction{
    val transactionMetaData = TransactionMetaData(
        linkedMapOf(
            TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
        )
    )
    val componentGroupLists = listOf(
        listOf(jsonMarshallingService.format(transactionMetaData).toByteArray(Charsets.UTF_8)), // TODO(update with CORE-5940)
        listOf(".".toByteArray()),
        listOf("abc d efg".toByteArray()),
    )
    return WireTransaction(
        merkleTreeProvider,
        digestService,
        jsonMarshallingService,
        getPrivacySaltImpl(),
        componentGroupLists
    )
}
