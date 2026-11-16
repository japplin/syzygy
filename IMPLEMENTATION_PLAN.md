# Syzygy — Implementation Plan

This document is written for an engineer or coding agent building Syzygy from the README. It explains **why** each API decision was made, gives the **context** on the libraries Syzygy borrows from and competes with, defines the **build order**, and names the **disciplines** that must hold or the design collapses. Read this in full before writing code. When a decision seems arbitrary, the reason is here; do not "simplify" it away without checking the motivation.

---

## 1. What Syzygy is, precisely

Syzygy is a **primitive**, not a framework. Its single responsibility: **create a tree of aligned Compose runtimes**, where each node aligns the lifetimes of a `CoroutineScope`, a Compose runtime, and (via a seam) a dependency graph into one controllable unit, so the three live and die together.

Everything that is not that alignment — rendering, navigation, DI, process‑death, retention policy details — is a **layer** or a **seam**, in a separate module, adoptable independently. The prime directive: *keep the core small and unopinionated; push everything else out.*

A direct consequence of that directive, and a property to protect deliberately: **the core is multiplatform Kotlin, and any tree built on it runs headless wherever Kotlin runs.** A `Model` produces state, not pixels; `syzygy-core` depends only on multiplatform libraries (the Compose runtime, coroutines, Molecule) and imports no platform types. Nothing in the core is JVM‑ or Android‑specific — the JVM is simply the convenient place to *run* a headless tree in a unit test. This is not incidental; it is the payoff of owning the producer side and disowning the consumer side, and it is what makes the whole tree testable in an ordinary `src/test` unit test with no Android, instrumentation, or Robolectric. If a change would tie the core to a platform, or make a full tree impossible to run headless in a plain unit test, it is the wrong change.

The litmus test for any proposed change: **"Does this help align a tree of runtimes, or does it assume what the runtimes are for?"** The first is core. The second is a layer, and belongs in a different module.

---

## 2. Background context (read before designing anything)

### Metro (the recommended DI — a companion, not a core dependency)

Metro is a compile‑time dependency injection framework for Kotlin (KMP), unifying the ideas of Dagger, Anvil, and kotlin‑inject into one tool with no annotation processor split. Reached 1.0 in 2026. It is **also the recommended DI for Amazon's App Platform**.

**Crucial:** `syzygy-core` does **not** depend on Metro, or on any DI framework. DI is threaded through the `align` seam — the core hands you a scope, you hand back a `Syzygy`, and how you built it is invisible to the core. Syzygy works with Dagger/Hilt, kotlin‑inject, hand‑wired factories, or no DI at all. Metro is what the **docs and samples recommend**, not what the core uses. Never make `syzygy-core` reference a Metro type.

Why Metro is the recommendation (for samples and docs): it is compile‑time‑validated and aggregation‑based, built for large modularized codebases (200+ engineers, hundreds of modules), and its **graph extensions map cleanly onto Syzygy's tree** — a child node's graph extends its parent's, which is exactly the nesting Syzygy produces. It also keeps Syzygy in the same ecosystem as App Platform. A different DI framework would use its own equivalents behind the same `align` seam.

The Metro surface the sample uses:

- `@DependencyGraph(SomeScope::class)` — declares a graph; `@DependencyGraph.Factory` for a factory with `@Provides` parameters.
- `@GraphExtension(SomeScope::class)` with `@GraphExtension.Factory` — a child graph that extends a parent, wired **like Dagger subcomponents**. The parent exposes the extension's factory as an accessor (`val childFactory: ChildGraph.Factory`); that accessor is what attaches the extension.
- `@Inject`, `@Provides`, `@SingleIn(Scope::class)`, `@ContributesBinding`, `@ContributesIntoMap`.
- `createGraph<T>()` / `createGraphFactory<T.Factory>()` to instantiate.

**Important recency note:** Metro removed the older `@Extends` / `isExtendable` extension model in favor of the subcomponent‑style `@GraphExtension`. Do **not** use `@Extends`. Verify exact annotation signatures against the Metro version you pin — the extension API moved during development.

