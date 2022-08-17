package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey

class ConsensualSignedTransactionImpl(
    val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
    ): ConsensualSignedTransaction
{

    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
    }

    override val id: SecureHash
        get() = wireTransaction.id

    override fun toLedgerTransaction(serializer: SerializationService): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializer)

    /** CORE-5091 it does not do anything at the moment. */
    override fun addSignature(publicKey: PublicKey): ConsensualSignedTransaction {
        return ConsensualSignedTransactionImpl(wireTransaction, signatures)
    }

    override fun getMissingSigningKeys(serializer: SerializationService): Set<PublicKey> {
        val alreadySigned = signatures.map{it.by}.toSet()
        val requiredSigningKeys = this.toLedgerTransaction(serializer).requiredSigningKeys
        return requiredSigningKeys.filter { !it.isFulfilledBy(alreadySigned) }.toSet()
    }
}