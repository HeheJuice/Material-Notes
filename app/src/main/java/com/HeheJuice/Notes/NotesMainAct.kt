package com.HeheJuice.Notes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.DynamicColors

class NotesMainAct : AppCompatActivity() {

    private lateinit var slidingPillView: View
    private lateinit var notesTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var notesContainer: FrameLayout
    private lateinit var settingsContainer: FrameLayout
    private var isNotesActive = true

    // Menu state & views
    private var isMenuExpanded = false
    private lateinit var menuOverlayContainer: LinearLayout
    private lateinit var bottomBarLayout: LinearLayout
    private lateinit var dimOverlay: View
    private lateinit var plusIconDrawable: PlusDrawable
    private lateinit var plusBtnRef: ImageView

    // Top Bar views
    private lateinit var topBarLayout: FrameLayout
    private lateinit var contentHolder: FrameLayout
    private lateinit var appNamePill: TextView

    // Colors (surface-based)
    private var windowBgColor: Int = 0
    private var accentColor: Int = 0
    private var activeTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var cardBgColor: Int = 0
    private var secondaryBtnColor: Int = 0

    // Press-scale touch listener
    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        val springBackInterpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .alpha(0.88f)
                    .setDuration(120)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(350)
                    .setInterpolator(springBackInterpolator)
                    .start()
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ----- Apply Saved Theme Preference before super.onCreate -----
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("app_theme", 0) // 0: System, 1: Light, 2: Dark
        when (savedTheme) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        // Restore active tab state if available
        if (savedInstanceState != null) {
            isNotesActive = savedInstanceState.getBoolean("is_notes_active", true)
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // ----- Force Dynamic Colors Application -----
        DynamicColors.applyToActivityIfAvailable(this)

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // ----- Status Bar Visibility Fix for Light Mode -----
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !isDark
        windowInsetsController.isAppearanceLightNavigationBars = !isDark

        // ----- Color Resolution -----
        windowBgColor = if (isDark) Color.BLACK else Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            accentColor = ContextCompat.getColor(
                this,
                if (isDark) android.R.color.system_accent2_800 else android.R.color.system_accent2_100
            )
            activeTextColor = ContextCompat.getColor(
                this,
                if (isDark) android.R.color.system_accent2_100 else android.R.color.system_accent1_900
            )
            cardBgColor = ContextCompat.getColor(
                this,
                if (isDark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
            )
        } else {
            accentColor = if (isDark) Color.parseColor("#36343B") else Color.parseColor("#F2EEF5")
            activeTextColor = if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#1D192B")
            cardBgColor = if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
        }

        secondaryTextColor = if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
        secondaryBtnColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(windowBgColor) }

