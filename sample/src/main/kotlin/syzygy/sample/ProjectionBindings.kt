package syzygy.sample

import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

@MapKey
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ProjectionKey(val value: KClass<*>)
