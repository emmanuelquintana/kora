package com.emmanuelquintana.kora.core.foldable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureResolverTest {

    private fun hinge(horizontal: Boolean, halfOpened: Boolean) = HingeSnapshot(
        isHorizontal = horizontal,
        isHalfOpened = halfOpened,
        isSeparating = halfOpened,
        isOccluding = false,
        bounds = if (horizontal) PxRect(0, 1100, 1800, 1100) else PxRect(900, 0, 900, 2200),
    )

    @Test
    fun halfOpenedHorizontalHingeIsTabletopRegardlessOfWidth() {
        val tabletop = hinge(horizontal = true, halfOpened = true)
        assertEquals(KoraPosture.Tabletop(tabletop), PostureResolver.resolve(WidthClass.EXPANDED, listOf(tabletop)))
        assertEquals(KoraPosture.Tabletop(tabletop), PostureResolver.resolve(WidthClass.MEDIUM, listOf(tabletop)))
    }

    @Test
    fun halfOpenedVerticalHingeIsBook() {
        val book = hinge(horizontal = false, halfOpened = true)
        assertEquals(KoraPosture.Book(book), PostureResolver.resolve(WidthClass.EXPANDED, listOf(book)))
    }

    @Test
    fun flatUnfoldedScreenIsExpandedAndKeepsTheHinge() {
        val flat = hinge(horizontal = false, halfOpened = false)
        assertEquals(KoraPosture.Expanded(flat), PostureResolver.resolve(WidthClass.EXPANDED, listOf(flat)))
    }

    @Test
    fun widthAloneNeverImpliesAFoldablePosture() {
        assertEquals(KoraPosture.Expanded(null), PostureResolver.resolve(WidthClass.EXPANDED, emptyList()))
        assertEquals(KoraPosture.Medium, PostureResolver.resolve(WidthClass.MEDIUM, emptyList()))
        assertEquals(KoraPosture.Compact, PostureResolver.resolve(WidthClass.COMPACT, emptyList()))
    }

    @Test
    fun coverScreenWithoutHingeIsCompact() {
        assertTrue(PostureResolver.resolve(WidthClass.COMPACT, emptyList()) is KoraPosture.Compact)
    }
}
