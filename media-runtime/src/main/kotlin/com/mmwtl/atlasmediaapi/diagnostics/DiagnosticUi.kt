package com.mmwtl.atlasmediaapi.diagnostics

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Shared native view styling matching the AtlasMediaWidget matte graphite surface family. */
internal object DiagnosticUi {
    // Matches the default matte graphite surface used by fx11widget / AtlasMediaWidget
    val BACKGROUND = Color.rgb(29, 34, 40)
    val CARD = Color.rgb(38, 38, 38)
    val NESTED = Color.rgb(51, 51, 51)
    val PRIMARY = Color.rgb(245, 245, 245)
    val SECONDARY = Color.rgb(212, 212, 212)
    val ACCENT = Color.rgb(120, 147, 160)
    val OUTLINE = Color.rgb(115, 115, 115)
    val ERROR = Color.rgb(217, 130, 130)

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun background(context: Context, color: Int, radiusDp: Float = 8f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    fun outlinedBackground(
        context: Context,
        color: Int = Color.TRANSPARENT,
        strokeColor: Int = OUTLINE,
        radiusDp: Float = 8f,
    ): GradientDrawable = background(context, color, radiusDp).apply {
        setStroke(dp(context, 1f), strokeColor)
    }

    fun text(context: Context, value: String, sizeSp: Float, color: Int = PRIMARY): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            includeFontPadding = true
        }

    fun heading(context: Context, value: String, sizeSp: Float): TextView =
        text(context, value, sizeSp).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    fun button(
        context: Context,
        value: String,
        primary: Boolean = false,
        destructive: Boolean = false,
    ): Button {
        val normalColor = when {
            primary -> ACCENT
            destructive -> Color.TRANSPARENT
            else -> NESTED
        }
        val pressedColor = when {
            primary -> Color.rgb(145, 169, 180)
            destructive -> Color.rgb(72, 40, 40)
            else -> Color.rgb(68, 68, 68)
        }
        val buttonTextColor = if (primary) Color.rgb(7, 16, 20) else {
            if (destructive) ERROR else PRIMARY
        }

        return Button(context).apply {
            text = value
            textSize = 14f
            setTextColor(buttonTextColor)
            setAllCaps(false)
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
            setPadding(dp(context, 16f), dp(context, 10f), dp(context, 16f), dp(context, 10f))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                    background(context, pressedColor))
                addState(intArrayOf(-android.R.attr.state_enabled),
                    background(context, normalColor).apply { alpha = 105 })
                addState(intArrayOf(), if (destructive) {
                    outlinedBackground(context, strokeColor = ERROR)
                } else if (primary) {
                    background(context, normalColor)
                } else {
                    background(context, normalColor)
                })
            }
        }
    }

    fun outlinedButton(context: Context, value: String, destructive: Boolean = false): Button =
        button(context, value, destructive = destructive).apply {
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                    background(context, if (destructive) Color.rgb(72, 40, 40) else NESTED))
                addState(intArrayOf(-android.R.attr.state_enabled),
                    outlinedBackground(context, strokeColor = if (destructive) ERROR else OUTLINE).apply {
                        alpha = 105
                    })
                addState(intArrayOf(), outlinedBackground(
                    context,
                    strokeColor = if (destructive) ERROR else OUTLINE,
                ))
            }
        }

    fun permissionButton(
        context: Context,
        label: String,
        granted: Boolean,
    ): Button = Button(context).apply {
        text = if (granted) "✓ $label (Предоставлено)" else label
        textSize = 15f
        setAllCaps(false)
        minHeight = dp(context, 48f)
        minimumHeight = dp(context, 48f)
        stateListAnimator = null
        setPadding(dp(context, 16f), dp(context, 12f), dp(context, 16f), dp(context, 12f))
        val normalBg = if (granted) NESTED else ACCENT
        val pressedBg = if (granted) Color.rgb(68, 68, 68) else Color.rgb(145, 169, 180)
        val buttonTextColor = if (granted) PRIMARY else Color.rgb(7, 16, 20)
        setTextColor(buttonTextColor)
        setTypeface(Typeface.DEFAULT, if (granted) Typeface.NORMAL else Typeface.BOLD)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                background(context, pressedBg))
            addState(intArrayOf(-android.R.attr.state_enabled),
                background(context, normalBg).apply { alpha = 105 })
            addState(intArrayOf(), background(context, normalBg))
        }
    }

    fun tileButton(
        context: Context,
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): Button = Button(context).apply {
        text = label
        textSize = 14f
        setAllCaps(false)
        minHeight = dp(context, 46f)
        minimumHeight = dp(context, 46f)
        stateListAnimator = null
        setPadding(dp(context, 8f), dp(context, 10f), dp(context, 8f), dp(context, 10f))
        updateTileState(selected)
        setOnClickListener { onClick() }
    }

    fun Button.updateTileState(selected: Boolean) {
        val ctx = context
        if (selected) {
            setTextColor(Color.rgb(7, 16, 20))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = background(ctx, ACCENT, 8f)
        } else {
            setTextColor(PRIMARY)
            setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            background = background(ctx, NESTED, 8f)
        }
    }

    fun switch(
        context: Context,
        label: String,
        initialChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ): Switch = Switch(context).apply {
        text = label
        textSize = 15f
        setTextColor(PRIMARY)
        isChecked = initialChecked
        val accentStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked),
            ),
            intArrayOf(
                ACCENT,
                Color.rgb(140, 140, 140),
            ),
        )
        val trackStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked),
            ),
            intArrayOf(
                Color.rgb(70, 95, 108),
                Color.rgb(68, 68, 68),
            ),
        )
        thumbTintList = accentStateList
        trackTintList = trackStateList
        setOnCheckedChangeListener { _, checked -> onCheckedChange(checked) }
    }

    fun sizeSeekBar(context: Context, min: Int, max: Int, initial: Int): SeekBar =
        SeekBar(context).apply {
            this.max = max
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.min = min
            }
            this.progress = initial
            val accentStateList = ColorStateList.valueOf(ACCENT)
            progressTintList = accentStateList
            thumbTintList = accentStateList
        }

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20f), dp(context, 18f), dp(context, 20f), dp(context, 18f))
        background = background(context, CARD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(context, 12f) }
    }

    fun fullWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    fun topMargin(view: View, context: Context, marginDp: Float) {
        val params = (view.layoutParams as? LinearLayout.LayoutParams) ?: fullWrap()
        params.topMargin = dp(context, marginDp)
        view.layoutParams = params
    }

    fun applySystemBarInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            target.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}
