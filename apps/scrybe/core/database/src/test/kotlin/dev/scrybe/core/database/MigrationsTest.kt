package dev.scrybe.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationsTest {
    @Test
    fun `MIGRATION_4_5 migrates from version 4 to version 5`() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }
}
