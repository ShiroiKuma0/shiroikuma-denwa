package org.fossify.phone.models

import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.phone.extensions.getDayCode

/**
 * Used at displaying recent calls.
 * For contacts with multiple numbers specify the number and type
 */
@kotlinx.serialization.Serializable
data class RecentCall(
    val id: Int,
    val phoneNumber: String,
    val name: String,
    val photoUri: String,
    val startTS: Long,
    val duration: Int,
    val type: Int,
    val simID: Int,
    val simColor: Int,
    val specificNumber: String,
    val specificType: String,
    val isUnknownNumber: Boolean,
    val groupedCalls: MutableList<RecentCall>? = null,
    /** What the call was made through, frozen then — see [CallStamp]; null when nothing is known. */
    val stamp: CallStamp? = null,
) : CallLogItem() {
    val dayCode = startTS.getDayCode()

    fun doesContainPhoneNumber(text: String): Boolean {
        return if (text.toLongOrNull() != null) {
            val normalizedText = text.normalizePhoneNumber()
            PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
                    phoneNumber.contains(text) ||
                    phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
                    phoneNumber.contains(normalizedText)
        } else {
            false
        }
    }
}
