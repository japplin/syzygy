# Syzygy

Syzygy aligns a dependency graph with a branch of a Compose composition. A root and its children use
the same DSL. Compose keeps the graphs alive across recompositions and disposes them when their
branches leave or their keys change.

```kotlin
@Composable
fun presentDocument(documentId: String): EditorState = syzygy {
    val app = alignGraph { createGraph<AppGraph>() }

    syzygy(key = documentId) {
        val editor = alignGraph(app) {
            app.editorGraphFactory.create(documentId)
        }
        editor.presenter.present()
    }
}
```

- Call `alignGraph` once per block before entering children or returning. It returns the
  graph, and the last expression in `syzygy` becomes the result.
- Pass the graph from the immediately enclosing `syzygy` when aligning a child. Syzygy checks
  identity before invoking the factory. The application supplies the Metro factory call.
- A graph can implement `HasAlignmentStrategy` to run Compose effects, register projections, or
  enter child alignments. Syzygy makes the graph available as the parent before running the strategy.
- Compose owns effect cancellation. Syzygy calls `AutoCloseable.close()` for graphs that implement
  it when it forgets or abandons them. Child disposal precedes parent cleanup. Compose requests
  cancellation of `LaunchedEffect` jobs without waiting for their completion.

## Running and projecting the presentation

Run Syzygy in an existing Compose runtime. The Android sample starts a headless AppState `listener`
from its `Application` and keeps that coroutine for the application's lifetime. An Activity observes
the committed presentation and supplies its projection snapshot to the UI. The alignment and
projection APIs also compile for JVM hosts, including Desktop.

A graph can opt in by implementing `ProjectionRegistry` and supplying `ProjectionAlignment`.
Compose `ProjectionHost.Content` around the root presenter to publish its output and the committed
projection snapshot together. For transitions, render each frame with its own snapshot. The sample
uses a navigation entry ID as the animation key, so the outgoing rendering stays available during
the fade even after the outgoing graph closes. Projections used during a transition must work with
their captured output after the corresponding graph has closed.

The sample's Settings projection renders Rename as a dialog. The presenter exposes the rename
state only while the dialog is open, and a nested alignment supplies its presenter and projection.

See [BUILDING.md](BUILDING.md) for build and test commands.
