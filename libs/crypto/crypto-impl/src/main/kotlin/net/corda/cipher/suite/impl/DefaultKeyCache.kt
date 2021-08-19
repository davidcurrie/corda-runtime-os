package net.corda.cipher.suite.impl

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

interface DefaultKeyCache {
    fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme)
    fun save(alias: String, key: WrappingKey)
    fun find(alias: String): DefaultCachedKey?
}