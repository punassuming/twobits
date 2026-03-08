package dev.scrybe.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val scrybeDispatchers: ScrybeDispatchers)

enum class ScrybeDispatchers {
    Default,
    IO,
}
