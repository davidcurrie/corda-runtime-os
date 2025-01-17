package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.fail
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [ VirtualNodeService::class ])
class VirtualNodeService @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    private val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        private fun generateHoldingIdentity() = createTestHoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }
    init {
        // setting cache size to 2 as some tests require 2 concurrent sandboxes for validating they don't overlap
        sandboxGroupContextComponent.initCache(2)
    }

    private val vnodes = mutableMapOf<SandboxGroupContext, VirtualNodeInfo>()

    @Suppress("unused")
    @Deactivate
    fun done() {
        sandboxGroupContextComponent.close()
    }

    private fun getOrCreateSandbox(virtualNodeInfo: VirtualNodeInfo, type: SandboxGroupType): SandboxGroupContext {
        val cpi = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CPI ${virtualNodeInfo.cpiIdentifier} not found")
        val vNodeContext = VirtualNodeContext(
            virtualNodeInfo.holdingIdentity,
            cpi.cpksMetadata.mapTo(LinkedHashSet()) { it.fileChecksum },
            type,
            SingletonSerializeAsToken::class.java,
            null
        )
        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)
        }
    }

    fun loadSandbox(resourceName: String, type: SandboxGroupType): SandboxGroupContext {
        val vnodeInfo = virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
        return getOrCreateSandbox(vnodeInfo, type).also { ctx ->
            vnodes[ctx] = vnodeInfo
        }
    }

    fun unloadSandbox(sandboxGroupContext: SandboxGroupContext) {
        (sandboxGroupContext as? AutoCloseable)?.close()
        vnodes.remove(sandboxGroupContext)?.let(virtualNodeLoader::unloadVirtualNode)
    }

    fun <T : Any> runFlow(className: String, groupContext: SandboxGroupContext): T {
        val workflowClass = groupContext.sandboxGroup.loadClassFromMainBundles(className, Flow::class.java)
        val context = FrameworkUtil.getBundle(workflowClass).bundleContext
        val reference = context.getServiceReferences(SubFlow::class.java, "(component.name=$className)")
            .firstOrNull() ?: fail("No service found for $className.")
        return context.getService(reference)?.let { service ->
            try {
                @Suppress("unchecked_cast")
                service.call() as? T ?: fail("Workflow did not return the correct type.")
            } finally {
                context.ungetService(reference)
            }
        } ?: fail("$className service not available - OSGi error?")
    }
}
