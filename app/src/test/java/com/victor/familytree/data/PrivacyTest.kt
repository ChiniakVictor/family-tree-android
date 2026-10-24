package com.victor.familytree.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyTest {
    private fun f(d: String?) = d?.let { Fact(it, null, null, null, emptyList(), emptyList(), emptyList()) }
    private val recent = (java.time.Year.now().value - 40).toString()

    @Test fun flaggedLivingStaysLiving() = assertTrue(FamilyParser.isLiving(true, f("1850"), f("1900"), null))
    @Test fun recentBirthWithoutDeathIsLiving() = assertTrue(FamilyParser.isLiving(false, f(recent), null, null))
    @Test fun recentBirthWithDeathIsNotLiving() = assertFalse(FamilyParser.isLiving(false, f(recent), f("2020"), null))
    @Test fun oldBirthIsNotLiving() = assertFalse(FamilyParser.isLiving(false, f("1880"), null, null))
    @Test fun noDatesIsNotForcedLiving() = assertFalse(FamilyParser.isLiving(null, null, null, null))
}
