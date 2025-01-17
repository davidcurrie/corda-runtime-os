package net.corda.membership.httprpc.v1

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(
    name = "Certificates API",
    description = "Certificates management endpoints.",
    path = "certificates"
)
interface CertificatesRpcOps : RpcOps {
    companion object {
        const val SIGNATURE_SPEC = "signatureSpec"
    }

    /**
     * PUT endpoint which import certificate chain.
     *
     * @param tenantId The tenant ID.
     * @param alias The certificate alias.
     * @param certificates - The certificate chain (in PEM format)
     */
    @HttpRpcPUT(
        path = "{tenantId}",
        description = "Import certificate."
    )
    fun importCertificateChain(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcRequestBodyParameter(
            description = "The certificate alias.",
            required = true,
        )
        alias: String,
        @HttpRpcRequestBodyParameter(
            description = "The certificate chain (in PEM format)",
            required = true,
            name = "certificate"
        )
        certificates: List<HttpFileUpload>,
    )

    /**
     * POST endpoint which Generate a certificate signing request (CSR) for a holding identity.
     *
     * @param tenantId The tenant ID.
     * @param keyId The Key ID.
     * @param x500Name A valid X500 name.
     * @param subjectAlternativeNames - list of subject alternative DNS names
     * @param contextMap - Any additional attributes to add to the CSR.
     *
     * @return The CSR in PEM format.
     */
    @Suppress("LongParameterList")
    @HttpRpcPOST(
        path = "{tenantId}/{keyId}",
        description = "Generate certificate signing request (CSR)."
    )
    fun generateCsr(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "The Key ID.")
        keyId: String,
        @HttpRpcRequestBodyParameter(
            description = "The X500 name",
            required = true,
        )
        x500Name: String,
        @HttpRpcRequestBodyParameter(
            description = "Subject alternative names",
            required = false,
        )
        subjectAlternativeNames: List<String>?,
        @HttpRpcRequestBodyParameter(
            description = "Context Map. For example: `$SIGNATURE_SPEC` to signature spec (SHA512withECDSA, SHA384withRSA...)",
            required = false,
        )
        contextMap: Map<String, String?>?,
    ): String
}
