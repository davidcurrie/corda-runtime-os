package net.corda.test.util.dsl.entities.cpx

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import java.util.UUID
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier

fun cpkDbChangeLogAudit(init: CpkDbChangeLogAuditBuilder.() -> Unit): CpkDbChangeLogAudit {
    val builder = CpkDbChangeLogAuditBuilder()
    init(builder)
    return builder.build()
}

class CpkDbChangeLogAuditBuilder(
    private var fileChecksumSupplier: () -> String? = { null },
    private val randomUUID: UUID = UUID.randomUUID()
) {

    private var id: String? = null
    private var filePath: String? = null
    private var content: String? = null
    private var isDeleted: Boolean? = null

    fun id(value: String): CpkDbChangeLogAuditBuilder {
        id = value
        return this
    }

    fun fileChecksum(value: String): CpkDbChangeLogAuditBuilder {
        fileChecksumSupplier = { value }
        return this
    }

    fun filePath(value: String): CpkDbChangeLogAuditBuilder {
        filePath = value
        return this
    }

    fun content(value: String): CpkDbChangeLogAuditBuilder {
        content = value
        return this
    }

    fun build(): CpkDbChangeLogAudit {
        return CpkDbChangeLogAudit(
            id ?: "id_$randomUUID",
            CpkDbChangeLog(
                CpkDbChangeLogIdentifier(
                    fileChecksumSupplier.invoke() ?: "file_checksum_$randomUUID",
                    filePath ?: "file_path_$randomUUID"
                ),
                content ?: "data_$randomUUID"
            )
        )
    }
}
