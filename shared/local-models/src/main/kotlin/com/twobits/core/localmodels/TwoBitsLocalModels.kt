package com.twobits.core.localmodels

/**
 * Thin registry helper over an app-supplied list of [LocalModelSpec]s. Most of the inventory of
 * concrete models is deliberately app-owned (Scrybe ships [LocalWhisperModel]; historically each
 * app tracked its own Gemma spec too) — centralizing it would force apps to depend on model
 * families they do not use. [LocalLlmModel] is the deliberate exception: Scrybe, Shelf Snap, and
 * PriceDrop all want the literal same on-device text model, so it lives here instead of being
 * duplicated three times with URLs/hashes that could drift out of sync. This object only
 * filters/queries whatever the app passes in.
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
