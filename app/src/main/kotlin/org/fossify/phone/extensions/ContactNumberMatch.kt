package org.fossify.phone.extensions

import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact

/** Digits compared from the end of a number when the exact comparison finds no one. */
private const val COMPARABLE_TAIL_LENGTH = 9

/**
 * Whether this stored number and [number] end in the same nine digits.
 *
 * Commons' [Contact.doesHavePhoneNumber] compares only the stored normalized form through
 * `PhoneNumberUtils.compare`, and that misses a contact saved without its country code (`605 282 446`)
 * when the network hands us `+420605282446`. A private contact from renrakusaki has no system call-log
 * cache to fall back on either, so the call screen and the call log both showed the bare number.
 */
fun PhoneNumber.hasSameTailAs(number: String): Boolean {
    val wanted = number.filter { it.isDigit() }
    if (wanted.length < COMPARABLE_TAIL_LENGTH) return false
    val tail = wanted.takeLast(COMPARABLE_TAIL_LENGTH)
    return listOf(normalizedNumber, value).any { candidate ->
        val digits = candidate.orEmpty().normalizePhoneNumber().filter { it.isDigit() }
        digits.length >= COMPARABLE_TAIL_LENGTH && digits.takeLast(COMPARABLE_TAIL_LENGTH) == tail
    }
}

/** The contact holding [number]: commons' exact match first, then the nine-digit tail across every number. */
fun List<Contact>.findByNumber(number: String): Contact? =
    firstOrNull { it.doesHavePhoneNumber(number) }
        ?: firstOrNull { contact -> contact.phoneNumbers.any { it.hasSameTailAs(number) } }
