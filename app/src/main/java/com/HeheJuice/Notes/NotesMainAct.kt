package com.HeheJuice.Notes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.DynamicColors
import java.io.File

// ----- Data class & Repository -----
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val lastModified: Long
)

object NoteRepository {
    private const val NOTES_DIR = "notes"
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        val dir = getNotesDir()
        if (!dir.exists()) dir.mkdirs()
    }

    private fun getNotesDir(): File = context.filesDir.resolve(NOTES_DIR)

    fun getAllNotes(): List<Note> {
        val dir = getNotesDir()
        return dir.listFiles { file -> file.extension == "txt" }
            ?.mapNotNull { file ->
                val fullText = file.readText()
                val lines = fullText.lines()
                val title = if (lines.isNotEmpty()) lines.first() else file.nameWithoutExtension
                val content = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
                Note(
                    id = file.nameWithoutExtension,
                    title = title,
                    content = content,
                    lastModified = file.lastModified()
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    fun getNote(id: String): Note? {
        val file = getNotesDir().resolve("$id.txt")
        return if (file.exists()) {
            val fullText = file.readText()
            val lines = fullText.lines()
            val title = if (lines.isNotEmpty()) lines.first() else id
            val content = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
            Note(id, title, content, file.lastModified())
        } else null
    }

    fun saveNote(id: String, title: String, content: String) {
        val file = getNotesDir().resolve("$id.txt")
        val fullText = if (content.isNotEmpty()) "$title\n$content" else title
        file.writeText(fullText)
    }

    fun deleteNote(id: String) {
        getNotesDir().resolve("$id.txt").delete()
    }

    fun renameNote(oldId: String, newId: String): Boolean {
        val oldFile = getNotesDir().resolve("$oldId.txt")
        val newFile = getNotesDir().resolve("$newId.txt")
        return oldFile.renameTo(newFile)
    }
}

// ----- Main Activity -----
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

    // Notes list
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var notesAdapter: NotesListAdapter

    // Colors (resolved from theme)
    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceContainerLowColor: Int = 0
    private var surfaceContainerHighestColor: Int = 0
    private var onSurfaceVariantColor: Int = 0

    // GoogleSansFlex typeface
    private var googleSansFlex: Typeface? = null

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

        // Initialize repository
        NoteRepository.init(applicationContext)

        // Load GoogleSansFlex
        googleSansFlex = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (e: Exception) {
            null
        }

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // ----- Status Bar Visibility -----
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !isDark
        windowInsetsController.isAppearanceLightNavigationBars = !isDark

        // ----- Resolve all required container colors from the current theme -----
        val typedValue = TypedValue()

        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
        primaryContainerColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
        onPrimaryContainerColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
        surfaceContainerColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerLow, typedValue, true)
        surfaceContainerLowColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true)
        surfaceContainerHighestColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        onSurfaceVariantColor = typedValue.data

        // ----- Root background: Surface Container -----
        val rootFrame = FrameLayout(this).apply { setBackgroundColor(surfaceContainerColor) }

        // ----- Content containers -----
        notesContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = if (isNotesActive) View.VISIBLE else View.GONE

            val rv = RecyclerView(this@NotesMainAct).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(this@NotesMainAct)
            }
            notesRecyclerView = rv
            addView(rv)
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
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(100f))
        }

        // ----- App Theme Card -----
        val themeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24f).toFloat()
                setColor(surfaceContainerLowColor)
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
            setTextColor(onSurfaceVariantColor)
            applyGoogleSansBoldRound(this)  // ← apply font
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14f)
            }
        }

        // --- Expressive Toggle Group with Surface Container Highest for unselected ---
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
            com.google.android.material.R.style.Widget_Material3Expressive_Button_OutlinedButton
        )

        // Color state lists: selected = Primary Container, unselected = Surface Container Highest
        val buttonBgTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(primaryContainerColor, surfaceContainerHighestColor)
        )

        val buttonTextTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(onPrimaryContainerColor, onSurfaceVariantColor)
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

        // ----- Apply Theme Button (Primary Container) -----
        val applyThemeBtn = TextView(this).apply {
            text = getString(R.string.themerestart)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(primaryContainerColor)
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

        // --- Continue with rest of UI ---
        contentHolder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(notesContainer)
            addView(settingsContainer)
        }
        rootFrame.addView(contentHolder)

        // ----- Top Bar Layout (padding reduced to 12dp) -----
        topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
        }

        // App Name Pill (Left) – Surface Container Low
        appNamePill = TextView(this).apply {
            text = if (isNotesActive) getString(R.string.nav_notes) else getString(R.string.nav_settings)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            applyGoogleSansBoldRound(this)  // ← apply font
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(surfaceContainerLowColor)
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

        // Menu Button Container (Right) – Surface Container Low
        val topBarRefreshContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceContainerLowColor)
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
            setImageDrawable(tintDrawableFunc(menuDrawable, onSurfaceVariantColor))
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

        // ----- Expanding Menu Container (Floating Pills now use Primary Container) -----
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
                setColor(primaryContainerColor)
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

        val createIcon = tintDrawableFunc(editNoteDrawable, onPrimaryContainerColor)
        val importIcon = tintDrawableFunc(boxAddDrawable, onPrimaryContainerColor)

        // Menu Item: Create Notes Pill
        val createNotesItem = TextView(this).apply {
            text = getString(R.string.create_notes)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
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
                val intent = Intent(this@NotesMainAct, NoteEditorActivity::class.java)
                startActivity(intent)
            }
        }

        // Menu Item: Import Notes Pill
        val importNotesItem = TextView(this).apply {
            text = getString(R.string.import_notes)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
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
                Toast.makeText(this@NotesMainAct, getString(R.string.import_notes_toast), Toast.LENGTH_SHORT).show()
            }
        }

        menuOverlayContainer.addView(createNotesItem)
        menuOverlayContainer.addView(importNotesItem)
        rootFrame.addView(menuOverlayContainer)

        // ----- Bottom bar layout (background Surface Container Low) -----
        bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        }

        // ----- Tab pill container (background Surface Container Low) -----
        val tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(surfaceContainerLowColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        }

        // Sliding pill background – Primary Container
        slidingPillView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(primaryContainerColor)
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
            setTextColor(if (isNotesActive) onPrimaryContainerColor else onSurfaceVariantColor)
            applyGoogleSansBoldRound(this)  // ← apply font
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
            setTextColor(if (!isNotesActive) onPrimaryContainerColor else onSurfaceVariantColor)
            applyGoogleSansBoldRound(this)  // ← apply font
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
        plusIconDrawable = PlusDrawable(onSurfaceVariantColor, dpToPx(3f).toFloat())
        plusBtnRef = ImageView(this).apply {
            setImageDrawable(plusIconDrawable)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceContainerLowColor)
            }
            contentDescription = getString(R.string.add_button_content_desc)
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

        // ----- Notes list adapter initialization -----
        notesAdapter = NotesListAdapter(emptyList()) { note ->
            val intent = Intent(this, NoteEditorActivity::class.java).apply {
                putExtra("note_id", note.id)
            }
            startActivity(intent)
        }
        notesRecyclerView.adapter = notesAdapter
        loadNotesList()

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
            tintDrawableColor(notesTabBtn, onPrimaryContainerColor)
        } else {
            tintDrawableColor(settingsTabBtn, onPrimaryContainerColor)
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
                notesTabBtn.setTextColor(onPrimaryContainerColor)
                notesTabBtn.setCompoundDrawablesWithIntrinsicBounds(notesDrawable, null, null, null)
                tintDrawableColor(notesTabBtn, onPrimaryContainerColor)

                settingsTabBtn.setTextColor(onSurfaceVariantColor)
                settingsTabBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            } else {
                notesTabBtn.setTextColor(onSurfaceVariantColor)
                notesTabBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

                settingsTabBtn.setTextColor(onPrimaryContainerColor)
                settingsTabBtn.setCompoundDrawablesWithIntrinsicBounds(settingsDrawable, null, null, null)
                tintDrawableColor(settingsTabBtn, onPrimaryContainerColor)
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

    override fun onResume() {
        super.onResume()
        if (::notesAdapter.isInitialized) {
            loadNotesList()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_notes_active", isNotesActive)
    }

    private fun loadNotesList() {
        val notes = NoteRepository.getAllNotes()
        notesAdapter.updateItems(notes)
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

    // Helper to apply GoogleSansFlex Bold + Round
    private fun applyGoogleSansBoldRound(textView: TextView) {
        googleSansFlex?.let { font ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                textView.typeface = Typeface.create(font, 700, false)
                textView.fontVariationSettings = "'wght' 700, 'ROND' 100"
            } else {
                // Fallback to bold
                textView.setTypeface(font, Typeface.BOLD)
            }
        }
    }

    // ----- Inner adapter for notes list with 12dp margins -----
    inner class NotesListAdapter(
        private var items: List<Note>,
        private val onItemClick: (Note) -> Unit
    ) : RecyclerView.Adapter<NotesListAdapter.ViewHolder>() {

        inner class ViewHolder(val card: TextView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                textSize = 16f
                setTextColor(onPrimaryContainerColor)
                setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(12f).toFloat()
                    setColor(surfaceContainerLowColor)
                }
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8f)
                    leftMargin = dpToPx(12f)   // matches top bar padding
                    rightMargin = dpToPx(12f)
                }
                isClickable = true
                isFocusable = true
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = items[position]
            holder.card.text = note.title
            holder.card.setOnClickListener { onItemClick(note) }
            holder.card.setOnLongClickListener {
                NoteRepository.deleteNote(note.id)
                loadNotesList()
                true
            }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<Note>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    // ----- Plus drawable helper -----
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