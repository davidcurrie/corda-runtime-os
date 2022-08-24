package net.corda.testutils

import net.corda.testutils.tools.RPCRequestDataWrapper
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

interface SimVirtualNode {
    val holdingIdentity: HoldingIdentity
    val member : MemberX500Name

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @input the data to input to the flow
     *
     * @return the response from the flow
     */
    fun callFlow(input: RPCRequestDataWrapper): String

    /**
     * Retrieves the persistence service associated with this node's member
     */
    fun getPersistenceService(): PersistenceService
}