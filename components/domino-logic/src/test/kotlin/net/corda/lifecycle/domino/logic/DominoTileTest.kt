package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class DominoTileTest {

    companion object {
        private const val TILE_NAME = "MyTestTile"
    }

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    @Nested
    inner class SimpleLeafTileTests {

        private fun tile(): DominoTile {
            return DominoTile(TILE_NAME, factory)
        }

        @Test
        fun `start will update the status to started`() {
            val tile = tile()

            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `start will start the coordinator if not started`() {
            val tile = tile()

            tile.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop will update the status to stopped`() {
            val tile = tile()
            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
        }

        @Test
        fun `stop will update the status the tile is started`() {
            val tile = tile()
            tile.start()

            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
        }

        @Test
        fun `isRunning will return true if state is started`() {
            val tile = tile()
            tile.start()

            assertThat(tile.isRunning).isTrue
        }

        @Test
        fun `stop will update the coordinator state from up to down`() {
            val tile = tile()
            tile.start()

            tile.stop()

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `processEvent will set as error if the event is error`() {
            val tile = tile()
            tile.start()

            handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `handleEvent can handle unknown event`() {
            val tile = tile()
            val event = RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.UP
            )
            tile.start()

            assertDoesNotThrow {
                handler.lastValue.processEvent(event, coordinator)
            }
        }

        @Test
        fun `processEvent will ignore the stop event`() {
            tile()

            handler.lastValue.processEvent(StopEvent(), coordinator)
        }

        @Test
        fun `processEvent will ignore the start event`() {
            tile()

            handler.lastValue.processEvent(StartEvent(), coordinator)
        }

        @Test
        fun `processEvent will ignore unexpected event`() {
            tile()

            handler.lastValue.processEvent(
                object : LifecycleEvent {},
                coordinator
            )
        }

        @Test
        fun `close will not change the tiles state`() {
            val tile = tile()

            tile.close()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
        }

        @Test
        fun `close will close the coordinator`() {
            val tile = tile()

            tile.close()

            verify(coordinator).close()
        }

        @Test
        fun `withLifecycleLock return the invocation value`() {
            val tile = tile()

            val data = tile.withLifecycleLock {
                33
            }

            assertThat(data).isEqualTo(33)
        }

        @Test
        fun `close tile will not be able to start`() {
            val tile = tile()
            tile.close()

            tile.start()

            assertThat(tile.state).isNotEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `gotError will set the state to error`() {
            val tile = tile()
            tile.start()

            tile.resourcesStarted(true)

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `gotError for the second time will not throw an exception`() {
            val tile = tile()
            tile.start()
            tile.resourcesStarted(true)

            tile.resourcesStarted(true)
        }
    }

    @Nested
    inner class LeafTileWithResourcesTests {

        @Test
        fun `startTile called createResource`() {
            var createResourceCalled = 0
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) {
                createResourceCalled ++
            }

            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(createResourceCalled).isEqualTo(1)
        }

        @Test
        fun `startTile will set error if created resource failed`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) {
                throw IOException("")
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `startTile will not set started until resources are created`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) { }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            tile.resourcesStarted(false)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `if an error occurs when resources are created the the the tile will error`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) { }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            tile.resourcesStarted(true)
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `startTile, stopTile, startTile will not restart the tile until resources are created`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) { }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
            tile.start()
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
            tile.resourcesStarted(false)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `stopTile will close all the resources`() {
            val actions = mutableListOf<Int>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    actions.add(2)
                }
                resources.keep {
                    actions.add(3)
                }
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(3, 2, 1))
        }

        @Test
        fun `stopTile will ignore error during stop`() {
            val actions = mutableListOf<Int>()

            fun createResources(resources: ResourcesHolder) {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    throw IOException("")
                }
                resources.keep {
                    actions.add(3)
                }
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(3, 1))
        }

        @Test
        fun `stopTile will not run the same actions again`() {
            val actions = mutableListOf<Int>()
            fun createResources(resources: ResourcesHolder) {
                resources.keep {
                    actions.add(1)
                }
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)

            tile.start()
            tile.stop()
            tile.stop()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(1))
        }

        @Test
        fun `second start will not restart anything`() {
            val called = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                called.incrementAndGet()
            })
            tile.start()
            tile.resourcesStarted(false)

            tile.start()

            assertThat(called).hasValue(1)
        }

        @Test
        fun `second start will not recreate the resources if it had errors`() {
            val called = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                called.incrementAndGet()
            })
            tile.start()
            tile.resourcesStarted(true)

            tile.start()

            assertThat(called).hasValue(1)
        }

        @Test
        fun `second start will recreate the resources if it was stopped`() {
            val called = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                called.incrementAndGet()
            })
            tile.start()
            tile.resourcesStarted(false)
            tile.stop()

            tile.start()

            assertThat(called).hasValue(2)
        }

        @Test
        fun `resourcesStarted will start tile if possible`() {
            val tile = DominoTile(TILE_NAME, factory)
            tile.resourcesStarted(false)

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `resourcesStarted will start tile stopped`() {
            val tile = DominoTile(TILE_NAME, factory)
            tile.start()
            tile.stop()

            tile.resourcesStarted(false)

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `resourcesStarted will start is configuration was bad`() {
            val tile = DominoTile(TILE_NAME, factory)
            tile.start()
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(IOException()))

            tile.resourcesStarted(false)

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `resourcesStarted will not start if it had issues`() {
            val tile = DominoTile(TILE_NAME, factory)
            tile.start()
            tile.resourcesStarted(true)

            tile.resourcesStarted(false)

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `resourcesStarted will not recreate the resources`() {
            val created = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                created.incrementAndGet()
            })
            tile.start()
            tile.resourcesStarted(false)

            tile.resourcesStarted(false)

            assertThat(created).hasValue(1)
        }
    }

    private data class Configuration(val data: Int)

    @Nested
    inner class LeafTileWithConfigTests {

        private val calledNewConfigurations = mutableListOf<Pair<Configuration, Configuration?>>()
        private val registration = mock<AutoCloseable>()
        private val configurationHandler = argumentCaptor<ConfigurationHandler>()
        private val service = mock<ConfigurationReadService> {
            on { registerForUpdates(configurationHandler.capture()) } doReturn registration
        }
        private val configFactory = mock<(Config)-> Configuration> {
            on { invoke(any()) } doAnswer {
                Configuration((it.arguments[0] as Config).getInt(""))
            }
        }
        private val key = "key"

        private inner class TileConfigurationChangeHandler : ConfigurationChangeHandler<Configuration>(
            service,
            key,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?,
                resources: ResourcesHolder
            ) {
                calledNewConfigurations.add(newConfiguration to oldConfiguration)
            }
        }

        private fun tile(): DominoTile {
            return DominoTile(TILE_NAME, factory, configurationChangeHandler = TileConfigurationChangeHandler())
        }

        @Test
        fun `start register listener`() {
            tile().start()

            verify(service).registerForUpdates(any())
        }

        @Test
        fun `close unregister listener`() {
            val tile = tile()
            tile.start()
            tile.close()

            verify(registration).close()
        }

        @Test
        fun `close close the coordinator`() {
            val tile = tile()

            tile.close()

            verify(coordinator).close()
        }

        @Test
        fun `onNewConfiguration will ignore irrelevant changes`() {
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(emptySet(), mapOf(key to mock()))

            verify(configFactory, never()).invoke(any())
        }

        @Test
        fun `onNewConfiguration will ignore changes with out the actual value`() {
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), emptyMap())

            verify(configFactory, never()).invoke(any())
        }

        @Test
        fun `onNewConfiguration will cause the tile to stop if bad config`() {
            val config = mock<SmartConfig>()
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(java.lang.RuntimeException("Bad config")))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
            assertThat(tile.isRunning).isFalse
        }

        @Test
        fun `onNewConfiguration will apply correct configuration if valid`() {
            val config = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(calledNewConfigurations)
                .hasSize(1)
                .contains(Configuration(33) to null)
        }

        @Test
        fun `onNewConfiguration will set the state to started if valid`() {
            val config = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            tile.configApplied(DominoTile.ConfigUpdateResult.Success)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
            assertThat(tile.isRunning).isTrue
        }

        @Test
        fun `onNewConfiguration will call only once if the configuration did not changed`() {
            val config1 = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val config2 = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config1))

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config2))

            assertThat(calledNewConfigurations)
                .hasSize(1)
                .contains(Configuration(33) to null)
        }

        @Test
        fun `onNewConfiguration will call on every change`() {
            val config1 = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val config2 = mock<SmartConfig> {
                on { getInt(any()) } doReturn 25
            }
            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config1))

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config2))

            assertThat(calledNewConfigurations)
                .hasSize(2)
                .contains(Configuration(33) to null)
                .contains(Configuration(25) to Configuration(33))
        }

        @Test
        fun `onNewConfiguration will not apply new configuration if not started`() {
            val config = mock<SmartConfig> {
                on { getInt(any()) } doReturn 11
            }
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            tile()

            assertThat(calledNewConfigurations)
                .isEmpty()
        }

        @Test
        fun `onNewConfiguration will re-apply after error`() {
            val badConfig = mock<SmartConfig> {
                on { getInt(any()) } doReturn 5
            }
            val goodConfig = mock<SmartConfig> {
                on { getInt(any()) } doReturn 17
            }

            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(java.lang.RuntimeException("Bad config")))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to goodConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.Success)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)

            assertThat(calledNewConfigurations).contains(Configuration(17) to Configuration(5))
        }

        @Test
        fun `if config is the same after error then the tile stays stopped`() {
            val badConfig = mock<SmartConfig> {
                on { getInt(any()) } doReturn 5
            }

            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(java.lang.RuntimeException("Bad config")))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.NoUpdate)
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            assertThat(calledNewConfigurations).hasSize(1)
            assertThat(tile.isRunning).isFalse
        }

        @Test
        fun `startTile will apply new configuration when started`() {
            val config = mock<SmartConfig> {
                on { getInt(any()) } doReturn 11
            }
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            val tile = tile()

            tile.start()

            assertThat(calledNewConfigurations)
                .hasSize(1)
                .contains(Configuration(11) to null)
        }

        @Test
        fun `startTile not will apply new configuration if not ready`() {
            val tile = tile()

            tile.start()

            assertThat(calledNewConfigurations)
                .isEmpty()
        }

        @Test
        fun `startTile will not register more than once`() {
            val tile = tile()

            tile.start()

            tile.start()

            verify(service, times(1)).registerForUpdates(any())
        }

        @Test
        fun `stopTile will close the registration`() {
            val tile = tile()
            tile.start()

            tile.stop()

            verify(registration).close()
        }

        @Test
        fun `applyNewConfiguration handle exceptions`() {
            whenever(configFactory.invoke(any())).thenThrow(RuntimeException("Bad configuration"))
            val config = mock<SmartConfig>()
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            val tile = tile()

            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
        }

        @Test
        fun `second start will not restart if the configuration had error`() {
            val resourceCreated = AtomicInteger(0)
            whenever(configFactory.invoke(any())).thenThrow(RuntimeException("Bad configuration"))
            val config = mock<SmartConfig>()
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            val tile = DominoTile(
                TILE_NAME,
                factory,
                configurationChangeHandler = TileConfigurationChangeHandler(),
                createResources =
                {
                    resourceCreated.incrementAndGet()
                }
            )
            tile.start()

            tile.start()

            assertThat(resourceCreated).hasValue(1)
        }
    }

    @Nested
    inner class InternalTileTest {
        private fun tile(children: Collection<DominoTile>): DominoTile = DominoTile(
            TILE_NAME,
            factory,
            children = children
        )

        @Nested
        inner class StartTileTests {
            @Test
            fun `startTile will register to follow all the tiles on the first run`() {
                val children = (1..3).map {
                    DominoTile(
                        "name-$it",
                        mock {
                            on { createCoordinator(any(), any()) } doReturn mock()
                        }
                    )
                }
                val tile = tile(children)

                tile.start()

                verify(coordinator).followStatusChangesByName(setOf(children[1].name))
            }

            @Test
            fun `startTile will not register to follow any tile for the second time`() {
                val children = (1..3).map {
                    DominoTile(
                        "name-$it",
                        mock {
                            on { createCoordinator(any(), any()) } doReturn mock()
                        }
                    )
                }
                val tile = tile(children)

                tile.start()
                tile.start()

                verify(coordinator, times(1)).followStatusChangesByName(setOf(children[1].name))
            }

            @Test
            fun `startTile will start all the children`() {
                val children = listOf<DominoTile>(mock(), mock(), mock())
                val tile = tile(children)

                tile.start()

                children.forEach {
                    verify(it, atLeast(1)).start()
                }
            }
        }

        @Nested
        inner class StartKidsIfNeededTests {
            @Test
            fun `startKidsIfNeeded will start all if there is no error child`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.StoppedByParent
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Started
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Created
                    },
                )

                tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(children[0]).start()
                verify(children[1]).start()
                verify(children[2]).start()
            }

            @Test
            fun `startKidsIfNeeded will set status to up if all are running`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.Started
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Started
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Started
                        on { isRunning } doReturn true
                    },
                )

                val tile = tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                assertThat(tile.isRunning).isTrue
            }

            @Test
            fun `startKidsIfNeeded will not start any if there is error child`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.StoppedByParent
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Started
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToError
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Created
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToBadConfig
                    },
                )

                tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                children.forEach {
                    verify(it, never()).start()
                }
            }

            @Test
            fun `startKidsIfNeeded will stop all non error children`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.StoppedByParent
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Started
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToError
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Created
                    },
                )

                tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(children[0]).stop()
                verify(children[1]).stop()
                verify(children[3]).stop()
            }

            @Test
            fun `Tests for startKidsIfNeeded`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.StoppedByParent
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToError
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Created
                    },
                )

                tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(children[1], never()).start()
            }

            @Test
            fun `startKidsIfNeeded will set the status to UP if all the children are running`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { isRunning } doReturn true
                    },
                )

                val tile = tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                assertThat(tile.isRunning).isTrue
            }
            @Test
            fun `startKidsIfNeeded will not set the status to UP if a child is not running`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { isRunning } doReturn false
                    },
                    mock {
                        on { isRunning } doReturn true
                    },
                )

                val tile = tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                assertThat(tile.isRunning).isFalse
            }
        }

        @Nested
        inner class HandleEventTests {
            @Test
            fun `up will start the tile`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { isRunning } doReturn true
                    },
                    mock {
                        on { isRunning } doReturn true
                    },
                )
                tile(children)

                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(children.first()).start()
            }

            @Test
            fun `other events will not throw an exception`() {
                val children = listOf<DominoTile>(
                    mock(),
                )
                tile(children)
                class Event : LifecycleEvent

                assertDoesNotThrow {
                    handler.lastValue.processEvent(
                        Event(),
                        coordinator
                    )
                }
            }

            @Test
            fun `down without error will stop the tile`() {
                val children = listOf(
                    mock<DominoTile> {
                        on { state } doReturn DominoTile.State.StoppedByParent
                    }
                )

                tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.DOWN
                    ),
                    coordinator
                )

                verify(children.first()).stop()
            }

            @Test
            fun `down with error will post error`() {
                val children = listOf(
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToError
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToBadConfig
                    },
                    mock<DominoTile> {
                        on { state } doReturn DominoTile.State.StoppedByParent
                    }
                )

                val tile = tile(children)
                handler.lastValue.processEvent(
                    RegistrationStatusChangeEvent(
                        mock(),
                        LifecycleStatus.ERROR
                    ),
                    coordinator
                )

                assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
            }
        }

        @Nested
        inner class StopTileTests {
            @Test
            fun `stopTile will stop all the non error children`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.Started
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToError
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Created
                    }
                )
                val tile = tile(children)

                tile.stop()

                verify(children[0]).stop()
                verify(children[2]).stop()
            }

            @Test
            fun `stopTile will not stop error children`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { state } doReturn DominoTile.State.Started
                    },
                    mock {
                        on { state } doReturn DominoTile.State.StoppedDueToError
                    },
                    mock {
                        on { state } doReturn DominoTile.State.Created
                    }
                )
                val tile = tile(children)

                tile.stop()

                verify(children[1], never()).stop()
            }
        }

        @Nested
        inner class CloseTests {
            @Test
            fun `close will close the registrations`() {
                val children = listOf<DominoTile>(mock())
                val registration = mock<RegistrationHandle>()
                doReturn(registration).whenever(coordinator).followStatusChangesByName(any())
                val tile = tile(children)
                tile.start()

                tile.close()

                verify(registration).close()
            }

            @Test
            fun `close will not try to close registration if non where open`() {
                val children = listOf<DominoTile>(mock())
                val registration = mock<RegistrationHandle>()
                doReturn(registration).whenever(coordinator).followStatusChangesByName(any())
                val tile = tile(children)

                tile.close()

                verify(registration, never()).close()
            }

            @Test
            fun `close will close the coordinator`() {
                val children = listOf<DominoTile>(mock())
                val tile = tile(children)

                tile.close()

                verify(coordinator).close()
            }

            @Test
            fun `close will close the children`() {
                val children = listOf<DominoTile>(mock())
                val tile = tile(children)

                tile.close()

                verify(children.first()).close()
            }

            @Test
            fun `close will not fail if closing a child fails`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { close() } doThrow RuntimeException("")
                    }
                )
                val tile = tile(children)

                tile.close()
            }
        }
    }
}
