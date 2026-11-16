package syzygy.projection

import androidx.compose.runtime.Composable
import syzygy.Projection
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/**
 * The registry assembles at compile time but does not prove every emitted output has a projection.
 * This is the test template from IMPLEMENTATION_PLAN §7: look up by output type, enumerate the output
 * types an app can emit and assert each has a projection, and check the missing-projection message.
 */
class ProjectionRegistryTest {

    data class HomeState(val greeting: String)
    data class ProfileState(val displayName: String)

    class HomeProjection : Projection<HomeState> {
        @Composable override fun project(output: HomeState) = Unit
    }

    class ProfileProjection : Projection<ProfileState> {
        @Composable override fun project(output: ProfileState) = Unit
    }

    private val registry: ProjectionRegistry = mapOf(
        HomeState::class to HomeProjection(),
        ProfileState::class to ProfileProjection(),
    )

    @Test
    fun looksUpTheProjectionByOutputType() {
        assertTrue(registry.projectionFor(HomeState("hi")) is HomeProjection)
        assertTrue(registry.projectionFor(ProfileState("Ada")) is ProfileProjection)
    }

    /** The template to copy per app: enumerate every output type and assert a projection is registered. */
    @Test
    fun everyOutputTypeHasARegisteredProjection() {
        val emittedOutputs: List<KClass<*>> = listOf(HomeState::class, ProfileState::class)
        emittedOutputs.forEach { output ->
            assertNotNull("no Projection registered for $output", registry[output])
        }
    }

    @Test
    fun missingProjectionFailsWithAClearMessage() {
        data class Orphan(val x: Int)
        val error = assertThrows(IllegalStateException::class.java) {
            registry.projectionFor(Orphan(1))
        }
        assertTrue(error.message!!.contains("No Projection registered"))
    }
}
