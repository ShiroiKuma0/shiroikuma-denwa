package org.fossify.phone.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogFontPickerBinding
import org.fossify.phone.databinding.ItemFontOptionBinding
import org.fossify.phone.extensions.availableFontOptions
import org.fossify.phone.extensions.fontTypeface

// Lists every available font with its name drawn in its own typeface, plus an "Add font…" action.
class FontPickerDialog(
    private val activity: Activity,
    private val onAddFont: () -> Unit,
    private val onPick: (fileName: String) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogFontPickerBinding.inflate(activity.layoutInflater)

    init {
        val textColor = activity.getProperTextColor()
        val primaryColor = activity.getProperPrimaryColor()

        activity.availableFontOptions().forEach { option ->
            val row = ItemFontOptionBinding.inflate(activity.layoutInflater, binding.fontPickerHolder, false)
            row.fontOptionLabel.text = option.displayName
            row.fontOptionLabel.setTextColor(textColor)
            row.fontOptionLabel.typeface = activity.fontTypeface(option.fileName)
            row.fontOptionLabel.setOnClickListener {
                dialog?.dismiss()
                onPick(option.fileName)
            }
            binding.fontPickerHolder.addView(row.root)
        }

        val addRow = ItemFontOptionBinding.inflate(activity.layoutInflater, binding.fontPickerHolder, false)
        addRow.fontOptionLabel.text = activity.getString(R.string.theme_add_font)
        addRow.fontOptionLabel.setTextColor(primaryColor)
        addRow.fontOptionLabel.setOnClickListener {
            dialog?.dismiss()
            onAddFont()
        }
        binding.fontPickerHolder.addView(addRow.root)

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.theme_font) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
