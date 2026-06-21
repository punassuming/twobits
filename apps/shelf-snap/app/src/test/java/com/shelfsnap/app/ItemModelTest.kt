package com.shelfsnap.app

import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import org.junit.Assert.*
import org.junit.Test

class ItemModelTest {
    @Test
    fun `Item defaults to draft`() {
        val item = Item()
        assertTrue(item.isDraft)
    }

    @Test
    fun `Item default condition is GOOD`() {
        val item = Item()
        assertEquals(Condition.GOOD, item.condition)
    }

    @Test
    fun `Item default estimated value is zero`() {
        val item = Item()
        assertEquals(0.0, item.estimatedValue, 0.001)
    }

    @Test
    fun `Item can hold multiple photo paths`() {
        val paths = listOf("/data/path1.jpg", "/data/path2.jpg", "/data/path3.jpg")
        val item = Item(photoPaths = paths)
        assertEquals(3, item.photoPaths.size)
        assertEquals(paths, item.photoPaths)
    }

    @Test
    fun `Condition enum has expected values`() {
        val conditions = Condition.entries.map { it.name }
        assertTrue(conditions.contains("EXCELLENT"))
        assertTrue(conditions.contains("GOOD"))
        assertTrue(conditions.contains("FAIR"))
        assertTrue(conditions.contains("POOR"))
    }

    @Test
    fun `Item copy preserves all fields`() {
        val original =
            Item(
                id = 42L,
                category = "Books",
                description = "Kotlin book",
                condition = Condition.EXCELLENT,
                estimatedValue = 15.00,
                confidencePercent = 87,
                isDraft = false,
                photoPaths = listOf("/a.jpg"),
            )
        val copy = original.copy(category = "Updated")
        assertEquals("Updated", copy.category)
        assertEquals(42L, copy.id)
        assertEquals(Condition.EXCELLENT, copy.condition)
        assertEquals(15.00, copy.estimatedValue, 0.001)
        assertEquals(87, copy.confidencePercent)
        assertFalse(copy.isDraft)
    }

    @Test
    fun `total estimated value across items sums correctly`() {
        val items =
            listOf(
                Item(estimatedValue = 10.00),
                Item(estimatedValue = 25.50),
                Item(estimatedValue = 5.75),
            )
        val total = items.sumOf { it.estimatedValue }
        assertEquals(41.25, total, 0.001)
    }

    @Test
    fun `totals update correctly when item is removed`() {
        val items =
            mutableListOf(
                Item(id = 1L, estimatedValue = 10.00),
                Item(id = 2L, estimatedValue = 20.00),
                Item(id = 3L, estimatedValue = 5.00),
            )
        items.removeAll { it.id == 2L }
        val total = items.sumOf { it.estimatedValue }
        assertEquals(15.00, total, 0.001)
    }

    @Test
    fun `totals update correctly when item value is edited`() {
        val items =
            listOf(
                Item(id = 1L, estimatedValue = 10.00),
                Item(id = 2L, estimatedValue = 20.00),
            )
        // Simulate edit: update item 2's value to 30.00
        val updated = items.map { if (it.id == 2L) it.copy(estimatedValue = 30.00) else it }
        val total = updated.sumOf { it.estimatedValue }
        assertEquals(40.00, total, 0.001)
    }

    @Test
    fun `search filter works on category`() {
        val items =
            listOf(
                Item(id = 1L, category = "Books"),
                Item(id = 2L, category = "Electronics"),
                Item(id = 3L, category = "Clothing"),
            )
        val filtered = items.filter { it.category.contains("Electronics", ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals(2L, filtered.first().id)
    }

    @Test
    fun `search filter works on description`() {
        val items =
            listOf(
                Item(id = 1L, description = "Red wool scarf"),
                Item(id = 2L, description = "Blue denim jacket"),
                Item(id = 3L, description = "Wool blanket"),
            )
        val filtered = items.filter { it.description.contains("wool", ignoreCase = true) }
        assertEquals(2, filtered.size)
    }
}
