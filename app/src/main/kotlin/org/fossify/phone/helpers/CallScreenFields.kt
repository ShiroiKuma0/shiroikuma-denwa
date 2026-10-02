package org.fossify.phone.helpers

import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import org.fossify.phone.R
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.models.CallContact

// What the caller block on the call screen may show — the twin of renrakusaki's RowField catalog,
// built on [CallContact] rather than on a full contact.
//
// `slot` carries the field's own styling (font / weight / size / colour) through the existing theme
// machinery; `defaultSizeRes` is the size it wears until that styling overrides it; `extract` returns
// the field's value, and "" means the field is skipped for this caller rather than drawn empty.
enum class CallField(
    val key: String,
    @StringRes val labelRes: Int,
    val slot: ThemeSlot,
    @DimenRes val defaultSizeRes: Int,
    val extract: (CallContact) -> String,
    // The name-shaped fields: the ones a nickname stands in for, and the ones that fall back to the
    // number (or "unknown caller") instead of vanishing when nothing is known about the caller.
    val isName: Boolean = false,
) {
    DISPLAY_NAME(
        "display_name", R.string.field_display_name, ThemeSlot.CALL_DISPLAY_NAME,
        R.dimen.caller_name_text_size, { it.name }, isName = true,
    ),
    // The composite name fields: one string built from the two structured name parts, in the order —
    // and with the capitalization — each field is named after. Exactly one of them is normally shown;
    // they all fall back to the display name for a caller carrying neither part (a company-only
    // contact, or a number with nothing behind it), so the block never goes blank.
    SURNAME_FIRST(
        "surname_first", R.string.field_surname_first, ThemeSlot.CALL_SURNAME_FIRST,
        R.dimen.caller_name_text_size,
        { composedName(it, surnameFirst = true, surnameCaps = false, separator = ", ") }, isName = true,
    ),
    FIRST_SURNAME(
        "first_surname", R.string.field_first_surname, ThemeSlot.CALL_FIRST_SURNAME,
        R.dimen.caller_name_text_size,
        { composedName(it, surnameFirst = false, surnameCaps = false, separator = " ") }, isName = true,
    ),
    FIRST_SURNAME_CAPS(
        "first_surname_caps", R.string.field_first_surname_caps, ThemeSlot.CALL_FIRST_SURNAME_CAPS,
        R.dimen.caller_name_text_size,
        { composedName(it, surnameFirst = false, surnameCaps = true, separator = " ") }, isName = true,
    ),
    SURNAME_CAPS_FIRST(
        "surname_caps_first", R.string.field_surname_caps_first, ThemeSlot.CALL_SURNAME_CAPS_FIRST,
        R.dimen.caller_name_text_size,
        { composedName(it, surnameFirst = true, surnameCaps = true, separator = " ") }, isName = true,
    ),
    PREFIX("prefix", R.string.field_prefix, ThemeSlot.CALL_PREFIX, R.dimen.call_status_text_size, { it.prefix }),
    FIRST_NAME(
        "first_name", R.string.field_first_name, ThemeSlot.CALL_FIRST_NAME,
        R.dimen.call_status_text_size, { it.firstName },
    ),
    MIDDLE_NAME(
        "middle_name", R.string.field_middle_name, ThemeSlot.CALL_MIDDLE_NAME,
        R.dimen.call_status_text_size, { it.middleName },
    ),
    SURNAME("surname", R.string.field_surname, ThemeSlot.CALL_SURNAME, R.dimen.call_status_text_size, { it.surname }),
    SUFFIX("suffix", R.string.field_suffix, ThemeSlot.CALL_SUFFIX, R.dimen.call_status_text_size, { it.suffix }),
    NICKNAME(
        "nickname", R.string.field_nickname, ThemeSlot.CALL_NICKNAME,
        R.dimen.call_status_text_size, { it.nickname },
    ),
    NUMBER("number", R.string.field_phone, ThemeSlot.CALL_NUMBER, R.dimen.call_status_text_size, { it.number }),
    NUMBER_LABEL(
        "number_label", R.string.field_number_label, ThemeSlot.CALL_NUMBER_LABEL,
        R.dimen.call_status_text_size, { it.numberLabel },
    ),
    COMPANY("company", R.string.field_company, ThemeSlot.CALL_COMPANY, R.dimen.call_status_text_size, { it.company }),
    POSITION(
        "position", R.string.field_position, ThemeSlot.CALL_POSITION,
        R.dimen.call_status_text_size, { it.jobPosition },
    ),
    NOTE("note", R.string.field_note, ThemeSlot.CALL_NOTE, R.dimen.call_status_text_size, { it.note });

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * The field's value for this caller. With [preferNickname] on, a contact who has a nickname is known
 * by it: the nickname takes the place of whichever name shape is shown, and the name shapes are the
 * only fields it displaces — a separately ticked [CallField.NICKNAME] row still shows the nickname,
 * and every other field keeps its own value.
 */
fun CallField.valueFor(contact: CallContact, preferNickname: Boolean): String =
    if (isName && preferNickname && contact.nickname.isNotBlank()) contact.nickname else extract(contact)

// Join the two structured name parts in the given order, upper-casing the surname when asked. A caller
// carrying neither part falls back to the display name, left exactly as it is — that string is not a
// surname, so upper-casing it would be wrong.
private fun composedName(
    contact: CallContact,
    surnameFirst: Boolean,
    surnameCaps: Boolean,
    separator: String,
): String {
    val surname = if (surnameCaps) contact.surname.uppercase() else contact.surname
    val parts = if (surnameFirst) listOf(surname, contact.firstName) else listOf(contact.firstName, surname)
    return parts.filter { it.isNotEmpty() }.joinToString(separator).ifEmpty { contact.name }
}

// A single row in the editor / the caller block: a field, whether it is shown, and whether it shares
// the previous shown field's line (true => sits as a column to its right, false => starts a new line).
data class CallFieldEntry(val field: CallField, var checked: Boolean, var sameLine: Boolean)

// The composite name fields, in catalog order — kept together at the head of the layout list when a
// stored layout predates one of them (indexOfLast returning -1 puts the first of them at index 0).
private val COMPOSITE_NAME_FIELDS = setOf(
    CallField.SURNAME_FIRST, CallField.FIRST_SURNAME, CallField.FIRST_SURNAME_CAPS, CallField.SURNAME_CAPS_FIRST,
)

// Parse / serialize the caller-block layout config, and the built-in default.
object CallScreenConfig {
    private const val ENTRY_SEP = "|"
    private const val PART_SEP = ":"

    // Default: the name, then the number with its type beside it, then the caller's company, position
    // and note — one line each. Everything else is off, and an install that never touches the editor
    // stores nothing at all, so this is also what an upgrade lands on.
    fun defaultEntries(): List<CallFieldEntry> {
        val onByDefault = listOf(
            CallField.DISPLAY_NAME, CallField.NUMBER, CallField.NUMBER_LABEL,
            CallField.COMPANY, CallField.POSITION, CallField.NOTE,
        )
        val result = ArrayList<CallFieldEntry>()
        onByDefault.forEach {
            // the number's type reads as a column beside the number, the way it used to be appended to it
            result.add(CallFieldEntry(it, checked = true, sameLine = it == CallField.NUMBER_LABEL))
        }
        CallField.entries.filter { it !in onByDefault }.forEach {
            result.add(CallFieldEntry(it, checked = false, sameLine = false))
        }
        return result
    }

    fun parse(stored: String): List<CallFieldEntry> {
        if (stored.isBlank()) {
            return defaultEntries()
        }

        val seen = LinkedHashMap<CallField, CallFieldEntry>()
        stored.split(ENTRY_SEP).forEach { token ->
            val parts = token.split(PART_SEP)
            val field = parts.getOrNull(0)?.let { CallField.fromKey(it) } ?: return@forEach
            val checked = parts.getOrNull(1) == "1"
            val sameLine = parts.getOrNull(2) == "1"
            seen[field] = CallFieldEntry(field, checked, sameLine)
        }

        // Add any catalog fields missing from storage (e.g. introduced in a later version), unchecked — a
        // composite name field joins its siblings at the top of the list, everything else goes to the end.
        // Once the user reorders and saves, the stored order wins.
        val result = seen.values.toMutableList()
        CallField.entries.forEach { field ->
            if (field !in seen) {
                val entry = CallFieldEntry(field, checked = false, sameLine = false)
                if (field in COMPOSITE_NAME_FIELDS) {
                    result.add(result.indexOfLast { it.field in COMPOSITE_NAME_FIELDS } + 1, entry)
                } else {
                    result.add(entry)
                }
            }
        }
        return result
    }

    fun serialize(entries: List<CallFieldEntry>): String = entries.joinToString(ENTRY_SEP) { entry ->
        "${entry.field.key}$PART_SEP${if (entry.checked) 1 else 0}$PART_SEP${if (entry.sameLine) 1 else 0}"
    }
}