### Amazon App Platform (the ancestor — what we borrow and what we reject)

App Platform (by vrallev / Ralf Wondratschek) is a **cohesive** Android presentation framework. Its good ideas, which Syzygy borrows:

- **`MoleculePresenter`** — a `@Composable present()` that produces state via Cash App's Molecule. Syzygy's `Model.transform` is the same shape, renamed.
- **`Renderer` + `@ContributesRenderer`** — a model‑type‑keyed map of renderers. Syzygy's `Projection` + registry is the same idea, renamed and made pure/global.
- Metro as recommended DI.

What Syzygy **rejects** — and *why the rejection is the whole reason Syzygy exists*: App Platform couples its pieces (a Gradle plugin, an opinionated module structure, a service‑locator "Scope" API, the presenter, and the renderer) into one unit whose **granularity of adoption is the whole framework**. Those couplings are *good engineering for a greenfield app or a bought‑in team* — they are not bad decisions. They simply make App Platform the wrong fit for a **conservative org adopting one piece at a time**. Syzygy's differentiator is not "less coupled = better"; it is **"the same ideas, refactored so each is separately adoptable."**

Do not reintroduce App Platform's couplings. Specifically: no required Gradle plugin to get value; no imposed module structure; no service‑locator scope; the `Model` contract must work on a bare `ViewModel` with zero other Syzygy pieces present.

### Circuit (the closest competitor — where we diverge deliberately)

Circuit (Slack) is a mature, production, KMP Compose architecture: a `Presenter` (`@Composable` returning UI state) plus a pure `Ui` (a `@Composable` of that state), unidirectional with event callbacks carried in the state, state retention via `rememberRetained`, and a global `Ui` registry. Circuit runs **one composition per screen** — the whole presenter tree shares one runtime — and leans on `rememberRetained` for per‑screen state.

Syzygy diverges on **one axis, deliberately, and you must preserve the divergence**: Syzygy runs **one Compose runtime per node** (not per screen), which buys **per‑node effect scoping** and **per‑node scoped DI graphs**. Circuit's authors considered per‑presenter runtimes and chose not to pay the cost (N recomposers). Syzygy makes the opposite bet because its target users want per‑feature scoped graphs with aligned lifetimes.

This means: **do not "optimize" Syzygy into one runtime per screen.** That would delete the differentiator and make it a Circuit clone. The N‑recomposer cost is a known, accepted trade, mitigated by the collection‑policy knob (below), not eliminated by collapsing runtimes.

Circuit is also the honest yardstick for "why not just use Circuit?" — the answer is per‑node scoped runtimes + aligned lifetime control, and that answer must remain true in the code.

---

## 3. The API decisions and their motivations

### `Model<Input, Output>` with `transform`

- **Why `@Composable (Input) -> Output`:** it is a pure, testable transformation. State lives in the Compose runtime, not in class properties, so a model is a function of its input — testable with Molecule + Turbine, no Android, no `ViewModel`.
- **Why the name `Model` (not `Presenter`, not `ComposableModel`):** "Presenter" carries MVP baggage and, paired with a "produces a ViewModel/Model" description, confused real users about which thing holds state. "Model" (as in the M of MVI — a thing that models the screen and emits state) is the honest role. The output is named `Output`, not `Model`/`State`, so producer and product never blur. The type is `Model`; the phrase "the model's projection" reads cleanly, which drove the final choice.
- **Why `Output` is a free type parameter, not a fixed `State`/`Model` type:** it keeps the primitive general — the same interface serves a screen, a sub‑component, or a pure derived‑value transform — and it makes the ViewModel confusion structurally impossible (there is no fixed output noun to collide with `ViewModel`).

### `Projection<Output>` with `project`

