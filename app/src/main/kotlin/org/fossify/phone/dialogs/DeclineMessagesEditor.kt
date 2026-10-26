package org.fossify.phone.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.phone.R
import org.fossify.phone.helpers.DeclineMessagesPrefs

/**
 * Dialogs that edit a list of decline messages — the general list ([number] null) or one caller's own
 * list. Shared by the Call decline messages section of the 白い熊 電話 UI page and the call log's
 * per-entry action, so both edit through exactly the same code (as in shiroikuma-jami, whose
 * DeclineMessagesEditor this mirrors). [onChanged] runs after every write.
 */
object DeclineMessagesEditor {
    // a list dialog never covers more of the screen than this; it scrolls beyond
    private const val LIST_MAX_HEIGHT = 0.6f

    private const val ACTION_EDIT = 0
    private const val ACTION_MOVE_UP = 1
    private const val ACTION_MOVE_DOWN = 2
    private const val ACTION_DELETE = 3

    private fun load(activity: Activity, number: String?): List<String> =
        if (number == null) DeclineMessagesPrefs.global(activity) else DeclineMessagesPrefs.forNumber(activity, number)

    private fun store(activity: Activity, number: String?, list: List<String>) {
        if (number == null) {
            DeclineMessagesPrefs.setGlobal(activity, list)
        } else {
            DeclineMessagesPrefs.setForNumber(activity, number, list)
        }
    }

    /** The whole list in one dialog: tap a message for its actions, "+ Add message" to add one. */
    fun showList(activity: Activity, number: String?, callerName: String?, onChanged: () -> Unit = {}) {
        if (number != null && callerName != null) {
            DeclineMessagesPrefs.setCallerName(activity, number, callerName)
        }

        val list = load(activity, number)
        val title = if (number == null) {
            activity.getString(R.string.sk_decline_editor_title)
        } else {
            activity.getString(R.string.sk_decline_editor_caller_title, callerName ?: number)
        }

        val again = { showList(activity, number, callerName, onChanged) }
        var dialog: AlertDialog? = null
        val view = if (list.isEmpty()) {
            DeclineMessageViews.messageView(activity, activity.getString(R.string.sk_decline_editor_empty))
        } else {
            DeclineMessageViews.rowsView(activity, list, LIST_MAX_HEIGHT) { which ->
                dialog?.dismiss()
                showItemActions(activity, number, which, onChanged, again)
            }
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.sk_decline_add) { _, _ ->
                showEdit(activity, activity.getString(R.string.sk_decline_add), "") { text ->
                    val l = load(activity, number).toMutableList()
                    if (text !in l) {
                        l.add(text)
                    }
                    store(activity, number, l)
                    onChanged()
                    again()
                }
            }
            .setNegativeButton(android.R.string.ok, null)

        if (number != null && list.isNotEmpty()) {
            builder.setNeutralButton(R.string.sk_decline_delete_all) { _, _ ->
                store(activity, number, emptyList())
                onChanged()
            }
        }

        activity.setupDialogStuff(view, builder, titleText = title) { dialog = it }
    }

    /** Edit / Move up / Move down / Delete for the message at [index]; [then] re-opens the list. */
    fun showItemActions(activity: Activity, number: String?, index: Int, onChanged: () -> Unit, then: () -> Unit = {}) {
        val list = load(activity, number)
        if (index !in list.indices) {
            return
        }

        val actions = listOf(
            activity.getString(R.string.sk_decline_edit),
            activity.getString(R.string.sk_decline_move_up),
            activity.getString(R.string.sk_decline_move_down),
            activity.getString(R.string.sk_decline_delete)
        )

        var dialog: AlertDialog? = null
        val view = DeclineMessageViews.rowsView(activity, actions, LIST_MAX_HEIGHT) { which ->
            dialog?.dismiss()
            val l = load(activity, number).toMutableList()
            if (index !in l.indices) {
                return@rowsView
            }

            when (which) {
                ACTION_EDIT -> {
                    showEdit(activity, activity.getString(R.string.sk_decline_edit), l[index]) { text ->
                        val m = load(activity, number).toMutableList()
                        if (index in m.indices) {
                            m[index] = text
                            store(activity, number, m)
                            onChanged()
                        }
                        then()
                    }
                    return@rowsView
                }

                ACTION_MOVE_UP -> if (index > 0) l.add(index - 1, l.removeAt(index))
                ACTION_MOVE_DOWN -> if (index < l.size - 1) l.add(index + 1, l.removeAt(index))
                ACTION_DELETE -> l.removeAt(index)
            }

            store(activity, number, l)
            onChanged()
            then()
        }

        val builder = activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.cancel) { _, _ -> then() }

        activity.setupDialogStuff(view, builder, titleText = list[index]) { dialog = it }
    }

    /** A multi-line text box; OK with blank text is ignored. */
    fun showEdit(activity: Activity, title: String, initial: String, onOk: (String) -> Unit) {
        val input = DeclineMessageViews.messageInput(activity, minLines = 2).apply {
            setText(initial)
            setSelection(initial.length)
            val h = DeclineMessageViews.px(activity, R.dimen.decline_compose_padding_start)
            val v = DeclineMessageViews.px(activity, R.dimen.decline_row_padding_vertical)
            setPadding(h, v, h, v)
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    onOk(text)
                }
            }
            .setNegativeButton(R.string.cancel, null)

        activity.setupDialogStuff(input, builder, titleText = title)
    }
}
