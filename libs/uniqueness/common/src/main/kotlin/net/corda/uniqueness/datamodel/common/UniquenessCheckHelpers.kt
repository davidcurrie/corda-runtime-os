package net.corda.uniqueness.datamodel.common

import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultMalformedRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultSuccessAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowOutOfBoundsAvro
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorGeneralImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorGeneral
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import org.apache.avro.specific.SpecificRecord
import java.time.Instant

/**
 * Converts an Avro result to a [UniquenessCheckResult].
 */
fun UniquenessCheckResponseAvro.toUniquenessResult(): UniquenessCheckResult {

    return when (val avroResult = result) {
        is UniquenessCheckResultInputStateConflictAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorInputStateConflictImpl(avroResult.conflictingStates.map {
                    // FIXME Consuming tx hash is populated as [null] for now
                    UniquenessCheckStateDetailsImpl(it.toStateRef(), null)
                })
            )
        }
        is UniquenessCheckResultInputStateUnknownAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorInputStateUnknownImpl(avroResult.unknownStates.map {
                    it.toStateRef()
                })
            )
        }
        is UniquenessCheckResultReferenceStateConflictAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateConflictImpl(avroResult.conflictingStates.map {
                    // FIXME Consuming tx hash is populated as [null] for now
                    UniquenessCheckStateDetailsImpl(it.toStateRef(), null)
                })
            )
        }
        is UniquenessCheckResultReferenceStateUnknownAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateUnknownImpl(avroResult.unknownStates.map {
                    it.toStateRef()
                })
            )
        }
        is UniquenessCheckResultTimeWindowOutOfBoundsAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                    avroResult.evaluationTimestamp,
                    avroResult.timeWindowLowerBound,
                    avroResult.timeWindowUpperBound
                )
            )
        }
        is UniquenessCheckResultMalformedRequestAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorGeneralImpl(
                    avroResult.errorText
                )
            )
        }
        is UniquenessCheckResultSuccessAvro -> {
            UniquenessCheckResultSuccessImpl(avroResult.commitTimestamp)
        }
        else -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorGeneralImpl(
                    "Unknown response type: ${avroResult.javaClass.typeName}"
                )
            )
        }
    }
}

/**
 * Converts the failure to the external Avro error
 */
fun UniquenessCheckResultFailure.toExternalError(): SpecificRecord {
    return when (val uniquenessError = error) {
        is UniquenessCheckErrorInputStateConflict ->
            UniquenessCheckResultInputStateConflictAvro(
                uniquenessError.conflictingStates.map { it.stateRef.toString() }
            )
        is UniquenessCheckErrorInputStateUnknown ->
            UniquenessCheckResultInputStateUnknownAvro(
                uniquenessError.unknownStates.map { it.toString() }
            )
        is UniquenessCheckErrorReferenceStateConflict ->
            UniquenessCheckResultReferenceStateConflictAvro(
                uniquenessError.conflictingStates.map { it.stateRef.toString() }
            )
        is UniquenessCheckErrorReferenceStateUnknown ->
            UniquenessCheckResultReferenceStateUnknownAvro(
                uniquenessError.unknownStates.map { it.toString() }
            )
        is UniquenessCheckErrorTimeWindowOutOfBounds ->
            UniquenessCheckResultTimeWindowOutOfBoundsAvro(
                uniquenessError.evaluationTimestamp,
                uniquenessError.timeWindowLowerBound,
                uniquenessError.timeWindowUpperBound
            )
        is UniquenessCheckErrorGeneral ->
            UniquenessCheckResultMalformedRequestAvro(
                uniquenessError.errorText
            )
        else -> {
            // TODO Should we add a general avro error?
            UniquenessCheckResultMalformedRequestAvro(
                "Unknown error type: ${uniquenessError.javaClass.typeName}"
            )
        }
    }
}

fun UniquenessCheckResult.toCharacterRepresentation() = if (this is UniquenessCheckResultSuccess) {
    UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
} else {
    UniquenessConstants.RESULT_REJECTED_REPRESENTATION
}

fun String.toStateRef() : UniquenessCheckStateRef {
    return UniquenessCheckStateRefImpl(
        SecureHash.parse(substringBeforeLast(":")),
        substringAfterLast(":").toInt()
    )
}