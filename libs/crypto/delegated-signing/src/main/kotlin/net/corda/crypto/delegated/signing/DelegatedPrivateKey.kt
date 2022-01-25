package net.corda.crypto.delegated.signing

import java.math.BigInteger
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey

internal class DelegatedPrivateKey(
    val publicKey: PublicKey,
    val signer: DelegatedSigner,
) : PrivateKey, RSAPrivateKey {
    companion object {
        private val KEY_SIZE = BigInteger.valueOf(1L).shiftLeft(527)
        private val EXPONENT = BigInteger.valueOf(1L)
    }

    override fun getFormat(): String = publicKey.format
    override fun getAlgorithm(): String = publicKey.algorithm
    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }
    override fun getModulus(): BigInteger = KEY_SIZE
    override fun getPrivateExponent(): BigInteger = EXPONENT
}
