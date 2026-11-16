# Syzygy

**A syzygy is the alignment of three or more celestial bodies.**

Syzygy aligns the lifetimes of a `CoroutineScope`, a Compose runtime, and a `DependencyGraph` into one you control — so scope, runtime, and graph live and die together, on your terms. And Syzygies compose: each aligns to its parent, forming a tree of scoped runtimes shaped to your app.

Syzygy is a small primitive, not a framework. It owns the two things whose shared lifetime *is* the alignment it guarantees — a coroutine scope and a model — and leaves a single seam for you to bind everything else (typically a DI graph) to that same lifetime. Rendering, navigation, retention policy, and process‑death restoration are separate, independently adoptable layers.

The core is pure Kotlin. It depends only on multiplatform libraries — the Compose runtime, coroutines, and Molecule — and imports no platform types, so a `Model` produces state, not pixels. An entire Syzygy tree — nested nodes, scoped graphs, effects and all — is therefore **multiplatform and runs headless** wherever Kotlin runs. In practice that means you can build and drive a full tree in an ordinary unit test on the JVM, with no Android, no instrumentation, no Robolectric. Android is where the *layers* live (rendering via Compose UI, navigation via Nav3), not the core.

---

## The problem it solves

The motivating case is Android, where "how long does this screen's state live" is three questions managed separately: a `ViewModel`'s lifecycle, a `CoroutineScope`'s cancellation, and a DI subcomponent's teardown. You coordinate them by hand and hope they agree. The same shape of problem — a scope, a running computation, and a scoped object graph that must share a lifetime — appears anywhere you drive Compose, on or off Android.

Syzygy makes them one question. Because the three lifetimes are *aligned into one*, you decide how long that unit lives with a single policy choice, and the scope, runtime, and graph obey it by construction — they can't disagree, because they're the same lifetime.

---

## Core concepts

There are exactly two contracts you write, plus one function that runs them.

### `Model` — a composable transformation

A `Model` transforms an `Input` into an `Output`. Its `transform` function is `@Composable`, which means it can hold state (`remember`), run effects (`LaunchedEffect`), and call other `Model`s — so models compose.

```kotlin
interface Model<Input, Output> {
    @Composable fun transform(input: Input): Output
}
```

A `Model` holds no state in class properties. All state lives in the Compose runtime that runs it. It is a pure function of its input, testable in isolation with Molecule + Turbine — no `ViewModel`, no Android.

### `Projection` — a view onto the output

A `Projection` projects an `Output` onto the screen. It is a pure, disowned counterpart to a `Model`: bring your own, swap freely, register globally. A model and its projection are paired by their shared `Output` type.

```kotlin
interface Projection<Output> {
    @Composable fun project(output: Output)
}
```

### `syzygy()` — align and run

`syzygy()` creates an aligned runtime: it owns a `CoroutineScope`, runs your `Model` on it as its own Compose runtime, and returns the model's `Output` as observable state. The `align` lambda is the seam — Syzygy hands you the owned scope (and the parent node) and you build whatever you want bound to this lifetime, almost always a dependency graph.

```kotlin
@Composable
fun <Input, Output> syzygy(
    input: Input,
    vararg keys: Any?,                                     // identity for lists, tabs
    lifetime: Lifetime = Lifetime.Retained,               // how long the aligned unit lives
    align: (CoroutineScope, Syzygy<*, *>?) -> Syzygy<Input, Output>,
): State<Output>
```

Because the model runs on the owned scope, any `LaunchedEffect` or `rememberCoroutineScope` inside `transform` runs on **that** scope — surviving configuration change if the scope is retained, and cancelled exactly when the aligned unit is retired. Effect alignment is not a feature you configure; it falls out of the runtime being genuinely separate.

### `Syzygy` — the aligned unit

The type your graphs implement. It carries the two owned things: the scope and the root model.

```kotlin
interface Syzygy<Input, Output> {
    val coroutineScope: CoroutineScope
    val root: Model<Input, Output>
}
```

---

## Controlling the lifetime

Because the three lifetimes are aligned into one, there is only one thing to decide: how long that unit lives. That is a single policy axis.

```kotlin
sealed interface Lifetime {
    object Retained : Lifetime            // survives config change; retires on removal (default)
    object Remembered : Lifetime          // dies on composition exit, including rotation
    object NavEntry : Lifetime            // kept while on the back stack; retired on pop
    data class Scoped(val scope: CoroutineScope) : Lifetime  // lives exactly as long as your scope
}
```