        // ----- Content containers -----
        notesContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = if (isNotesActive) View.VISIBLE else View.GONE
        }
        settingsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = if (isNotesActive) View.GONE else View.VISIBLE
        }

        // Populate Settings Page UI
        val settingsScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            clipToPadding = false
        }

        val settingsContentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(100f))
        }

        // App Theme Card
        val themeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24f).toFloat()
                setColor(cardBgColor)
            }
            setPadding(dpToPx(20f), dpToPx(20f), dpToPx(20f), dpToPx(20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val themeTitle = TextView(this).apply {
            text = getString(R.string.setting_app_theme)
            textSize = 16f
            setTextColor(activeTextColor)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14f)
            }
        }

        // ----- Material 3 Expressive (MD3E) Toggle Group Style -----
        val toggleGroupContext = ContextThemeWrapper(
            this,
            com.google.android.material.R.style.Widget_Material3Expressive_MaterialButtonToggleGroup
        )

        val themeSelectorContainer = MaterialButtonToggleGroup(toggleGroupContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isSingleSelection = true
            isSelectionRequired = true
        }

        val themeOptions = listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )

        val buttonContext = ContextThemeWrapper(
            this,
            com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton
        )

        val unselectedBgColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getColor(
                this,
                if (isDark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_200
            )
        } else {
            secondaryBtnColor
        }

        val checkedState = intArrayOf(android.R.attr.state_checked)
        val uncheckedState = intArrayOf(-android.R.attr.state_checked)

        val buttonBgTint = android.content.res.ColorStateList(
            arrayOf(checkedState, uncheckedState),
            intArrayOf(accentColor, unselectedBgColor)
        )

        val buttonTextTint = android.content.res.ColorStateList(
            arrayOf(checkedState, uncheckedState),
            intArrayOf(activeTextColor, activeTextColor)
        )

        var pendingTheme = savedTheme

        themeOptions.forEachIndexed { index, optionName ->
            val isActive = (savedTheme == index)

            val optionBtn = MaterialButton(buttonContext).apply {
                id = View.generateViewId()
                text = optionName
                icon = null
                textSize = 11.5f
                isSingleLine = true

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                backgroundTintList = buttonBgTint
                setTextColor(buttonTextTint)
                strokeWidth = 0

                // Adjusted vertical padding for a slightly higher button height (16f)
                setPadding(dpToPx(4f), dpToPx(16f), dpToPx(4f), dpToPx(16f))
                setOnTouchListener(pressScaleTouchListener)
            }

            themeSelectorContainer.addView(optionBtn)
            if (isActive) {
                themeSelectorContainer.check(optionBtn.id)
            }
        }

        themeSelectorContainer.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                pendingTheme = when (checkedId) {
                    group.getChildAt(0).id -> 0
                    group.getChildAt(1).id -> 1
                    group.getChildAt(2).id -> 2
                    else -> savedTheme
                }
            }
        }

        // ----- Round & Monet Apply Theme Button (Matching Selected Tab Color & Increased Height) -----
        val applyThemeBtn = TextView(this).apply {
            text = getString(R.string.themerestart)
            textSize = 14f
            setTextColor(activeTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(accentColor)
            }
            setPadding(dpToPx(24f), dpToPx(16f), dpToPx(24f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                if (savedTheme != pendingTheme) {
                    prefs.edit().putInt("app_theme", pendingTheme).apply()
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

                    val mode = when (pendingTheme) {
                        0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    AppCompatDelegate.setDefaultNightMode(mode)

                    overridePendingTransition(0, 0)
                    recreate()
                    overridePendingTransition(0, 0)
                } else {
                    Toast.makeText(this@NotesMainAct, "Selected theme is already applied", Toast.LENGTH_SHORT).show()
                }
            }
        }

        themeCard.addView(themeTitle)
        themeCard.addView(themeSelectorContainer)
        themeCard.addView(applyThemeBtn)
        settingsContentLayout.addView(themeCard)
        settingsScrollView.addView(settingsContentLayout)
        settingsContainer.addView(settingsScrollView)

        contentHolder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(notesContainer)
            addView(settingsContainer)
        }
        rootFrame.addView(contentHolder)

        // ----- Top Bar Layout -----
        topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dpToPx(16f), dpToPx(8f), dpToPx(16f), dpToPx(8f))
        }

        // App Name Pill (Left)
        appNamePill = TextView(this).apply {
            text = if (isNotesActive) getString(R.string.nav_notes) else getString(R.string.nav_settings)
            textSize = 16f
            setTextColor(activeTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
            }
            setPadding(dpToPx(22f), dpToPx(12f), dpToPx(22f), dpToPx(12f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        }

        val tintDrawableFunc: (android.graphics.drawable.Drawable?, Int) -> android.graphics.drawable.Drawable? = { drawable, color ->
            drawable?.let {
                val wrapped = DrawableCompat.wrap(it).mutate()
                DrawableCompat.setTint(wrapped, color)
                wrapped
            }
        }

        // Menu Button Container (Right)
        val topBarRefreshContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cardBgColor)
            }
            layoutParams = FrameLayout.LayoutParams(dpToPx(50f), dpToPx(50f), Gravity.END or Gravity.CENTER_VERTICAL)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Empty as requested
            }
            setOnTouchListener(pressScaleTouchListener)
        }

        val menuDrawable = try { ContextCompat.getDrawable(this, R.drawable.menu_24px) } catch (e: Exception) { null }

        val topBarRefreshIcon = ImageView(this).apply {
            setImageDrawable(tintDrawableFunc(menuDrawable, activeTextColor))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(26f),
                dpToPx(26f),
                Gravity.CENTER
            )
        }

        topBarRefreshContainer.addView(topBarRefreshIcon)
        topBarLayout.addView(appNamePill)
        topBarLayout.addView(topBarRefreshContainer)
        rootFrame.addView(topBarLayout)

        // ----- Dim Background Overlay for Menu -----
        dimOverlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            visibility = View.GONE
            setOnClickListener {
                if (isMenuExpanded) closeMenu()
            }
        }
        rootFrame.addView(dimOverlay)

        // ----- Expanding Menu Container -----
        menuOverlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            )
        }

        val createPillBackground = {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
            }
        }

        val menuItemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(12f)
            gravity = Gravity.END
        }

        val editNoteDrawable = try { ContextCompat.getDrawable(this, R.drawable.edit_note_24px) } catch (e: Exception) { null }
        val boxAddDrawable = try { ContextCompat.getDrawable(this, R.drawable.box_add_24px) } catch (e: Exception) { null }

        val createIcon = tintDrawableFunc(editNoteDrawable, activeTextColor)
        val importIcon = tintDrawableFunc(boxAddDrawable, activeTextColor)

        // Menu Item: Create Notes Pill
        val createNotesItem = TextView(this).apply {
            text = getString(R.string.create_notes)
            textSize = 14f
            setTextColor(activeTextColor)
            setTypeface(null, Typeface.BOLD)
            background = createPillBackground()
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(24f), dpToPx(14f))
            compoundDrawablePadding = dpToPx(12f)
            setCompoundDrawablesWithIntrinsicBounds(createIcon, null, null, null)
            layoutParams = menuItemParams
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                closeMenu()
                Toast.makeText(this@NotesMainAct, "Create Notes clicked", Toast.LENGTH_SHORT).show()
            }
        }

        // Menu Item: Import Notes Pill
        val importNotesItem = TextView(this).apply {
            text = getString(R.string.import_notes)
            textSize = 14f
            setTextColor(activeTextColor)
            setTypeface(null, Typeface.BOLD)
            background = createPillBackground()
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(24f), dpToPx(14f))
            compoundDrawablePadding = dpToPx(12f)
            setCompoundDrawablesWithIntrinsicBounds(importIcon, null, null, null)
            layoutParams = menuItemParams
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                closeMenu()
                Toast.makeText(this@NotesMainAct, "Import Notes clicked", Toast.LENGTH_SHORT).show()
            }
        }

        menuOverlayContainer.addView(createNotesItem)
        menuOverlayContainer.addView(importNotesItem)
        rootFrame.addView(menuOverlayContainer)

        // ----- Bottom bar layout -----
        bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        }

        // ----- Tab pill container -----
        val tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        }

        // Sliding pill background
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

        // Notes tab
        notesTabBtn = TextView(this).apply {
            text = getString(R.string.nav_notes)
            textSize = 14f
            setTextColor(if (isNotesActive) activeTextColor else secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(20f), 0)
            compoundDrawablePadding = dpToPx(8f)
            if (isNotesActive) {
                setCompoundDrawablesWithIntrinsicBounds(notesDrawable, null, null, null)
            } else {
                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
        }

        // Settings tab
        settingsTabBtn = TextView(this).apply {
            text = getString(R.string.nav_settings)
            textSize = 14f
            setTextColor(if (!isNotesActive) activeTextColor else secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(20f), 0)
            compoundDrawablePadding = dpToPx(8f)
            if (!isNotesActive) {
                setCompoundDrawablesWithIntrinsicBounds(settingsDrawable, null, null, null)
            } else {
                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
        }

        tabButtonsLayout.addView(notesTabBtn)
        tabButtonsLayout.addView(settingsTabBtn)

        tabPillContainer.addView(slidingPillView)
        tabPillContainer.addView(tabButtonsLayout)

        // ----- + Button (FAB-like) -----
        plusIconDrawable = PlusDrawable(activeTextColor, dpToPx(3f).toFloat())
        plusBtnRef = ImageView(this).apply {
            setImageDrawable(plusIconDrawable)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cardBgColor)
            }
            contentDescription = "Add"
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(52f), dpToPx(52f)).apply {
                marginStart = dpToPx(12f)
            }
            setOnClickListener {
                if (isMenuExpanded) {
                    closeMenu()
                } else {
                    openMenu()
                }
            }
            setOnTouchListener(pressScaleTouchListener)
        }

        bottomBarLayout.addView(tabPillContainer)
        bottomBarLayout.addView(plusBtnRef)
        rootFrame.addView(bottomBarLayout)

        setContentView(rootFrame)

        // ----- Tab switching logic & Pill animation -----
        val tintDrawableColor: (TextView, Int) -> Unit = { textView, color ->
            val drawables = textView.compoundDrawables
            val left = drawables[0]?.let {
                val wrapped = DrawableCompat.wrap(it).mutate()
                DrawableCompat.setTint(wrapped, color)
                wrapped
            }
            textView.setCompoundDrawablesWithIntrinsicBounds(left, drawables[1], drawables[2], drawables[3])
        }

        if (isNotesActive) {
            tintDrawableColor(notesTabBtn, activeTextColor)
        } else {
            tintDrawableColor(settingsTabBtn, activeTextColor)
        }

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
                notesTabBtn.setCompoundDrawablesWithIntrinsicBounds(notesDrawable, null, null, null)
                tintDrawableColor(notesTabBtn, activeTextColor)

                settingsTabBtn.setTextColor(secondaryTextColor)
                settingsTabBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            } else {
                notesTabBtn.setTextColor(secondaryTextColor)
                notesTabBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

                settingsTabBtn.setTextColor(activeTextColor)
                settingsTabBtn.setCompoundDrawablesWithIntrinsicBounds(settingsDrawable, null, null, null)
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
                appNamePill.text = if (toNotes) getString(R.string.nav_notes) else getString(R.string.nav_settings)
            }
        }

        // ----- Click listeners for tabs -----
        notesTabBtn.setOnClickListener {
            if (!isNotesActive) {
                notesTabBtn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                animatePillTo(0f) { switchTab(true) }
            }
        }

        settingsTabBtn.setOnClickListener {
            if (isNotesActive) {
                settingsTabBtn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                animatePillTo(1f) { switchTab(false) }
            }
        }

        // ----- Initial pill layout -----
        tabPillContainer.doOnLayout {
            val targetBtn = if (isNotesActive) notesTabBtn else settingsTabBtn
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = targetBtn.width }
            slidingPillView.translationX = targetBtn.left.toFloat()
            slidingPillView.requestLayout()
        }

        // ----- Window insets (Dynamic Spacing) -----
        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetTop
            }

            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }

            topBarLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = topInset
            }

            contentHolder.setPadding(0, topInset + dpToPx(66f), 0, 0)

            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(16f) + bottomInset
            (menuOverlayContainer.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(88f) + bottomInset
            
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_notes_active", isNotesActive)
    }

    private fun openMenu() {
        isMenuExpanded = true
        plusBtnRef.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

        val rootFrame = menuOverlayContainer.parent as? FrameLayout
        if (rootFrame != null) {
            menuOverlayContainer.measure(
                View.MeasureSpec.makeMeasureSpec(rootFrame.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val menuWidth = menuOverlayContainer.measuredWidth
            val menuHeight = menuOverlayContainer.measuredHeight

            menuOverlayContainer.pivotX = menuWidth.toFloat()
            menuOverlayContainer.pivotY = menuHeight.toFloat()

            val plusCenterX = bottomBarLayout.x + plusBtnRef.x + (plusBtnRef.width / 2f)
            val rightMargin = rootFrame.width - plusCenterX - (menuWidth / 2f)
            val lp = menuOverlayContainer.layoutParams as FrameLayout.LayoutParams
            lp.rightMargin = rightMargin.toInt().coerceAtLeast(dpToPx(16f))
            menuOverlayContainer.layoutParams = lp
        }

        plusBtnRef.animate()
            .rotation(45f)
            .setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
            .start()

        dimOverlay.visibility = View.VISIBLE
        dimOverlay.animate().alpha(1f).setDuration(200).start()

        menuOverlayContainer.visibility = View.VISIBLE
        menuOverlayContainer.alpha = 0f
        menuOverlayContainer.scaleX = 0.8f
        menuOverlayContainer.scaleY = 0.8f
        menuOverlayContainer.translationY = dpToPx(16f).toFloat()

        menuOverlayContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
            .start()
    }

    private fun closeMenu() {
        isMenuExpanded = false
        plusBtnRef.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

        plusBtnRef.animate()
            .rotation(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
            .start()

        dimOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            dimOverlay.visibility = View.GONE
        }.start()

        menuOverlayContainer.pivotX = menuOverlayContainer.width.toFloat()
        menuOverlayContainer.pivotY = menuOverlayContainer.height.toFloat()

        menuOverlayContainer.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .translationY(dpToPx(16f).toFloat())
            .setDuration(200)
            .withEndAction {
                menuOverlayContainer.visibility = View.GONE
            }.start()
    }

    private inner class PlusDrawable(private val colorInt: Int, private val strokeWidthPx: Float) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        override fun draw(canvas: android.graphics.Canvas) {
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val size = dpToPx(9f).toFloat()
            canvas.drawLine(cx - size, cy, cx + size, cy, paint)
            canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}