- **Why a separate consumer type:** Syzygy owns the **producer** side (model → output) and disowns the **consumer** side (output → UI). Owning the producer while disowning the consumer is the cut that keeps render complexity out of core.
- **Why the name `Projection` (not `Renderer`/`View`/`Ui`/`Display`):** those collide on import and read as Compose‑UI machinery (App Platform's own `Renderer` was judged a soft choice). "Projection" is accurate first (mapping an output onto a surface), celestial second (projecting the sky onto a plane — on‑theme with the name), and encodes the producer/consumer asymmetry (a projection is a derived *view onto* data it does not own). It is uncommon enough to avoid import clashes and not rendering‑flavored.
- **Why pure + global registry:** scoping the renderer map per‑graph creates an ambient‑graph‑vs‑owning‑graph mismatch that is genuinely hard. Making projections pure (scoped work done by the model, passed through the output, including callbacks) removes the need for a scoped map entirely, so a single global registry keyed by output type is correct — this is Circuit's bet and it scales precisely because UIs are injection‑free.
- Pairing: `Model<Input, Output>` and `Projection<Output>` are linked by the shared `Output` type parameter (compiler‑enforced), not by matching name prefixes. The mild name asymmetry is intentional: the model is framework‑flavored and core‑owned; the projection is a plain, portable, swappable contract.

### `syzygy()` and the aligned unit

- **Why Syzygy owns the `CoroutineScope`:** ownership is what lets Syzygy *guarantee* alignment. It creates the scope, decides its retire behavior, and runs the model on it — so it can promise "effects in `transform` run on this scope and die on retire" rather than hoping a caller wired a scope correctly.
- **Why Molecule is used internally (and is NOT optional):** `LaunchedEffect`/`rememberCoroutineScope` derive their coroutine context from the composition's recomposer, not from any scope in lexical reach. To make effects inside `transform` run on the **Syzygy** scope, the model must run in a composition whose recomposer is built from that scope — which is exactly what `scope.launchMolecule(...)` does. Molecule's role is *effect‑scope alignment*, not "producing a StateFlow." Removing Molecule breaks the central guarantee.
- **Why `RecompositionMode.Immediate`:** these are headless nodes with no vsync frame clock. Immediate mode recomposes on snapshot writes, needs no frame clock, and correctly prevents animation APIs from running inside models (animation belongs in projections, which have vsync). Do not use `ContextClock` for nodes.
- **Why runtime‑per‑node:** one composition has exactly one effect coroutine context. Per‑node effect scoping therefore *requires* per‑node recomposers. The "tree of scopes" becomes a "tree of Compose runtimes" by entailment, not by choice. (See Circuit divergence above.)
- **Why the `align` seam (`(scope, parent) -> Syzygy`):** DI is not owned by core; it is threaded through this hole. `align` hands the user the owned scope and the parent node, and the user builds whatever they want lifetime‑aligned — normally a Metro graph implementing `Syzygy`. The core never names Metro. This is what makes DI *enabled but not required* and keeps the core unopinionated.
- **Why parent discovery via `CompositionLocal`:** so nesting is automatic — a child resolves its parent from the enclosing runtime and extends its graph. The `parent as ConcreteGraph` cast is the visible, accepted cost of the core being graph‑agnostic (it cannot know the concrete graph type). Keep the cast explicit rather than hiding it; explicit‑over‑ambient is a standing bias.

### The `Lifetime` axis

- **Why one axis:** because the three lifetimes are aligned into one, there is only one decision — how long that unit lives. This is the design's headline feature ("control over the shared lifetime"), not a config detail. Surface it as a first‑class `lifetime` parameter so the power is visible at the call site.
- **Why `Retained` is the default:** it is the safe direction (a screen should survive rotation). `Remembered` silently resets on config change, so it must be the deliberate opt‑out, never the default. Flexibility with a safe default.
- **Why `Scoped(scope)` exists:** retention becomes an *input*, so the core does not depend on the experimental `retain` API and users are never boxed in (they can host on `viewModelScope`, an app scope, etc.). The `retain` integration is an adapter, not a core dependency.
- **Why process‑death is NOT on this axis:** every `Lifetime` is about in‑memory survival; none survive a process kill. Folding a "survives everything" option into the enum would conflate in‑memory retention with serialized restoration and mislead users. Keep them distinct — saveable state is an additive tier handled with `rememberSaveable` inside `transform`.

---

## 4. Module structure

Enforce these boundaries with actual module dependencies; do not let them blur.

- **`syzygy-core`** — `Model`, `Projection`, `Syzygy`, `syzygy()`, `SyzygyContent`, the holder/engine, `Lifetime`, Molecule hosting. **No Android imports.** (Keeps KMP reachable without paying KMP tax now.)
- **`syzygy-projection`** — the projection registry, `Project(output)` ambient lookup, `@OutputKey` and the multibinding glue.
- **`syzygy-nav3`** — the real integration work: a Nav3 `NavEntryDecorator` (or equivalent) that hosts one aligned unit per back‑stack entry and ties its retention to entry lifecycle (kept on stack, retired on pop). This is where Android and navigation live.
- **`sample`** — the demo app (below). Ships a reference `Projection` set; the core never depends on it.

---

## 5. Build order (milestones)

Build in this order. Each milestone is independently demoable; do not skip ahead.

1. **Core engine, single screen, no nav, no Metro — validated in a plain JVM test.** Prove: a `Model` runs as a Molecule loop on an owned scope; a `LaunchedEffect` in `transform` runs on that scope; retire cancels the scope. Retention is exercised by driving the retain scope's keep/retire directly (there is no device to rotate in a JVM test) — assert that a kept‑then‑re‑entered node preserves state and a running scope, and that `Lifetime.Remembered` discards on exit. True device rotation is an integration test in a later milestone; the *behavior* is provable headless here. This is the heart — get it right, in `src/test`, before anything else.
2. **Metro through `align`, still one screen.** Prove: `align` builds a `@DependencyGraph` on the owned scope; the model is `@Inject`‑constructed; `coroutineScope` is backed by the factory `@Provides`. Proves DI‑per‑node.
3. **`syzygy-projection` + one reference projection.** Prove: output → UI via a global registry keyed by output type; recursive `Project(childOutput)`.
4. **`syzygy-nav3` + a 3‑level flow (`Home → Profile → Settings`).** Prove: each destination is a node whose graph **extends its parent's** (reaches parent bindings, adds its own); push/pop/rotate visibly preserves and tears down per‑entry state. **This is the thesis‑proving milestone** — nesting across real navigation. Everything before is scaffolding for it.
5. **Nesting variants + effect‑survival demo.** Show both model‑nesting (parent model embeds child output via `syzygy()`) and UI‑nesting (parent projection calls `SyzygyContent` for a child), and a `LaunchedEffect` that survives rotation and cancels exactly on pop. These are the details a skeptic probes.

**Deferred, as roadmap not gaps** (do not build now; leave as named seams): multi‑back‑stack (bottom‑nav), process‑death restoration (saveable tier), retain‑free `ViewModel` hosting adapter.

---

## 6. Disciplines that must hold (or the design fails)

1. **Core imports no platform types.** Enforce with a dependency rule (fail the build if `syzygy-core` gains any `android.*`/`androidx.*` non‑Compose‑runtime dependency, or any other platform‑specific one). The core depends only on multiplatform libraries. This protects three things at once: the primitive's purity, the ability to run a full tree headless in a plain unit test, and multiplatform reach (Android is one target, not the core's identity). The headless‑testability is the most concrete daily payoff — treat any platform leak into core as breaking the test story, not just an abstract purity violation.
2. **The Nav3 leak test.** When `syzygy-nav3` needs something `syzygy-core` doesn't expose, ask: *would a non‑Nav3 back‑stack integration need the same thing?* If yes → it's a general primitive, add to core. If only Nav3 wants it → it stays in `syzygy-nav3`, even at the cost of a less clean integration. This is what keeps navigation assumptions out of a navigation‑agnostic core, and it is the discipline most likely to erode while chasing a good demo.
3. **Explicit over ambient at layer boundaries.** Any convenience that couples two primitives (effect‑scope alignment, parent discovery) is either an explicit parameter or a documented seam — never silent, ambient magic. Bias to explicit whenever the two conflict (e.g. keep the `parent as Graph` cast visible).
4. **Do not collapse runtime‑per‑node into one‑per‑screen.** That deletes the differentiator (see Circuit). The N‑recomposer cost is accepted and mitigated by collection policy, not removed by merging runtimes.
5. **Do not reintroduce App Platform's couplings.** No required Gradle plugin for basic value; no imposed module layout; no service‑locator scope. The `Model` must be usable on a plain `ViewModel` alone. Protect the incremental‑adoption path as a first‑class, tested configuration — it is the product's reason to exist for a conservative org.
6. **The Context‑free rule.** A dependency graph is the app's likeliest holder of a `Context`, and Syzygy retains graphs by construction. Retaining a `Context` (or anything transitively referencing one) is a leak. Provide a lint check that flags a retained graph whose transitive bindings reach `Context`/`View` — a compile error, not a wiki page. This is required infrastructure, not a nicety.

