package org.fossify.phone.helpers

import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.KeypadHelper
import java.util.Locale

/**
 * Turns a name into the digits it would be typed as on the keypad, so "Bonk" is found by 2665. Used
 * by both the Dialpad screen's contact list and the Recents tab's dialpad filter, which must agree.
 */
object T9Helper {
    private val russianCharsMap = hashMapOf(
        'а' to '2', 'б' to '2', 'в' to '2', 'г' to '2',
        'д' to '3', 'е' to '3', 'ё' to '3', 'ж' to '3', 'з' to '3',
        'и' to '4', 'й' to '4', 'к' to '4', 'л' to '4',
        'м' to '5', 'н' to '5', 'о' to '5', 'п' to '5',
        'р' to '6', 'с' to '6', 'т' to '6', 'у' to '6',
        'ф' to '7', 'х' to '7', 'ц' to '7', 'ч' to '7',
        'ш' to '8', 'щ' to '8', 'ъ' to '8', 'ы' to '8',
        'ь' to '9', 'э' to '9', 'ю' to '9', 'я' to '9'
    )

    private val hasRussianLocale get() = Locale.getDefault().language == "ru"

    fun toDigits(name: String): String {
        val converted = KeypadHelper
            .convertKeypadLettersToDigits(name.normalizeString())
            .filterNot { it.isWhitespace() }

        if (!hasRussianLocale) {
            return converted
        }

        var russianConverted = ""
        converted.lowercase(Locale.getDefault()).forEach { char ->
            russianConverted += russianCharsMap.getOrElse(char) { char }
        }

        return russianConverted
    }
}
