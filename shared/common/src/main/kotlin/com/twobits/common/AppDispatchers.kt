package com.twobits.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AppDispatcher(val dispatcher: AppDispatchers)

enum class AppDispatchers {
    Default,
    IO,
}