---

## 7. Known cost model & mitigations (have honest answers ready)

- **N recomposers for an N‑deep stack.** Each node is a headless Molecule loop: recomposition + a recomposer coroutine + a slot table. No layout/draw cost (headless). Cheap per unit, real in aggregate; compounds with backgrounded loops. **Benchmark this early** on a deep stack — it is the question the sharpest reviewer asks, and "we measured it, here's the number and the pause knob" is the only credible answer.
- **Backgrounded loops.** A kept‑but‑backgrounded node keeps running its `transform` and effects. Mitigation: a **collection policy** knob — run‑while‑retained (keep warm) vs a `WhileSubscribed`‑style pause‑on‑background. This knob lives on the owned scope (scope lifecycle is owned). Decide its default deliberately; it is the one knob with real behavioral teeth.
- **Unchecked cast in projection lookup.** The output‑type‑keyed registry is type‑safe to populate, unsafe to read; centralize the single `@Suppress` in the `Project` lookup, provably safe because the key is the output's class.
- **Missing projection = runtime failure.** The registry assembles at compile time but does not prove every emitted output has a projection. Ship a test template that enumerates output types and asserts a registered projection for each, plus a clear error message.

---

## 8. Open decisions (resolve during build, with guidance)

- **Collection policy default** — run‑while‑retained vs pause‑on‑background. Guidance: default to pause‑on‑background (`WhileSubscribed`) unless a concrete need for warm background loops appears; it caps the deep‑stack cost. Make it overridable per node.
- **Parent discovery ergonomics** — the automatic `CompositionLocal` route needs a small internal capture; the explicit‑parent route is cleaner internally but makes every child name its parent. Guidance: prototype both on one nested screen; prefer the route that keeps the `Model` author's code simplest, since that is the most‑written surface.
- **`Model` name length vs genericness** — `Model` is generic and may collide in codebases with existing `Model` types. Guidance: keep `Model`, disambiguate by package if a real clash appears; do not re‑qualify the concept.
- **Name of the aligned‑unit / host type** — currently `Syzygy<Input, Output>`. A tree‑node framing (`Node` / `SyzygyNode`) is defensible for this host type specifically (it *is* a tree position, and it is framework‑owned so framework‑qualification is honest). Decide during the nav milestone, when the tree becomes concrete.

---

## 9. Definition (for docs, keep verbatim)

> **A syzygy is the alignment of three or more celestial bodies.** Syzygy aligns the lifetimes of a `CoroutineScope`, a Compose runtime, and a `DependencyGraph` into one you control — so scope, runtime, and graph live and die together, on your terms. And Syzygies compose: each aligns to its parent, forming a tree of scoped runtimes shaped to your app.

Every design decision above should be checkable against that sentence. If a change makes the sentence less true, it is the wrong change.
