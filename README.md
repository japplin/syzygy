# Syzygy [SIZ-uh-jee] [<kbd>▶ Hear it</kbd>][pronunciation]

<img src="docs/assets/syzygy-logo-nebula.png" width="160" height="160" alt="Three stars aligned along a chain of starlight in a band of galactic dust.">

[GitHub Pages documentation](https://japplin.github.io/syzygy/)

In astronomy, a syzygy is the alignment of three or more celestial bodies. During a solar eclipse,
the Sun, Moon, and Earth line up, with the Moon in the middle.

Syzygy brings that idea to Compose by aligning three trees in your app.

- **Dependency graphs.** Graph extensions form a tree under a root graph. Each extension inherits
  dependencies from its parent and adds the ones its branch needs.
- **Composed state.** Composable presenters produce state and assemble it into a tree. A parent
  presenter can include the state returned by its children.
- **Compose UI.** Composables render that state as a UI tree. A parent renderer can hand a child
  state to the renderer for that branch.

<details>
<summary>Watch the constellations align</summary>

![A meteor pulls three groups of stars into alignment from left to right. Smaller stars travel straight into their center star, and their glowing connections join the diagonal wake.](docs/assets/syzygy-alignment.webp)

Artwork inspired by Hubble's view of NGC 5728. [ESA/Hubble, A. Riess et al., J. Greene][galaxy-reference]

</details>

In the Android sample, Settings creates a child graph when the user opens Rename. The child graph
supplies the Rename presenter, which returns a `RenameState`. Settings includes that state in its
own state, and the UI renders it as a dialog using the child graph's projection. The dependencies,
state, and UI all refer to the same Rename branch. That's the syzygy.

The alignment is extensible. An `AlignmentStrategy` can run Compose effects, pass values down
the alignment tree, or connect the graph and its result to another part of your architecture.
Projections are one example of a strategy that connects state to UI.

## Align a graph and its state

Use `:syzygy` for the core DSL. Wrap each branch in `syzygy`, call `alignGraph` once before
entering child branches, and return the presenter's state. Compose retains the graph across
recompositions. Changing the branch's key replaces its graph and descendants.

This example uses Metro. The parent graph exposes an `EditorGraph.Factory` accessor, and Metro
injects that factory into `AppPresenter`. The document ID keys the editor alignment.

```kotlin
@Composable
fun presentDocument(documentId: String): EditorState = syzygy {
    val graph = alignGraph { createGraph<AppGraph>() }
    graph.presenter.present(documentId)
}

@Inject
class AppPresenter(private val editorGraphFactory: EditorGraph.Factory) {
    @Composable
    fun present(documentId: String): EditorState = syzygy(key = documentId) {
        val graph = alignGraph { editorGraphFactory.create(documentId) }
        graph.presenter.present()
    }
}
```

Syzygy finds the parent alignment through Compose. You supply the graph factory. A Compose host
with a retained values store can restore the same graph instances when it recreates the composition.

## Extend the alignment

- Implement `HasAlignmentStrategy` on a graph to run a strategy for that graph. Implement
  `HasChildAlignmentStrategy` to run a strategy for every descendant. Inherited strategies run
  from the outermost ancestor inward before the graph's own strategy runs.
- Use `align(graph)` to work with the graph and `onResult(graph, result)` to observe the value
  the block returns. Use `AlignmentLocal` to share a value with descendant alignments and
  `rememberAligned` after `alignGraph` to cache a value for the current graph.
- Use `close()` to release resources owned by an alignment strategy. Give each graph lifetime
  its own resource-owning strategy.

An extension can call `currentAlignmentLifetime()` after `alignGraph` and acquire a hold with
`retain()`. Close that hold when the extension finishes using the graph. For example, a
`DisposableEffect` can acquire a hold while mounted and pass it to a host coroutine that releases
it in `finally` after suspending cleanup on disposal. Projections use the same lifetime API.

For graphs with custom suspending teardown, pass `onClose` when aligning the graph.

```kotlin
val graph = alignGraph(onClose = { it.shutdown() }) { factory.create() }
```

Syzygy captures the callback when it creates the graph. The callback replaces the graph's default
close operation and runs once its children and all holders have released it.

## Render state with projections

Add `:syzygy-projection` to render state with `Project(state)`, which selects a `Projection<T>`
by the state's exact class. Each graph exposes its full projection map, including inherited
bindings, through `ProjectionRegistry`. The selected map is available to the renderer and its
descendants.

Use `ProjectionAlignmentStrategy` when presentation and rendering share a composition. For
separate compositions, use a shared `ProjectionHost`. The sample installs its host on the root
graph with both `HasAlignmentStrategy` and `HasChildAlignmentStrategy`. An AppState `transform`
in the Application composes the state tree, and the Activity calls `projectionHost.Project(state)`
to render it. The host uses the recorded registry of each returned state object, so nested
`Project` calls can pick up the bindings from the graphs that produced those states.

Hosted lookup uses object identity. If siblings need different renderers for the same state
instance, return distinct instances or call `projected(state)` inside the producing alignment
to carry its registry with the state. First render the state while its producing graph is still
open.

Graphs can also supply `ProjectionTransition<T>` rules. Lookup starts at the producing alignment
and walks up to its ancestors. A rule can return a Compose `ContentTransform` for an update or
`null` to render it in place. Each animated frame uses its own registry. Outgoing frames expose
`LocalProjectionActive.current == false` so their renderers can disable input and Back handling.

For Metro wiring, see the sample's [root graph][graphs] and [projection contributions][projections].

## Cooperative cancellation

When a presenter subtree leaves composition, Compose requests cancellation of its effects.
As the alignments retire, Syzygy tears down their graphs from the leaves up. Each child keeps its
parent open until the child finishes closing, so it can still use parent dependencies during
cleanup. Syzygy closes each graph's strategies before calling the graph's `close()`.

If an exit animation still renders a state, `Project` automatically defers closing the graph
that produced it. The incoming screen can render as soon as its state is ready. The outgoing
graph stays open until Compose disposes its rendering, even when another transition interrupts
the animation.

This also works with Metro's experimental `SuspendCloseableGraph`. Once the graph can close,
Syzygy runs Metro's suspending `close()` independently of the presenter job. Metro awaits
suspending `@OnGraphClose` callbacks, and Syzygy keeps the parent graph open until that cleanup
finishes. [Metro graph lifecycle][metro-graph-lifecycle]

Set `shutdownTimeout` on `alignGraph` to give retirement a deadline. Syzygy starts the first
timer when Compose retires the graph and starts a fresh timer when the graph is ready to close.
Code holding the alignment's `AlignmentLifetime` can call `awaitClosed()` to observe the result.
It throws `AlignmentShutdownTimeoutException` if either deadline expires, with a phase that
distinguishes outstanding holds from slow cleanup. Release your own holds before awaiting.
A timeout leaves the holds in place and lets teardown continue as they release.
The default is `Duration.INFINITE`.

## Add navigation and Back handling

The optional `:syzygy-navigation` module provides an observable `Navigator<Route>` and Compose
Back dispatch. Use the active `NavEntry` as the alignment key so two visits to the same route
get distinct branches. Pass the captured entry to `push` or `pop` to guard against navigation
from an outgoing rendering.

On Android, install `AndroidBackHost` once per Back dispatcher. Use `BackScope` for the active
branch and `BackOverlay` for layers above it. Back visits the highest ordered overlay first,
then the active branch, then its parent. A modal overlay consumes Back even without a callback.
Set `active` to exclude outgoing content or select the focused pane. Custom hosts can connect
platform Back input to `BackHost.Content`, `canHandleBack`, and `dispatchBack()`.

## Test a presenter

Use the test tools for the host that runs your composable presenter. Both hosts can run a
whole `syzygy` tree with test dependencies supplied through its graph factories.

With Molecule, use `moleculeFlow` in immediate mode and assert the emitted states with Turbine.
[Molecule testing guide][molecule-testing]

```kotlin
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest

@Test
fun incrementsTheCount() = runTest {
    moleculeFlow(RecompositionMode.Immediate) { presenter.present() }.test {
        val initial = awaitItem()
        assertEquals(0, initial.count)

        initial.increment()
        assertEquals(1, awaitItem().count)
    }
}
```

With AppState Transform, use `transformTest` from `androidx.appstate.transform:transform-testing`.
Call `advance()` to drive recomposition, then assert `State.value`. For Turbine assertions,
collect `snapshotFlow { state.value }`. [AppState testing guide][transform-testing]

```kotlin
import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform

@Test
fun incrementsTheCount() = transformTest {
    val state = transform<CounterState?>(
        initialValue = null,
        scope = backgroundScope,
        dispatcher = testDispatcher,
    ) { presenter.present() }

    advance()
    val initial = checkNotNull(state.value)
    assertEquals(0, initial.count)

    initial.increment()
    advance()
    assertEquals(1, checkNotNull(state.value).count)
}
```

## Run the sample

The modules target Android and JVM. The Android sample takes you through Home, Profile, Settings,
and the Rename dialog. See its [presenters][presenters] for state composition and
[BUILDING.md](BUILDING.md) for build and test commands.

[pronunciation]: https://media.merriam-webster.com/audio/prons/en/us/mp3/s/syzygy01.mp3 "Hear Syzygy pronounced by Merriam-Webster"
[galaxy-reference]: https://esahubble.org/images/potw2139a/
[graphs]: sample/src/main/kotlin/syzygy/sample/Graphs.kt
[presenters]: sample/src/main/kotlin/syzygy/sample/Presenters.kt
[projections]: sample/src/main/kotlin/syzygy/sample/Projections.kt
[metro-graph-lifecycle]: https://github.com/ZacSweers/metro/blob/1.6.0/docs/dependency-graphs.md#graph-lifecycle
[molecule-testing]: https://github.com/cashapp/molecule#testing
[transform-testing]: https://developer.android.com/topic/libraries/architecture/appstate/transform#test-transforms
