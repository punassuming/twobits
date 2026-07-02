package com.twobits.core.localmodels

/**
 * Thin registry helper over an app-supplied list of [LocalModelSpec]s. The inventory of concrete
 * models is deliberately app-owned (Scrybe ships Whisper + Gemma; Shelf Snap ships Moondream +
 * Gemma; PriceDrop ships none) — centralizing it would force apps to depend on model families they
 * do not use. This object only filters/queries whatever the app passes in.
 */
object TwoBitsLocalModels {
    /** All specs belonging to [family], preserving input order. */
    fun specsFor(
        family: LocalModelFamily,
        all: List<LocalModelSpec>,
    ): List<LocalModelSpec> = all.filter { it.family == family }

    /** All specs for a given [task], preserving input order. */
    fun specsForTask(
        task: LocalModelTask,
        all: List<LocalModelSpec>,
    ): List<LocalModelSpec> = all.filter { it.task == task }
}
