package syzygy.projection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import dev.zacsweers.metro.DefaultBinding
import kotlin.reflect.KClass

/** Chooses how to animate between two values of the same projected output class. */
@DefaultBinding<ProjectionTransition<*>>
interface ProjectionTransition<Output : Any> {
    val outputType: KClass<Output>

    /** Runs during composition. Returns null to update the existing rendering in place. */
    fun transform(old: Output, new: Output): ContentTransform?
}

/** Creates a rule to contribute to a [ProjectionRegistry]. */
inline fun <reified Output : Any> projectionTransition(
    crossinline transform: (old: Output, new: Output) -> ContentTransform?,
): ProjectionTransition<Output> = object : ProjectionTransition<Output> {
    override val outputType: KClass<Output> = Output::class

    override fun transform(old: Output, new: Output): ContentTransform? = transform(old, new)
}

@Composable
internal fun ProjectWithTransitions(frame: ProjectionFrame) {
    val history = remember { ProjectionHistory() }
    val previous = history.target
    val next = remember(frame) {
        val transform = previous?.content?.frame?.let { old -> frame.transformFrom(old) }
        val content = if (previous != null && transform == null) {
            previous.content
        } else {
            ProjectionContent(frame, transform)
        }
        ProjectionTarget(content, animated = transform != null || previous?.animated == true)
    }

    SideEffect {
        next.content.frame = frame
        history.target = next
    }

    if (next.animated) {
        val state = remember { MutableTransitionState(previous?.content ?: next.content) }
        state.targetState = next.content
        rememberTransition(state, label = "Projection").AnimatedContent(
            transitionSpec = { targetState.transform ?: NoProjectionAnimation },
        ) { content ->
            val active = content === next.content
            content.render(if (active) frame else content.frame, active)
        }
    } else {
        next.content.render(frame, true)
    }
}

private val NoProjectionAnimation = ContentTransform(
    EnterTransition.None,
    ExitTransition.None,
    sizeTransform = null,
)

private class ProjectionHistory {
    var target: ProjectionTarget? = null
}

private class ProjectionTarget(val content: ProjectionContent, val animated: Boolean)

private class ProjectionContent(var frame: ProjectionFrame, val transform: ContentTransform?) {
    private val content: @Composable (ProjectionFrame, Boolean) -> Unit = { shown, active ->
        shown.Render(active)
    }

    // Preserve a participating frame's composition as it moves inside AnimatedContent.
    val render = if (frame.hasTransition) movableContentOf(content) else content
}
