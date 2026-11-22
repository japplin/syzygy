package syzygy.sample

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import syzygy.HasAlignmentStrategy
import syzygy.HasChildAlignmentStrategy
import syzygy.navigation.Navigator
import syzygy.projection.ProjectionHost
import syzygy.projection.ProjectionRegistry
import syzygy.projection.ProjectionTransition

@DependencyGraph(AppScope::class)
interface AppGraph : ProjectionRegistry, HasAlignmentStrategy, HasChildAlignmentStrategy {
    val projectionHost: ProjectionHost
    override val alignmentStrategy get() = projectionHost
    override val childAlignmentStrategy get() = projectionHost

    @Multibinds(allowEmpty = true)
    val metroProjectionTransitions: Set<ProjectionTransition<*>>
    override val projectionTransitions get() = metroProjectionTransitions

    val presenter: AppPresenter
    val homeGraphFactory: HomeGraph.Factory
    val profileGraphFactory: ProfileGraph.Factory
    val settingsGraphFactory: SettingsGraph.Factory

    @Provides
    @SingleIn(AppScope::class)
    fun provideNavigator(): Navigator<Destination> = Navigator(Destination.Home)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides projectionHost: ProjectionHost): AppGraph
    }
}

@GraphExtension(HomeScope::class)
interface HomeGraph : ProjectionRegistry {
    val presenter: HomePresenter

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): HomeGraph
    }
}

@GraphExtension(ProfileScope::class)
interface ProfileGraph : ProjectionRegistry {
    val presenter: ProfilePresenter

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): ProfileGraph
    }
}

@GraphExtension(SettingsScope::class)
interface SettingsGraph : ProjectionRegistry {
    val presenter: SettingsPresenter
    val renameGraphFactory: RenameGraph.Factory

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): SettingsGraph
    }
}

@GraphExtension(RenameScope::class)
interface RenameGraph : ProjectionRegistry {
    val presenter: RenamePresenter

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): RenameGraph
    }
}
