package net.corda.p2p.config.publisher

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.ConfigPublisher
import net.corda.libs.configuration.publish.factory.ConfigPublisherFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    subcommands = [
        GatewayConfiguration::class,
        LinkManagerConfiguration::class,
    ],
    description = [
        "Publish configuration to the P2P components"
    ],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
)
internal class CommonArguments(
    private val configPublisherFactory: ConfigPublisherFactory,
) {
    @Option(
        names = ["-h", "--help"],
        usageHelp = true,
        description = ["Display help and exit"]
    )
    var helpRequested = false

    @Option(
        names = ["-k", "--kafka-servers"],
        description = ["The kafka servers"]
    )
    var kafkaServers = System.getenv("KAFKA_SERVERS") ?: "localhost:9092"

    @Option(
        names = ["--topic-prefix"],
        description = ["The topic prefix"]
    )
    var topicPrefix = System.getenv("TOPIC_PREFIX") ?: ""

    private val kafkaNodeConfiguration: Config by lazy {
        ConfigFactory.empty()
            .withValue(
                "messaging.kafka.common.bootstrap.servers",
                ConfigValueFactory.fromAnyRef(kafkaServers)
            )
            .withValue(
                "messaging.topic.prefix",
                ConfigValueFactory.fromAnyRef(topicPrefix)
            )
    }

    fun createPublisher(): ConfigPublisher {
        // TODO - move to common worker and pick up secrets params
        val secretsConfig = ConfigFactory.empty()
        val bootConfig = SmartConfigFactory.create(secretsConfig).create(kafkaNodeConfiguration)

        return configPublisherFactory.createPublisher(
            CONFIG_TOPIC,
            bootConfig
        )
    }
}
