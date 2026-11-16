package syzygy

import kotlinx.coroutines.CoroutineScope

/**
 * How long an aligned unit lives — the single policy axis.
 *
 * Because a `CoroutineScope`, a Compose runtime, and a `DependencyGraph` are aligned into one, there
 * is exactly one decision to make: how long that unit lives. This is the design's headline feature,
 * surfaced as a first-class parameter on [syzygy] so the power is visible at the call site.
 *
 * > Process death is orthogonal to this axis. Every [Lifetime] controls in-memory survival only; none
 * > survive a low-memory process kill. Serialized restoration is a separate, additive tier
 * > (`rememberSaveable` inside `transform`). See IMPLEMENTATION_PLAN §3.
 */
sealed interface Lifetime {
    /** Survives configuration change; retires on removal. The safe default — a screen should survive rotation. */
    data object Retained : Lifetime

    /** Dies on composition exit, including rotation. The deliberate opt-out for disposable units. */
    data object Remembered : Lifetime

    /** Kept while on the back stack; retired on pop. Driven by the `syzygy-nav3` integration. */
    data object NavEntry : Lifetime

    /**
     * Lives exactly as long as [scope]. Retention becomes an input rather than a policy, so the core
     * never depends on any experimental `retain` API — hand in `viewModelScope`, an app-level scope,
     * or anything else.
     */
    data class Scoped(val scope: CoroutineScope) : Lifetime
}
