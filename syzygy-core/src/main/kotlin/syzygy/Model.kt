package syzygy

import androidx.compose.runtime.Composable

/**
 * A composable transformation from an [Input] to an [Output].
 *
 * `transform` is `@Composable`, so a model can hold state (`remember`), run effects
 * (`LaunchedEffect`), and call other models — models compose. A model holds **no** state in class
 * properties; all state lives in the Compose runtime that runs it, which makes it a pure function of
 * its input, testable in isolation with Molecule + Turbine — no `ViewModel`, no Android.
 *
 * The output is a free type parameter (not a fixed `State`/`Model` noun) so the same primitive serves
 * a screen, a sub-component, or a pure derived-value transform, and so it can never collide with
 * `ViewModel`. See IMPLEMENTATION_PLAN §3.
 */
interface Model<Input, Output> {
    @Composable
    fun transform(input: Input): Output
}
