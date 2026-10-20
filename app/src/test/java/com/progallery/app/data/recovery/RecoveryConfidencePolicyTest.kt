package com.progallery.app.data.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scan must never overstate what it knows: an extension alone is not proof,
 * and a file that cannot be read is not offered as recoverable.
 */
class RecoveryConfidencePolicyTest {

    @Test
    fun signatureIdentificationIsHighConfidenceAndAvailable() {
        val judgement = RecoveryConfidencePolicy.evaluate(
            identifiedBySignature = true,
            hasExtension = false,
            hasDeclaredMime = false,
            isReadable = true,
        )
        assertEquals(RecoveryConfidence.HIGH, judgement.confidence)
        assertEquals(RecoveryStatus.AVAILABLE, judgement.status)
    }

    @Test
    fun anExtensionAloneIsOnlyMediumConfidence() {
        val judgement = RecoveryConfidencePolicy.evaluate(
            identifiedBySignature = false,
            hasExtension = true,
            hasDeclaredMime = false,
            isReadable = true,
        )
        assertEquals(RecoveryConfidence.MEDIUM, judgement.confidence)
        assertEquals(RecoveryStatus.AVAILABLE, judgement.status)
    }

    @Test
    fun typePlusExtensionIsStillOnlyMedium() {
        val judgement = RecoveryConfidencePolicy.evaluate(
            identifiedBySignature = false,
            hasExtension = true,
            hasDeclaredMime = true,
            isReadable = true,
        )
        assertEquals(RecoveryConfidence.MEDIUM, judgement.confidence)
    }

    @Test
    fun noExtensionAndNoSignatureIsLowConfidence() {
        val judgement = RecoveryConfidencePolicy.evaluate(
            identifiedBySignature = false,
            hasExtension = false,
            hasDeclaredMime = false,
            isReadable = true,
        )
        assertEquals(RecoveryConfidence.LOW, judgement.confidence)
    }

    @Test
    fun anUnreadableFileIsNeverOfferedAsAvailable() {
        val judgement = RecoveryConfidencePolicy.evaluate(
            identifiedBySignature = true,
            hasExtension = true,
            hasDeclaredMime = true,
            isReadable = false,
        )
        assertEquals(RecoveryConfidence.LOW, judgement.confidence)
        assertEquals(RecoveryStatus.PREVIEW_ONLY, judgement.status)
    }

    @Test
    fun aThumbnailArtefactIsNeverAvailable() {
        assertEquals(
            RecoveryStatus.PREVIEW_ONLY,
            RecoveryConfidencePolicy.forThumbnailArtifact(identifiedBySignature = true).status,
        )
        assertEquals(
            RecoveryConfidencePolicy.forThumbnailArtifact(identifiedBySignature = true).evidence,
            "A thumbnail or cached preview, not the full-resolution original.",
        )
    }
}
