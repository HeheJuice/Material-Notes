package com.HeheJuice.Notes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd

class NotesMainAct : Activity() {

    private lateinit var slidingPillView: View
    private lateinit var notesTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var notesContent: View
    private lateinit var settingsContent: View
    private var isNotesActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        // 1. Get Monet colors from theme
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val accentColor = typedValue.data
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true) // same
        val primaryTextColor = typedValue.data
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        val secondaryTextColor = typedValue.data

        // 2. Root container
        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(if (isDarkMode()) 0xFF000000.toInt() else 0xFFF2F2F7.toInt())
        }

        // 3. Content container (two placeholders)
        val contentContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Notes placeholder
        notesContent = TextView(this).apply {
            text = "Notes Content"
            textSize = 24f
            setTextColor(primaryTextColor)
            gravity = Gravity.CENTER
        }
        // Settings placeholder
        settingsContent = TextView(this).apply {
            text = "Settings Content"
            textSize = 24f
            setTextColor(primaryTextColor)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        contentContainer.addView(notesContent)
        contentContainer.addView(settingsContent)
        rootFrame.addView(contentContainer)

        // 4. Bottom bar (sliding pill with two tabs)
        val bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val tabPillContainer = FrameLayout(this).apply {
            background = createRoundedDrawable(
                color = if (isDarkMode()) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt(),
                strokeColor = if (isDarkMode()) 0xFF2C2C2E.toInt() else 0xFFE5E5EA.toInt()
            )
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        }

        // Sliding pill background (uses accent/Monet color)
        val pillBg = createRoundedDrawable(color = accentColor)
        slidingPillView = View(this).apply {
            background = pillBg
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(44f))
        }

        // Buttons
        val tabButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        notesTabBtn = TextView(this).apply {
            text = "Notes"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
        }

        settingsTabBtn = TextView(this).apply {
            text = "Settings"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
        }

        tabButtonsLayout.addView(notesTabBtn)
        tabButtonsLayout.addView(settingsTabBtn)

        tabPillContainer.addView(slidingPillView)
        tabPillContainer.addView(tabButtonsLayout)
        bottomBarLayout.addView(tabPillContainer)
        rootFrame.addView(bottomBarLayout)

        setContentView(rootFrame)

        // 5. Tab switching logic (same as before, but simplified)
        val updatePillPosition: (Float) -> Unit = { progress ->
            val p = progress.coerceIn(0f, 1f)
            val x0 = notesTabBtn.left.toFloat()
            val x1 = settingsTabBtn.left.toFloat()
            val w0 = notesTabBtn.width.toFloat()
            val w1 = settingsTabBtn.width.toFloat()

            val currentX = x0 + (x1 - x0) * p
            val currentW = (w0 + (w1 - w0) * p).toInt()

            slidingPillView.translationX = currentX
            if (slidingPillView.layoutParams.width != currentW && currentW > 0) {
                slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = currentW }
                slidingPillView.requestLayout()
            }

            // Update text colors
            notesTabBtn.setTextColor(if (p < 0.5f) primaryTextColor else secondaryTextColor)
            settingsTabBtn.setTextColor(if (p < 0.5f) secondaryTextColor else primaryTextColor)
        }

        var pillAnimator: ValueAnimator? = null

        val animatePillTo: (Float, () -> Unit) -> Unit = { targetProgress, onEnd ->
            pillAnimator?.cancel()
            val currentX = slidingPillView.translationX
            val x0 = notesTabBtn.left.toFloat()
            val x1 = settingsTabBtn.left.toFloat()
            val currentProgress = if (x1 > x0) ((currentX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f

            pillAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
                duration = 220L
                interpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
                addUpdateListener { anim ->
                    updatePillPosition(anim.animatedValue as Float)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd()
                    }
                })
                start()
            }
        }

        val switchTab: (Boolean) -> Unit = { toNotes ->
            if (isNotesActive != toNotes) {
                isNotesActive = toNotes
                notesContent.visibility = if (toNotes) View.VISIBLE else View.GONE
                settingsContent.visibility = if (toNotes) View.GONE else View.VISIBLE
            }
        }

        // Touch handling on pill container
        tabPillContainer.setOnTouchListener { view, event ->
            val x0 = notesTabBtn.left.toFloat() + (notesTabBtn.width / 2f)
            val x1 = settingsTabBtn.left.toFloat() + (settingsTabBtn.width / 2f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pillAnimator?.cancel()
                    view.animate().cancel()
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .alpha(0.9f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()

                    val touchX = event.x - tabPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updatePillPosition(progress)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val touchX = event.x - tabPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updatePillPosition(progress)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val touchX = event.x - tabPillContainer.paddingLeft
                    val midPoint = (x0 + x1) / 2f
                    val targetIsNotes = touchX < midPoint
                    val targetProgress = if (targetIsNotes) 0f else 1f

                    animatePillTo(targetProgress) {
                        switchTab(targetIsNotes)
                    }

                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    view.animate().cancel()
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(350)
                        .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                        .start()
                    true
                }
                else -> false
            }
        }

        // Click listeners for direct taps
        notesTabBtn.setOnClickListener {
            if (!isNotesActive) {
                animatePillTo(0f) { switchTab(true) }
            }
        }
        settingsTabBtn.setOnClickListener {
            if (isNotesActive) {
                animatePillTo(1f) { switchTab(false) }
            }
        }

        // Set initial state
        rootFrame.post {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply {
                width = notesTabBtn.width
            }
            slidingPillView.translationX = notesTabBtn.left.toFloat()
            slidingPillView.requestLayout()
        }
    }

    // ---------- Helpers ----------
    private fun isDarkMode(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun createRoundedDrawable(color: Int, strokeColor: Int? = null): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(100f).toFloat()
            setColor(color)
            if (strokeColor != null) {
                setStroke(dpToPx(1f), strokeColor)
            }
        }
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}