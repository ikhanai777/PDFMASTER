package com.pdfmaster.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRangeTest {

    @Test fun singlePagesAndRanges() {
        assertEquals(listOf(0, 1, 2, 4), PageRange.parse("1-3, 5", 10))
    }

    @Test fun openEndedRanges() {
        assertEquals(listOf(7, 8, 9), PageRange.parse("8-", 10))
        assertEquals(listOf(0, 1, 2), PageRange.parse("-3", 10))
    }

    @Test fun groupsKeepOrderAndSeparation() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5, 6, 7, 8, 9)), PageRange.parseGroups("1-3; 4-", 10))
    }

    @Test fun duplicatesAreRemovedWhenFlattening() {
        assertEquals(listOf(0, 1, 2), PageRange.parse("1-3, 2", 5))
    }

    @Test fun extractPagesThreeToSeven() {
        assertEquals(listOf(2, 3, 4, 5, 6), PageRange.parse("3-7", 12))
    }

    @Test(expected = PageRange.ParseException::class) fun outOfRangeFails() {
        PageRange.parse("1-11", 10)
    }

    @Test(expected = PageRange.ParseException::class) fun backwardsRangeFails() {
        PageRange.parse("5-2", 10)
    }

    @Test(expected = PageRange.ParseException::class) fun garbageFails() {
        PageRange.parse("abc", 10)
    }

    @Test(expected = PageRange.ParseException::class) fun emptyFails() {
        PageRange.parse(" , ", 10)
    }

    @Test fun everyN() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), PageRange.everyN(5, 2))
    }

    @Test fun fromStartPointsAlwaysStartsAtZero() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3, 4), listOf(5)), PageRange.fromStartPoints(6, listOf(5, 2)))
    }
}
