package com.progallery.app.data.recovery

/**
 * How sure the scan can be that a discovered file is what it looks like, and
 * what may be done with it.
 *
 * The rule is deliberately conservative: an extension on its own is never
 * "high" confidence, and a file we cannot read is never reported as available.
 * Pure, so every branch is unit tested.
 */
object RecoveryConfidencePolicy {

    fun evaluate(
        identifiedBySignature: Boolean,
        hasExtension: Boolean,
        hasDeclaredMime: Boolean,
        isReadable: Boolean,
    ): RecoveryJudgement = when {
        !isReadable -> RecoveryJudgement(
            confidence = RecoveryConfidence.LOW,
            status = RecoveryStatus.PREVIEW_ONLY,
            evidence = "The file could not be read, so only its details are shown.",
        )

        identifiedBySignature -> RecoveryJudgement(
            confidence = RecoveryConfidence.HIGH,
            status = RecoveryStatus.AVAILABLE,
            evidence = "Identified from the file's own bytes, not its name.",
        )

        hasDeclaredMime && hasExtension -> RecoveryJudgement(
            confidence = RecoveryConfidence.MEDIUM,
            status = RecoveryStatus.AVAILABLE,
            evidence = "Classified from the system's type and the file extension.",
        )

        hasExtension -> RecoveryJudgement(
            confidence = RecoveryConfidence.MEDIUM,
            status = RecoveryStatus.AVAILABLE,
            evidence = "Classified from the file extension alone.",
        )

        else -> RecoveryJudgement(
            confidence = RecoveryConfidence.LOW,
            status = RecoveryStatus.AVAILABLE,
            evidence = "No extension or signature — the type is a guess.",
        )
    }

    /**
     * Confidence for a file that is only a thumbnail or cache artefact. It is
     * never the original, so it can never be reported as available.
     */
    fun forThumbnailArtifact(identifiedBySignature: Boolean): RecoveryJudgement = RecoveryJudgement(
        confidence = if (identifiedBySignature) {
            RecoveryConfidence.MEDIUM
        } else {
            RecoveryConfidence.LOW
        },
        status = RecoveryStatus.PREVIEW_ONLY,
        evidence = "A thumbnail or cached preview, not the full-resolution original.",
    )
}
