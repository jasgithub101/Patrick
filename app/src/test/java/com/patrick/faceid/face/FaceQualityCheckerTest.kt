package com.patrick.faceid.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityCheckerTest {

    private val checker = FaceQualityChecker()
    private val sharp = TestImages.noisy(200, 200)

    private fun rejection(result: QualityAssessment): QualityAssessment.Rejected {
        assertTrue("expected a rejection but got $result", result is QualityAssessment.Rejected)
        return result as QualityAssessment.Rejected
    }

    @Test fun noFaceIsRejectedWithGuidance() {
        val r = rejection(checker.assess(sharp, emptyList()))
        assertEquals(QualityIssue.NO_FACE, r.issue)
        assertTrue("message should tell the operator what to do", r.message.contains("Point the camera"))
    }

    @Test fun twoComparablySizedFacesAreRejectedAsAmbiguous() {
        // Both people are presenting to the camera, so the system must not guess which is meant.
        val faces = listOf(
            TestImages.face(Box(20f, 20f, 120f, 140f), interOcularPx = 50f),
            TestImages.face(Box(130f, 20f, 225f, 135f), interOcularPx = 50f),
        )
        val r = rejection(checker.assess(sharp, faces))
        assertEquals(QualityIssue.MULTIPLE_FACES, r.issue)
        assertTrue("message should say how many were seen", r.message.contains("2"))
    }

    @Test fun aSmallBystanderInTheBackgroundDoesNotBlockRecognition() {
        // Measured on LFW (report E3): 14 percent of ordinary photos contain a background face.
        // Rejecting those would make the app unusable, so only a comparably large second face counts.
        val subject = TestImages.face(Box(20f, 20f, 120f, 140f), interOcularPx = 50f)
        val bystander = TestImages.face(Box(150f, 30f, 175f, 60f), interOcularPx = 12f)
        val result = checker.assess(sharp, listOf(subject, bystander))
        assertTrue("a distant bystander must be ignored, got $result", result is QualityAssessment.Accepted)
    }

    @Test fun theLargestFaceIsTreatedAsTheSubject() {
        val small = TestImages.face(Box(150f, 30f, 175f, 60f), interOcularPx = 12f)
        val large = TestImages.face(Box(20f, 20f, 120f, 140f), interOcularPx = 50f)
        // Order reversed: the subject must be chosen by prominence, not by list position.
        val accepted = checker.assess(sharp, listOf(small, large)) as QualityAssessment.Accepted
        assertEquals(50f, accepted.face.interOcularDistance, 1f)
    }

    @Test fun theAmbiguityRatioIsConfigurable() {
        // Subject area 100x120 = 12000; second face 55x55 = 3025, a ratio of about 0.25. That is
        // below the 0.5 default (accepted) but above a strict 0.1 (rejected).
        val subject = TestImages.face(Box(20f, 20f, 120f, 140f), interOcularPx = 50f)
        val second = TestImages.face(Box(150f, 30f, 205f, 85f), interOcularPx = 20f)
        val faces = listOf(subject, second)

        assertTrue(
            "the default should treat it as a bystander",
            checker.assess(sharp, faces) is QualityAssessment.Accepted,
        )
        val strict = FaceQualityChecker(QualityThresholds(maxSecondFaceAreaRatio = 0.1f))
        assertEquals(QualityIssue.MULTIPLE_FACES, rejection(strict.assess(sharp, faces)).issue)
    }

    @Test fun missingLandmarksAreRejected() {
        val r = rejection(checker.assess(sharp, listOf(TestImages.face(landmarksValid = false))))
        assertEquals(QualityIssue.LANDMARKS_MISSING, r.issue)
    }

    @Test fun aFaceTooFarAwayIsRejected() {
        val r = rejection(checker.assess(sharp, listOf(TestImages.face(interOcularPx = 8f))))
        assertEquals(QualityIssue.FACE_TOO_SMALL, r.issue)
        assertTrue(r.message.contains("Move closer"))
        assertEquals(8f, r.metrics!!.interOcularPx, 0.5f)
    }

    @Test fun aFlatImageWithNoTextureIsRejectedAsBlurred() {
        val r = rejection(checker.assess(TestImages.flat(200, 200, 128), listOf(TestImages.face())))
        assertEquals(QualityIssue.TOO_BLURRY, r.issue)
        assertEquals("a flat image has zero Laplacian variance", 0f, r.metrics!!.blurVariance, 1e-3f)
    }

    @Test fun aSmoothGradientIsAlsoRejectedAsBlurred() {
        val r = rejection(checker.assess(TestImages.smooth(200, 200), listOf(TestImages.face())))
        assertEquals(QualityIssue.TOO_BLURRY, r.issue)
    }

    @Test fun aDarkImageIsRejected() {
        val dark = TestImages.noisy(200, 200, mean = 12, amplitude = 10)
        val r = rejection(checker.assess(dark, listOf(TestImages.face())))
        assertEquals(QualityIssue.TOO_DARK, r.issue)
        assertTrue(r.message.contains("brighter"))
    }

    @Test fun aBlownOutImageIsRejected() {
        val bright = TestImages.noisy(200, 200, mean = 252, amplitude = 3)
        val r = rejection(checker.assess(bright, listOf(TestImages.face())))
        assertEquals(QualityIssue.TOO_BRIGHT, r.issue)
    }

    @Test fun aSharpWellLitFaceIsAccepted() {
        val result = checker.assess(sharp, listOf(TestImages.face(interOcularPx = 50f)))
        assertTrue("expected acceptance but got $result", result is QualityAssessment.Accepted)
        val accepted = result as QualityAssessment.Accepted
        assertTrue("quality should be usable, was ${accepted.score}", accepted.score > 0.35f)
        assertEquals(1, accepted.metrics.facesDetected)
    }

    @Test fun headTurnedTooFarIsRejected() {
        val r = rejection(checker.assess(sharp, listOf(TestImages.face(interOcularPx = 50f, yaw = 60f))))
        assertEquals(QualityIssue.EXTREME_POSE, r.issue)
        assertTrue(r.message.contains("turned too far"))
    }

    @Test fun headTiltedTooFarIsRejectedWithItsOwnMessage() {
        val r = rejection(checker.assess(sharp, listOf(TestImages.face(interOcularPx = 50f, pitch = 50f))))
        assertEquals(QualityIssue.EXTREME_POSE, r.issue)
        assertTrue(r.message.contains("up or down"))
    }

    @Test fun aSlightPoseIsStillAccepted() {
        val result = checker.assess(sharp, listOf(TestImages.face(interOcularPx = 50f, yaw = 12f)))
        assertTrue(result is QualityAssessment.Accepted)
    }

    @Test fun poseIsNotGatedWhenTheDetectorReportsNoAngles() {
        val faceWithoutAngles = TestImages.face(interOcularPx = 50f, yaw = null, pitch = null, roll = null)
        val result = checker.assess(sharp, listOf(faceWithoutAngles))
        assertTrue("a detector without head angles must not be penalised", result is QualityAssessment.Accepted)
    }

    @Test fun aLargerSharperFaceScoresHigherThanASmallerOne() {
        fun score(px: Float) =
            (checker.assess(sharp, listOf(TestImages.face(interOcularPx = px))) as QualityAssessment.Accepted).score
        assertTrue("bigger face should score at least as high", score(70f) >= score(30f))
    }

    @Test fun theWeakestFactorLimitsTheScore() {
        // Sharp and well lit, but the face is barely above the size floor, so the score must be low.
        val result = checker.assess(sharp, listOf(TestImages.face(interOcularPx = 20f)))
        val accepted = result as QualityAssessment.Accepted
        assertTrue("score should be dragged down by size, was ${accepted.score}", accepted.score < 0.2f)
    }

    @Test fun thresholdsAreConfigurable() {
        val strict = FaceQualityChecker(QualityThresholds(minInterOcularPx = 100f))
        val r = rejection(strict.assess(sharp, listOf(TestImages.face(interOcularPx = 50f))))
        assertEquals(QualityIssue.FACE_TOO_SMALL, r.issue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun contradictoryLuminanceThresholdsAreRejected() {
        QualityThresholds(minMeanLuminance = 200f, maxMeanLuminance = 100f)
    }
}
