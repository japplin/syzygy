package syzygy.projection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.zacsweers.metro.MapKey
import syzygy.Projection
import kotlin.reflect.KClass

/**
 * The global projection registry: a map from an output's type to the [Projection] that draws it.
 *
 * Projections are pure functions of output, so a single global map keyed by output type is correct —
 * there is no per-graph scoping to reconcile (IMPLEMENTATION_PLAN §3). The map is populated once (e.g.
 * by Metro `@ContributesIntoMap` + [OutputKey] multibindings) and provided via [LocalProjections].
 */
typealias ProjectionRegistry = Map<KClass<*>, Projection<*>>

/**
 * The ambient projection registry. Provide it once near the root — normally from your DI graph's
 * multibinding: `CompositionLocalProvider(LocalProjections provides graph.projections) { ... }`.
 */
val LocalProjections: ProvidableCompositionLocal<ProjectionRegistry> = staticCompositionLocalOf { emptyMap() }

/**
 * Metro multibinding key pairing a [Projection] with the output type it draws.
 *
 * ```
 * @ContributesIntoMap(AppScope::class)
 * @OutputKey(HomeState::class)
 * @Inject
 * class HomeProjection : Projection<HomeState> { ... }
 * ```
 */
@MapKey
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class OutputKey(val value: KClass<*>)

/**
 * The registry is type-safe to populate (each [OutputKey] pairs a class with a `Projection` of that
 * type) but unsafe to read, because the map erases the pairing. This is the single, centralized
 * `@Suppress` for that read — provably safe because the lookup key is the output's own class
 * (IMPLEMENTATION_PLAN §7). Every projection lookup goes through here.
 */
fun <Output : Any> ProjectionRegistry.projectionFor(output: Output): Projection<Output> {
    val projection = this[output::class]
        ?: error(
            "No Projection registered for ${output::class.qualifiedName}. Register one with " +
                "@ContributesIntoMap(<scope>) + @OutputKey(${output::class.simpleName}::class) on a " +
                "Projection<${output::class.simpleName}>, and expose the map as LocalProjections.",
        )
    @Suppress("UNCHECKED_CAST")
    return projection as Projection<Output>
}

/**
 * Render [output] with its registered [Projection], looked up ambiently by output type. Recursive:
 * a projection calls `Project(childOutput)` to draw a child node's output (README, *Rendering*).
 */
@Composable
fun <Output : Any> Project(output: Output) {
    LocalProjections.current.projectionFor(output).project(output)
}
