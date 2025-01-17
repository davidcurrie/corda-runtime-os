package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.setup.AddGroup.Companion.toGroupRecord
import net.corda.p2p.setup.AddIdentity.Companion.toIdentityRecord
import net.corda.p2p.setup.AddKeyPair.Companion.toKeysRecord
import net.corda.p2p.setup.AddMember.Companion.toMemberRecord
import net.corda.schema.Schemas.P2P.Companion.GROUP_POLICIES_TOPIC
import net.corda.schema.Schemas.P2P.Companion.MEMBER_INFO_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.configuration.ConfigKeys
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "apply",
    description = ["Apply setup from a file"],
    showAtFileInUsageHelp = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class Apply : Callable<Collection<Record<String, *>>> {
    @Parameters(
        description = [
            "A file with the details to apply."
        ]
    )
    internal lateinit var dataFile: File

    private fun recordsFromConfigurations(
        data: Config,
        name: String,
        handler: (Config) -> Record<String, *>
    ): Collection<Record<String, *>> {
        return try {
            data.getConfigList(name).map {
                handler.invoke(it)
            }
        } catch (_: Missing) {
            emptyList()
        }
    }

    private fun applyOnConfiguration(data: Config): Collection<Record<String, *>> {
        val gatewayConfiguration = try {
            listOf(
                data
                    .getConfig("gatewayConfig")
                    .toConfigurationRecord(ConfigKeys.P2P_GATEWAY_CONFIG)
            )
        } catch (_: Missing) {
            emptyList()
        }

        val linkManagerConfiguration = try {
            listOf(
                data
                    .getConfig("linkManagerConfig")
                    .toConfigurationRecord(ConfigKeys.P2P_GATEWAY_CONFIG)
            )
        } catch (_: Missing) {
            emptyList()
        }

        val groupsToRemove = try {
            data.getStringList("groupsToRemove").map {
                Record(
                    GROUP_POLICIES_TOPIC,
                    it,
                    null
                )
            }
        } catch (_: Missing) {
            emptyList()
        }

        val groupsToAdd = recordsFromConfigurations(
            data,
            "groupsToAdd",
        ) {
            it.toGroupRecord()
        }

        val identitiesToAdd = recordsFromConfigurations(
            data,
            "identitiesToAdd",
        ) {
            it.toIdentityRecord()
        }

        val membersToAdd = recordsFromConfigurations(
            data,
            "membersToAdd",
        ) {
            it.toMemberRecord()
        }

        val keysToAdd = recordsFromConfigurations(
            data,
            "keysToAdd",
        ) {
            it.toKeysRecord()
        }
        val membersToRemove = recordsFromConfigurations(
            data,
            "membersToRemove"
        ) {
            val groupId = it.getString("groupId")
            val x500Name = it.getString("x500Name")
            Record(
                MEMBER_INFO_TOPIC,
                "$x500Name-$groupId",
                null
            )
        }
        val identitiesToRemove = recordsFromConfigurations(
            data,
            "identitiesToRemove"
        ) {
            val groupId = it.getString("groupId")
            val x500Name = it.getString("x500Name")
            Record(
                P2P_HOSTED_IDENTITIES_TOPIC,
                "$x500Name-$groupId",
                null
            )
        }

        return gatewayConfiguration +
            linkManagerConfiguration +
            groupsToAdd +
            groupsToRemove +
            identitiesToAdd +
            identitiesToRemove +
            membersToAdd +
            membersToRemove +
            keysToAdd
    }

    override fun call(): Collection<Record<String, *>> {
        if (!dataFile.canRead()) {
            throw SetupException("Can not read data from $dataFile.")
        }
        val data = ConfigFactory.parseFile(dataFile)
        return applyOnConfiguration(data)
    }
}
