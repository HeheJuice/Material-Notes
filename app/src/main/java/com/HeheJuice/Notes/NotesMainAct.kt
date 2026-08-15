package com.HeheJuice.Notes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.doOnLayout

class NotesMainAct : Activity() {

    private lateinit var slidingPillView: View
    private lateinit var notesTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var notesContainer: FrameLayout
    private lateinit var settingsContainer: FrameLayout
    private var isNotesActive = true

    private var accentColor: Int = 0
    private var activeTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var cardBgColor: Int = 0
    private var cardBorderColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // ----- 1. Monet & Material 3 Colors -----
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            accentColor = ContextCompat.getColor(this, android.R.color.system_accent2_100)
            activeTextColor = ContextCompat.getColor(this, android.R.color.system_accent2_800)
            cardBgColor = ContextCompat.getColor(
                this,
                if (isDark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
            )
        } else {
            accentColor = Color.parseColor("#E8DEF8")
            activeTextColor = Color.parseColor("#1D192B")
            cardBgColor = if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
        }

        secondaryTextColor = if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
        cardBorderColor = if (isDark) Color.parseColor("#49454F") else Color.parseColor("#E7E0EC")

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(cardBgColor) }

        // ----- 2. Content Containers -----
        notesContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        settingsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        val contentHolder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(notesContainer)
            addView(settingsContainer)
        }
        rootFrame.addView(contentHolder)

        // ----- 3. Bottom Bar & Unified Coordinate Stack -----
        val bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        }

        // Unified wrapper so slidingPillView and tabButtonsLayout share the exact same coordinate space
        val innerTabStack = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        slidingPillView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(44f), Gravity.CENTER_VERTICAL)
        }

        val tabButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val notesDrawable = try { ContextCompat.getDrawable(this, R.drawable.note_stack_24px) } catch (e: Exception) { null }
        val settingsDrawable = try { ContextCompat.getDrawable(this, R.drawable.settings_24px) } catch (e: Exception) { null }

        notesTabBtn = TextView(this).apply {
            text = "Notes"
            textSize = 14f
            setTextColor(activeTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(20f), 0)
            compoundDrawablePadding = dpToPx(8f)
            setCompoundDrawablesWithIntrinsicBounds(notesDrawable, null, null, null)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
        }

        settingsTabBtn = TextView(this).apply {
            text = "Settings"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(20f), 0)
            compoundDrawablePadding = dpToPx(8f)
            setCompoundDrawablesWithIntrinsicBounds(settingsDrawable, null, null, null)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
        }

        tabButtonsLayout.addView(notesTabBtn)
        tabButtonsLayout.addView(settingsTabBtn)

        innerTabStack.addView(slidingPillView)
        innerTabStack.addView(tabButtonsLayout)
        tabPillContainer.addView(innerTabStack)
        bottomBarLayout.addView(tabPillContainer)
        rootFrame.addView(bottomBarLayout)

        setContentView(rootFrame)

        val tintDrawableColor: (TextView, Int) -> Unit = { textView, color ->
            val drawables = textView.compoundDrawables
            val left = drawables[0]?.let {
                val wrapped = DrawableCompat.wrap(it).mutate()
                DrawableCompat.setTint(wrapped, color)
                wrapped
            }
            textView.setCompoundDrawablesWithIntrinsicBounds(left, drawables[1], drawables[2], drawables[3])
        }

        tintDrawableColor(notesTabBtn, activeTextColor)
        tintDrawableColor(settingsTabBtn, secondaryTextColor)

        // ----- 4. Tab Switching & Perfectly Synced Drag Tracking -----
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

            if (p < 0.5f) {
                notesTabBtn.setTextColor(activeTextColor)
                tintDrawableColor(notesTabBtn, activeTextColor)
                settingsTabBtn.setTextColor(secondaryTextColor)
                tintDrawableColor(settingsTabBtn, secondaryTextColor)
            } else {
                notesTabBtn.setTextColor(secondaryTextColor)
                tintDrawableColor(notesTabBtn, secondaryTextColor)
                settingsTabBtn.setTextColor(activeTextColor)
                tintDrawableColor(settingsTabBtn, activeTextColor)
            }
        }

        var pillAnimator: ValueAnimator? = null

        val animatePillTo: (Float, () -> Unit) -> Unit = { targetProgress, onEnd ->
            pillAnimator?.cancel()
            val x0 = notesTabBtn.left.toFloat()
            val x1 = settingsTabBtn.left.toFloat()
            val currentX = slidingPillView.translationX
            val currentProgress = if (x1 > x0) ((currentX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f

            pillAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
                duration = 250L
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
                notesContainer.visibility = if (toNotes) View.VISIBLE else View.GONE
                settingsContainer.visibility = if (toNotes) View.GONE else View.VISIBLE
            }
        }

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

        // Clean touch listener using precise innerStack coordinates
        tabPillContainer.setOnTouchListener { view, event ->
            val x0 = notesTabBtn.left.toFloat()
            val x1 = settingsTabBtn.left.toFloat()

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

        tabPillContainer.doOnLayout {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = notesTabBtn.width }
            slidingPillView.translationX = notesTabBtn.left.toFloat()
            slidingPillView.requestLayout()
        }

        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }

            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(16f) + bottomInset
            insets
        }
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}
