package org.fossify.phone.views

import org.fossify.commons.extensions.menuPanelDrawable
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.menu.ListMenuItemView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.fossify.phone.R
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.phone.helpers.MenuPills
import kotlin.math.roundToInt

/**
 * The row of every popup menu in the app. A menu must show at a glance which lines can be pressed, so:
 *
 * - an action row is an outlined pill spanning the menu, its icon inside at the left and a › at the
 *   right when it opens something;
 * - the icon is coloured by what the row does (ThemeSlot.MENU_ROLE_*), while the frame and the label
 *   stay in the accent — or all three in the warning colour for a destructive row;
 * - a disabled row is the same pill at 38 %;
 * - a checkable row stays plain and gets the outlined switch, never a checkbox;
 * - the disabled title line a menu opens with stays plain text.
 *
 * It is swapped in through the app's own copies of abc_popup_menu_item_layout and
 * abc_cascading_menu_item_layout, so no call site has to know; icons, roles and the › come from
 * helpers/MenuPills. Anything unexpected in the row's structure leaves it a stock row.
 */
@SuppressLint("RestrictedApi")
class PillMenuItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.listMenuViewStyle,
) : ListMenuItemView(context, attrs, defStyleAttr) {

    private var content: LinearLayout? = null
    private var title: TextView? = null
    private var arrow: ImageView? = null
    private var padStart = 0
    private var padTop = 0
    private var padEnd = 0
    private var padBottom = 0
    private var contentHeight = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        content = findViewById(androidx.appcompat.R.id.content)
        title = findViewById(androidx.appcompat.R.id.title)
        arrow = findViewById(androidx.appcompat.R.id.submenuarrow)
        val content = content ?: return
        padStart = content.paddingStart
        padTop = content.paddingTop
        padEnd = content.paddingEnd
        padBottom = content.paddingBottom
        contentHeight = content.layoutParams.height

        // The stock group divider is all but invisible on a black menu: a clear line, as wide as the pills
        findViewById<View>(androidx.appcompat.R.id.group_divider)?.let { divider ->
            divider.setBackgroundColor(MenuPills.dividerColor(context))
            (divider.layoutParams as? MarginLayoutParams)?.let {
                it.leftMargin = dp(MenuPills.SIDE_MARGIN_DP)
                it.rightMargin = dp(MenuPills.SIDE_MARGIN_DP)
                divider.layoutParams = it
            }
        }
    }

    override fun initialize(itemData: MenuItemImpl, menuType: Int) {
        val styled = content != null && title != null
        // every row keeps the icon column; style() fills it for an item that brought no icon
        if (styled) setForceShowIcon(true)

        super.initialize(itemData, menuType)
        if (styled) style(itemData)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The pill carries the ripple, so the list's rectangular press highlight would only paint over
        // its round ends
        val list = parent as? AbsListView ?: return
        if (content != null && list.selector !is ColorDrawable) {
            list.selector = ColorDrawable(Color.TRANSPARENT)
            // The popup's own panel: the outermost ancestor that carries a ground (the theme's popup
            // background) is given the menu ground inside the menu frame, built from their roles — no
            // colour in styles.xml.
            var panel: View? = null
            var v: View? = list
            while (v != null) {
                if (v.background != null) panel = v
                v = v.parent as? View
            }
            panel?.background = context.menuPanelDrawable()
        }
    }

    private fun style(item: MenuItemImpl) {
        val content = content ?: return
        val title = title ?: return
        val id = item.itemId
        val checkable = item.isCheckable
        val header = MenuPills.isHeader(item)
        val pill = !checkable && !header
        val frameColor = MenuPills.fade(item, MenuPills.frameColor(context, id))

        frameRow(content, pill, checkable)
        content.background = if (pill) pillBackground(frameColor) else null
        // The switch of a checkable row ends where the pills end
        content.setPaddingRelative(padStart, padTop, if (checkable) 0 else padEnd, padBottom)

        // Titles reach us wearing a colour span (Menu.colorItemTitles); the row decides its own colour
        title.text = item.title?.toString()
        title.setTextColor(MenuPills.fade(item, MenuPills.labelColor(context, id)))

        styleArrow(opens = pill && (item.hasSubMenu() || MenuPills.spec(id)?.opens == true), color = frameColor)
        val iconColor = MenuPills.fade(item, MenuPills.iconColor(context, id))
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child is CheckBox) {
                styleSwitch(child)
            } else if (child is ImageView && child.id == androidx.appcompat.R.id.icon) {
                styleIcon(child, iconColor, MenuPills.spec(id)?.icon)
            }
        }
    }

    private fun frameRow(content: LinearLayout, pill: Boolean, checkable: Boolean) {
        val params = content.layoutParams as? MarginLayoutParams ?: return
        val side = dp(MenuPills.SIDE_MARGIN_DP)
        val vertical = if (pill) dp(MenuPills.PILL_MARGIN_DP) else 0
        // A checkable row has nothing to frame, so it is only as tall as its switch needs
        params.height = if (checkable) dp(MenuPills.TOGGLE_HEIGHT_DP) else contentHeight
        params.setMargins(side, vertical, side, vertical)
        content.layoutParams = params
    }

    // The › at the right end of a pill that opens something
    private fun styleArrow(opens: Boolean, color: Int) {
        val arrow = arrow ?: return
        if (opens) {
            val chevron = ContextCompat.getDrawable(context, R.drawable.ic_menu_chevron)?.mutate()
            chevron?.setTint(color)
            arrow.setImageDrawable(chevron)
        }
        arrow.visibility = if (opens) View.VISIBLE else View.GONE
    }

    // The icon sits clear of the pill's round end, in its role's colour. The menu item's own drawable
    // may carry the top bar's colour filter and is shared with it, so the row tints a copy; an item
    // that brought none gets the table's. (Set on the view, never on the item: changing an item from
    // inside its own row's binding would re-notify the list mid-layout.)
    private fun styleIcon(icon: ImageView, color: Int, fallback: Int?) {
        (icon.layoutParams as? MarginLayoutParams)?.let {
            it.marginStart = dp(ICON_START_DP)
            it.marginEnd = -dp(ICON_END_PULL_DP)
            it.topMargin = dp(2)
            it.bottomMargin = dp(2)
            icon.layoutParams = it
        }

        val own = icon.drawable
        val copy = if (own != null) {
            own.constantState?.newDrawable(resources)?.mutate() ?: own.mutate()
        } else {
            fallback?.let { ContextCompat.getDrawable(context, it)?.mutate() }
        }
        copy?.colorFilter = null
        icon.setImageDrawable(copy)
        icon.imageTintList = ColorStateList.valueOf(color)
    }

    private fun styleSwitch(box: CheckBox) {
        if (box.tag != null) return
        box.buttonDrawable = SwitchDrawable(
            on = context.uiColor(UiRole.SWITCH_ON_OUTLINE),
            off = context.uiColor(UiRole.SWITCH_OFF_OUTLINE),
            onDot = context.uiColor(UiRole.SWITCH_ON_DOT),
            offDot = context.uiColor(UiRole.SWITCH_OFF_DOT),
            width = dp(SWITCH_WIDTH_DP),
            height = dp(SWITCH_HEIGHT_DP),
            strokeWidth = dp(2).toFloat(),
        )
        box.background = null
        // A long label stops short of the switch
        (box.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = dp(MenuPills.SIDE_MARGIN_DP)
            box.layoutParams = it
        }
        box.tag = true
    }

    private fun pillBackground(color: Int): Drawable {
        val radius = dp(PILL_RADIUS_DP).toFloat()
        val frame = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke((PILL_FRAME_DP * resources.displayMetrics.density).roundToInt().coerceAtLeast(1), color)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        val ripple = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * RIPPLE_ALPHA).roundToInt())
        return RippleDrawable(ColorStateList.valueOf(ripple), frame, mask)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    // The theme's outlined switch: a round outline with a solid dot, right and accent when on, left and
    // a darker accent when off. Nothing is ever filled.
    private class SwitchDrawable(
        private val on: Int,
        private val off: Int,
        private val onDot: Int,
        private val offDot: Int,
        private val width: Int,
        private val height: Int,
        strokeWidth: Float,
    ) : Drawable() {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val rect = RectF()
        private var checked = false

        override fun draw(canvas: Canvas) {
            val color = if (checked) on else off
            val inset = stroke.strokeWidth / 2f
            rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
            stroke.color = color
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, stroke)
            fill.color = if (checked) onDot else offDot
            val half = bounds.height() / 2f
            val centerX = if (checked) bounds.right - half else bounds.left + half
            canvas.drawCircle(centerX, bounds.exactCenterY(), bounds.height() * DOT_RADIUS, fill)
        }

        override fun isStateful() = true

        override fun onStateChange(state: IntArray): Boolean {
            val nowChecked = android.R.attr.state_checked in state
            if (nowChecked == checked) return false
            checked = nowChecked
            invalidateSelf()
            return true
        }

        override fun getIntrinsicWidth() = width

        override fun getIntrinsicHeight() = height

        // drawn in the two colours it was given, nothing else
        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val PILL_FRAME_DP = 1.25f
        const val PILL_RADIUS_DP = 100
        const val RIPPLE_ALPHA = 0.3f
        const val ICON_START_DP = 14
        const val ICON_END_PULL_DP = 6
        const val SWITCH_WIDTH_DP = 44
        const val SWITCH_HEIGHT_DP = 24
        const val DOT_RADIUS = 0.23f
    }
}
