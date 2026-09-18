package com.twobits.core.localmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants over the model catalog itself. Every entry here describes a multi-gigabyte native
 * model bundle that is downloaded once and then handed straight to a native runtime, where a
 * mismatch is answered with a process abort rather than an exception — so the cheapest place to
 * catch a wrong constant is right here, statically, instead of on a user's device.
 */
class LocalLlmModelCatalogTest {
    @Test
    fun `every model declares a non-blank identity`() {
        LocalLlmModel.entries.forEach { model ->
            assertTrue("${model.name} displayName", model.displayName.isNotBlank())
            assertTrue("${model.name} description", model.description.isNotBlank())
            assertTrue("${model.name} fileName", model.fileName.isNotBlank())
            assertTrue("${model.name} sizeLabel", model.sizeLabel.isNotBlank())
        }
    }

    @Test
    fun `no two models share a file name`() {
        val byFileName = LocalLlmModel.entries.groupBy { it.fileName }.filterValues { it.size > 1 }
        assertEquals("models sharing a download file name would overwrite each other", emptyMap<String, Any>(), byFileName)
    }

    /** A URL pointing at a different bundle than [LocalLlmModel.fileName] installs the wrong weights. */
    @Test
    fun `each download url ends with that model's own file name`() {
        LocalLlmModel.entries.forEach { model ->
            assertTrue(
                "${model.name}: downloadUrl ${model.downloadUrl} does not end with ${model.fileName}",
                model.downloadUrl.endsWith(model.fileName),
            )
        }
    }

    @Test
    fun `a declared sha256 is a full lowercase hex digest`() {
        LocalLlmModel.entries
            .mapNotNull { model -> model.sha256?.let { model to it } }
            .forEach { (model, digest) ->
                assertTrue("${model.name}: sha256 must be 64 hex chars, was ${digest.length}", digest.length == HEX_DIGEST_LENGTH)
                assertTrue("${model.name}: sha256 must be lowercase hex", digest.all { it in '0'..'9' || it in 'a'..'f' })
            }
    }

    /**
     * The regression this catches: Qwen 3 0.6B ships as `qwen3_0.6b_q4_block32_ekv1280.litertlm`,
     * whose `ekv1280` states a 1280-token KV cache, while every model was being asked for 4096 —
     * a mismatch the native runtime answers by aborting the process. Where a bundle states its own
     * limit in its file name, the catalog may not claim more.
     */
    @Test
    fun `a model never claims more context than its own file name states`() {
        val ekv = Regex("""ekv(\d+)""")
        LocalLlmModel.entries.forEach { model ->
            val stated =
                ekv
                    .find(model.fileName)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt() ?: return@forEach
            assertTrue(
                "${model.name}: file name states ekv$stated but maxContextTokens is ${model.maxContextTokens}",
                model.maxContextTokens <= stated,
            )
        }
    }

    @Test
    fun `every model declares a positive context budget`() {
        LocalLlmModel.entries.forEach { model ->
            assertTrue("${model.name} maxContextTokens", model.maxContextTokens > 0)
        }
    }

    private companion object {
        const val HEX_DIGEST_LENGTH = 64
    }
}
