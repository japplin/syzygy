package syzygy.sample

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import syzygy.ProjectionRegistry
import syzygy.projectionEntry

/**
 * The app graph contributes the root projection and exposes each feature extension factory. Each
 * factory receives the scope remembered by the corresponding `childSyzygy` call.
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ProjectionRegistry {
    override val entries get() = setOf(projectionEntry(AppState::class, AppProjection()))

    val coroutineScope: CoroutineScope
    val navigator: Navigator

    val homeGraphFactory: HomeGraph.Factory
    val profileGraphFactory: ProfileGraph.Factory
    val settingsGraphFactory: SettingsGraph.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides scope: CoroutineScope): AppGraph
    }
}

/**
 * Each screen is a Metro graph extension and an optional projection-registry contribution. Syzygy
 * never requires these graphs to implement a framework interface.
 */
@GraphExtension(HomeScope::class)
interface HomeGraph : ProjectionRegistry {
    override val entries get() = setOf(projectionEntry(HomeState::class, HomeProjection()))

    val coroutineScope: CoroutineScope
    val root: HomeModel

    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides scope: CoroutineScope): HomeGraph
    }
}

@GraphExtension(ProfileScope::class)
interface ProfileGraph : ProjectionRegistry {
    override val entries get() = setOf(projectionEntry(ProfileState::class, ProfileProjection()))

    val coroutineScope: CoroutineScope
    val root: ProfileModel

    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides scope: CoroutineScope): ProfileGraph
    }
}

@GraphExtension(SettingsScope::class)
interface SettingsGraph : ProjectionRegistry {
    override val entries get() = setOf(projectionEntry(SettingsState::class, SettingsProjection()))

    val coroutineScope: CoroutineScope
    val root: SettingsModel

    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides scope: CoroutineScope): SettingsGraph
    }
}
