package syzygy.sample

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import syzygy.HasAlignmentStrategy
import syzygy.projection.ProjectionAlignment
import syzygy.projection.ProjectionEntry
import syzygy.projection.ProjectionRegistry

@DependencyGraph(AppScope::class)
interface AppGraph {
    val presenter: AppPresenter
    val homeGraphFactory: HomeGraph.Factory
    val profileGraphFactory: ProfileGraph.Factory
    val settingsGraphFactory: SettingsGraph.Factory
}

interface ProjectingGraph : ProjectionRegistry, HasAlignmentStrategy {
    override val alignmentStrategy get() = ProjectionAlignment
}

@GraphExtension(HomeScope::class)
interface HomeGraph : ProjectingGraph {
    override val projections get() = setOf(ProjectionEntry(HomeState::class, HomeProjection))
    val presenter: HomePresenter

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): HomeGraph
    }
}

@GraphExtension(ProfileScope::class)
interface ProfileGraph : ProjectingGraph {
    override val projections get() = setOf(ProjectionEntry(ProfileState::class, ProfileProjection))
    val presenter: ProfilePresenter

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): ProfileGraph
    }
}

@GraphExtension(SettingsScope::class)
interface SettingsGraph : ProjectingGraph {
    override val projections get() = setOf(ProjectionEntry(SettingsState::class, SettingsProjection))
    val presenter: SettingsPresenter
    val renameGraphFactory: RenameGraph.Factory

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): SettingsGraph
    }
}

@GraphExtension(RenameScope::class)
interface RenameGraph : ProjectingGraph {
    override val projections get() = setOf(ProjectionEntry(RenameState::class, RenameProjection))
    val presenter: RenamePresenter

    @GraphExtension.Factory
    fun interface Factory {
        fun create(): RenameGraph
    }
}
