package com.example.wppsticker.ui.videoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedExportCandidatePlannerTest {
    @Test
    fun shortBalancedClipKeepsSourceFrameRateAndStartsWithOneStrongCandidate() {
        val candidates = planAnimatedEncodingCandidates(
            profile = AnimatedExportProfile.BALANCED,
            sourceFps = 30f,
            durationMs = 1_000L
        )

        assertEquals(AnimatedEncodingSettings(fps = 30, quality = 82), candidates.first())
        assertEquals(3, candidates.size)
        assertTrue(candidates.zipWithNext().all { (left, right) -> right.fps <= left.fps && right.quality < left.quality })
    }

    @Test
    fun longClipReducesFramesBeforeEncodingToStayWithinTheProfileBudget() {
        val candidates = planAnimatedEncodingCandidates(
            profile = AnimatedExportProfile.BALANCED,
            sourceFps = 30f,
            durationMs = 10_000L
        )

        assertEquals(12, candidates.first().fps)
        assertTrue(candidates.size <= 3)
        assertTrue(candidates.all { it.fps >= 8 && it.quality >= 40 })
    }

    @Test
    fun shortSourceBelowTargetRateIsNotUpsampled() {
        val candidates = planAnimatedEncodingCandidates(
            profile = AnimatedExportProfile.FLUID,
            sourceFps = 12f,
            durationMs = 1_000L
        )

        assertEquals(12, candidates.first().fps)
    }
}
