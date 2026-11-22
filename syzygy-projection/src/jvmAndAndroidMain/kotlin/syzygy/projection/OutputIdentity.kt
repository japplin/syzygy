package syzygy.projection

import java.lang.ref.WeakReference

internal actual fun outputIdentity(output: Any): OutputIdentity = WeakOutputIdentity(output)

private class WeakOutputIdentity(output: Any) : OutputIdentity {
    private val reference = WeakReference(output)
    private val identityHash = System.identityHashCode(output)

    override val isCollected: Boolean get() = reference.get() == null

    override fun hashCode(): Int = identityHash

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is WeakOutputIdentity && reference.get()?.let { it === other.reference.get() } == true)
}
