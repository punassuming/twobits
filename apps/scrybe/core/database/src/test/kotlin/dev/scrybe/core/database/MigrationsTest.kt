package dev.scrybe.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationsTest {
    @Test
    fun `MIGRATION_4_5 migrates from version 4 to version 5`() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }

    @Test
    fun `MIGRATION_16_17 migrates from version 16 to version 17`() {
        assertEquals(16, MIGRATION_16_17.startVersion)
        assertEquals(17, MIGRATION_16_17.endVersion)
    }
}
