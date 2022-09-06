@file:JvmName("KotlinUtilities")
package net.corda.flow.application

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.marshalling.MarshallingService

operator fun FlowContextProperties.set(key: String, value: String) = put(key, value)

inline fun <reified T> RPCRequestData.getRequestBodyAs(marshallingService: MarshallingService) : T {
    return getRequestBodyAs(marshallingService, T::class.java)
}

inline fun <reified T> RPCRequestData.getRequestBodyAsList(marshallingService: MarshallingService) : List<T> {
    return getRequestBodyAsList(marshallingService, T::class.java)
}