`Retained` is the default because it is the safe direction — a real screen should survive rotation. `Remembered` is the deliberate opt‑out for disposable units (a transient sheet, a throwaway step). `NavEntry` ties the unit to navigation. `Scoped` is the escape hatch: hand in `viewModelScope`, an app‑level scope, or anything else, and retention becomes an input rather than a policy.

> **Process death is orthogonal to this axis.** Every `Lifetime` controls in‑memory survival only; none survive a low‑memory process kill. Serialized restoration is a separate, additive tier — call `rememberSaveable` inside `transform` (see *Layers*).

---

## Building a dependency graph

DI is not owned by Syzygy — it is the canonical thing you thread through the `align` seam. The core knows nothing about any DI framework: `align` hands you a scope, you hand back a `Syzygy`, and how you build it is yours. Syzygy works with any DI (Dagger/Hilt, kotlin‑inject, hand‑wired factories) — or none. The docs and samples **recommend [Metro](https://github.com/ZacSweers/metro)** (a compile‑time DI framework, also recommended by Amazon's App Platform) because its graph extensions map cleanly onto Syzygy's tree, and that is what the example below uses. Your graph simply implements `Syzygy`, and your DI framework provides the scope you were handed.

```kotlin
// The app graph is a Metro @DependencyGraph that is also a Syzygy.
@DependencyGraph(AppScope::class)
interface AppGraph : Syzygy<Unit, HomeState> {
    override val coroutineScope: CoroutineScope   // backed by the @Provides below
    override val root: HomeModel                  // Metro constructs it from its @Inject deps
    val profileGraphFactory: ProfileGraph.Factory // exposing this wires the extension

    @DependencyGraph.Factory
    fun interface Factory {
        // the Syzygy scope is handed in by syzygy(); becomes the graph's coroutineScope
        fun create(@Provides scope: CoroutineScope): AppGraph
    }
}

@Inject @SingleIn(AppScope::class)
class UserRepository {
    val currentUserName: StateFlow<String> = MutableStateFlow("Ada")
}

@Inject
class HomeModel(
    private val users: UserRepository,
) : Model<Unit, HomeState> {
    @Composable
    override fun transform(input: Unit): HomeState {
        val name by users.currentUserName.collectAsState()

        // A child aligned unit, created from a graph EXTENSION of this one.
        // `parent` is the AppGraph, discovered automatically.
        val profile by syzygy(Unit, "profile") { scope, parent ->
            (parent as AppGraph).profileGraphFactory.create(scope)
        }

        return HomeState(greeting = "Hello, $name", profile = profile)
    }
}
```

The child feature is a **graph extension** — it reaches app‑scoped bindings while adding its own:

```kotlin
@GraphExtension(ProfileScope::class)
interface ProfileGraph : Syzygy<Unit, ProfileState> {
    override val coroutineScope: CoroutineScope
    override val root: ProfileModel

    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides scope: CoroutineScope): ProfileGraph
    }
}

@Inject @SingleIn(ProfileScope::class)
class ProfileAnalytics { fun screenViewed() { /* … */ } }

@Inject
class ProfileModel(
    private val users: UserRepository,      // reaches a PARENT (app) binding
    private val analytics: ProfileAnalytics // its OWN extension binding
) : Model<Unit, ProfileState> {
    @Composable
    override fun transform(input: Unit): ProfileState {
        // Runs on the Profile scope — cancelled when this unit is retired.
        LaunchedEffect(Unit) { analytics.screenViewed() }
        val name by users.currentUserName.collectAsState()
        return ProfileState(displayName = name)
    }
}
```

The tree of `syzygy()` calls forms a matching tree of graph extensions: `AppGraph` → `ProfileGraph`, each scope aligned to its own runtime, each reaching its parent. This is the tree the name promises.

---

## Rendering

Projections register themselves, keyed by output type, and are looked up ambiently. Rendering lives in a separate module (`syzygy-projection`); the core never touches it.

```kotlin
@ContributesIntoMap(AppScope::class)
@OutputKey(HomeState::class)
@Inject
class HomeProjection : Projection<HomeState> {
    @Composable override fun project(output: HomeState) {
        Text(output.greeting)
        Project(output.profile)   // recursive: render the child output
    }
}
```

Projections are pure functions of output. Anything scoped a projection might need is computed by the model and passed through the output (including callbacks that close over scoped dependencies), which keeps the render layer free of DI and lets the registry be global.

---

## Putting it together

```kotlin
setContent {
    // parent is null at the root; build the app graph via Metro's factory
    SyzygyContent(Unit, "app") { scope, _ ->
        createGraphFactory<AppGraph.Factory>().create(scope)
    }
}
```

`SyzygyContent` runs the aligned unit and projects its output; `syzygy()` returns raw output for a parent model to embed. Same engine, two entry points: one for rendering a screen, one for nesting a model.

---

## Testing — drive the whole tree headless

The core is multiplatform Kotlin with no platform dependencies, so a `Model` produces state rather than pixels and an entire tree — nested nodes, scoped graphs, effects — runs headless wherever Kotlin runs. The convenient place to do that is an ordinary JVM unit test: no Android, no instrumentation, no Robolectric; just Molecule + Turbine collecting the output.

```kotlin
// src/test — a normal JVM test, not an instrumented one.
@Test
fun homeGreetsTheUser() = runTest {
    val graph = createGraphFactory<AppGraph.Factory>().create(backgroundScope)

    moleculeFlow(RecompositionMode.Immediate) {
        graph.root.transform(Unit)          // runs the real model tree headless
    }.test {
        val state = awaitItem()
        assertThat(state.greeting).isEqualTo("Hello, Ada")
        assertThat(state.profile.displayName).isEqualTo("Ada")   // child node, same test
    }
}
```

The same graph you ship runs in the test — real DI, real nesting, real effects — because nothing in the tree touches the Android framework. Rendering and navigation are the only Android‑bound pieces, and they are separate layers you don't need in order to test behavior.

---

## Modules

| Module | Contains | Android? |
| --- | --- | --- |
| `syzygy-core` | `Model`, `Projection`, `Syzygy`, `syzygy()`, the aligned‑unit engine | No — multiplatform Kotlin (Compose runtime + coroutines + Molecule) |
| `syzygy-projection` | The projection registry and `Project(output)` lookup | Compose UI |
| `syzygy-nav3` | A Nav3 integration hosting one aligned unit per back‑stack entry | Android + Nav3 |
| `sample` | An example app wiring Metro + Nav3 + projections | Android |

`syzygy-core` deliberately imports no platform types — it depends only on multiplatform libraries (the Compose runtime, coroutines, Molecule). So the core and any tree built on it are **multiplatform and headless**: they run wherever Kotlin runs, which makes it natural to construct and drive a full Syzygy tree in an ordinary JVM `src/test` unit test — no Android, no Robolectric. Android enters only through the integration modules (`syzygy-projection` for Compose UI rendering, `syzygy-nav3` for navigation). Those integrations target Android today; adding another platform is a matter of writing its integration modules, not touching the core.

---

## What Syzygy is *not*

Syzygy owns the producer side (a model produces output) and disowns the consumer side (what draws output). Everything below is a seam or a layer, never core:

- **Rendering** — a separate module; bring your own if you like.
- **Navigation** — an integration (`syzygy-nav3`); the core is navigation‑agnostic.
- **Process‑death restoration** — a saveable tier, additive to the lifetime axis.
- **DI** — not owned, not required; the canonical thing you align through the seam.

This is what makes Syzygy adoptable one piece at a time rather than all at once.

---

## Status & roadmap

**Now:** the aligned‑unit primitive, DI through the seam (Metro in the samples), the projection layer, and a Nav3 integration with single‑stack retention.

**Next (named seams, not gaps):**

- Multi‑back‑stack retention (bottom‑nav tabs).
- Process‑death restoration (the saveable tier).
- A retain‑free hosting adapter (run a `Model` on a plain `ViewModel` for incremental adoption with zero other Syzygy pieces).

Each is a defined extension point, so "does it scale to X?" answers with "that seam — here is its shape," not "we'll figure it out."

---

## Prior art & thanks

Syzygy stands on ideas from **Amazon's App Platform** (the presenter/renderer shape), **Slack's Circuit** (retained presenters, pure UIs), **Cash App's Molecule** (running Compose to produce state), and **Metro** (compile‑time DI). It differs from all of them by being a *primitive* — the minimum that makes lifetime alignment a guarantee — with everything else left as a layer you choose.
