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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val spans: List<SpanData>,
    val lastModified: Long
)

data class SpanData(
    val start: Int,
    val end: Int,
    val type: String,
    val size: Float? = null
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

    private fun sanitizeTitle(title: String): String {
        return title.replace(Regex("[^a-zA-Z0-9\\-\\_ ]"), "")
            .replace(" ", "_")
            .trim()
            .ifEmpty { "note" }
    }

    private fun generateId(title: String): String {
        val sanitized = sanitizeTitle(title)
        return "$sanitized---${System.currentTimeMillis()}"
    }

    private fun getTitleFromFilename(filename: String): String {
        val parts = filename.split("---")
        return if (parts.isNotEmpty()) parts[0] else filename
    }

    private fun getMetaFile(id: String): File = getNotesDir().resolve("$id.meta")

    fun getAllNotes(): List<Note> {
        val dir = getNotesDir()
        return dir.listFiles { file -> file.extension == "txt" }
            ?.mapNotNull { file ->
                val id = file.nameWithoutExtension
                val plainText = file.readText()
                val title = getTitleFromFilename(id)
                val metaFile = getMetaFile(id)
                val spans = if (metaFile.exists()) {
                    parseSpans(metaFile.readText())
                } else {
                    emptyList()
                }
                Note(
                    id = id,
                    title = title,
                    content = plainText,
                    spans = spans,
                    lastModified = file.lastModified()
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    fun getNote(id: String): Note? {
        val file = getNotesDir().resolve("$id.txt")
        val metaFile = getMetaFile(id)
        return if (file.exists()) {
            val plainText = file.readText()
            val title = getTitleFromFilename(id)
            val spans = if (metaFile.exists()) {
                parseSpans(metaFile.readText())
            } else {
                emptyList()
            }
            Note(id, title, plainText, spans, file.lastModified())
        } else null
    }

    fun saveNote(title: String, plainText: String, spans: List<SpanData>) {
        val id = generateId(title)
        val file = getNotesDir().resolve("$id.txt")
        val metaFile = getMetaFile(id)

        file.writeText(plainText)
        if (spans.isNotEmpty()) {
            val json = JSONArray()
            for (span in spans) {
                val obj = JSONObject()
                obj.put("start", span.start)
                obj.put("end", span.end)
                obj.put("type", span.type)
                span.size?.let { obj.put("size", it) }
                json.put(obj)
            }
            metaFile.writeText(json.toString())
        } else {
            metaFile.delete()
        }
    }

    fun deleteNote(id: String) {
        getNotesDir().resolve("$id.txt").delete()
        getMetaFile(id).delete()
    }

    fun renameNote(oldId: String, newTitle: String): Boolean {
        val oldFile = getNotesDir().resolve("$oldId.txt")
        val oldMeta = getMetaFile(oldId)
        val newId = generateId(newTitle)
        val newFile = getNotesDir().resolve("$newId.txt")
        val newMeta = getMetaFile(newId)
        val result = oldFile.renameTo(newFile)
        if (oldMeta.exists()) oldMeta.renameTo(newMeta)
        return result
    }

    private fun parseSpans(jsonString: String): List<SpanData> {
        val list = mutableListOf<SpanData>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val start = obj.getInt("start")
                val end = obj.getInt("end")
                val type = obj.getString("type")
                val size = if (obj.has("size")) obj.getDouble("size").toFloat() else null
                list.add(SpanData(start, end, type, size))
            }
        } catch (_: Exception) { /* ignore */ }
        return list
    }
}

class NotesMainAct : AppCompatActivity() {

    private lateinit var slidingPillView: View
    private lateinit var notesTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var notesContainer: FrameLayout
    private lateinit var settingsContainer: FrameLayout
    private var isNotesActive = true

    private var isMenuExpanded = false
    private lateinit var menuOverlayContainer: LinearLayout
    private lateinit var bottomBarLayout: LinearLayout
    private lateinit var dimOverlay: View
    private lateinit var plusIconDrawable: PlusDrawable
    private lateinit var plusBtnRef: ImageView

    private lateinit var topBarLayout: FrameLayout
    private lateinit var contentHolder: FrameLayout
    private lateinit var appNamePill: TextView

    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var notesAdapter: NotesListAdapter

    // Theme-related variables
    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceContainerLowColor: Int = 0
    private var surfaceContainerHighestColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var redColor: Int = 0
    private var googleSansFlex: Typeface? = null

    // Theme preferences
    private lateinit var prefs: android.content.SharedPreferences
    private var followSystem: Boolean = true
    private var themeMode: Int = 0  // 0 = Light, 1 = Dark

    // Store top inset for dynamic padding adjustment
    private var currentTopInset = 0

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        val springBackInterpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.88f)
                    .setDuration(120).setInterpolator(DecelerateInterpolator(1.5f)).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                    .setDuration(350).setInterpolator(springBackInterpolator).start()
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getPreferences(Context.MODE_PRIVATE)
        followSystem = prefs.getBoolean("follow_system", true)
        themeMode = prefs.getInt("theme_mode", 0)

        applyThemeFromPrefs()

        if (savedInstanceState != null) {
            isNotesActive = savedInstanceState.getBoolean("is_notes_active", true)
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        DynamicColors.applyToActivityIfAvailable(this)
        NoteRepository.init(applicationContext)

        googleSansFlex = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (_: Exception) { null }

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !isDark
        windowInsetsController.isAppearanceLightNavigationBars = !isDark

        // ---- COLOR RESOLUTION using MaterialColors ----
        primaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.parseColor("#4F378B"))
        surfaceContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, Color.parseColor("#FEF7FF"))
        surfaceContainerLowColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.parseColor("#F2F2F7"))
        surfaceContainerHighestColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.parseColor("#FFFFFF"))
        onSurfaceVariantColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.parseColor("#49454F"))

        redColor = MaterialColors.getColor(this, android.R.attr.colorError, Color.parseColor("#FF3B30"))
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.parseColor("#FEF7FF"))
        // -------------------------------------------------

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(surfaceColor) }

        // ---- TOP BAR (Big Title for Notes Page, without "more" button) ----
        topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(8f))
        }

        appNamePill = TextView(this).apply {
            text = getString(R.string.nav_notes)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(16f)
                marginStart = dpToPx(8f)
            }
        }

        topBarLayout.addView(appNamePill)

        // ---- NOTES CONTAINER ----
        notesContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = if (isNotesActive) View.VISIBLE else View.GONE

            val rv = RecyclerView(this@NotesMainAct).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(this@NotesMainAct)
                setPadding(0, dpToPx(130f), 0, dpToPx(100f))
                clipToPadding = false
            }
            notesRecyclerView = rv

            addView(rv)
            addView(topBarLayout)
        }

        settingsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = if (isNotesActive) View.GONE else View.VISIBLE
        }

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
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }

        // ---- ADD LARGE SETTINGS TITLE ----
        val bigSettingsTitle = TextView(this).apply {
            text = getString(R.string.nav_settings)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(16f)
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
        }
        settingsContentLayout.addView(bigSettingsTitle)

        // ================================================================
        // THEME SETTINGS CARD (General group)
        // ================================================================
        val themeGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createGroupItemBg(topRadius: Float, bottomRadius: Float): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(surfaceContainerHighestColor)
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    bottomRadius, bottomRadius,
                    bottomRadius, bottomRadius
                )
            }
        }

        val outerRadiusPx = dpToPx(16f).toFloat()
        val innerRadiusPx = dpToPx(4f).toFloat()

        // Category Header
        val categoryHeader = TextView(this).apply {
            text = getString(R.string.category_general)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            setPadding(dpToPx(8f), dpToPx(12f), dpToPx(8f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        themeGroup.addView(categoryHeader)

        // 1. Follow system switch row
        val followSystemRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGroupItemBg(outerRadiusPx, innerRadiusPx)
            setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(2f)
            }
            setMinimumHeight(dpToPx(56f))
        }

        val followTextContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val followLabel = TextView(this).apply {
            text = getString(R.string.setting_follow_system)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val followSubtitle = TextView(this).apply {
            text = getString(R.string.setting_follow_system_subtitle)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2f)
            }
        }

        followTextContainer.addView(followLabel)
        followTextContainer.addView(followSubtitle)

        val accentColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#E8DEF8"))
        val trackOnColor = accentColor
        val trackOffColor = if (isDark) {
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, Color.parseColor("#1C1B1F"))
        } else {
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.parseColor("#E6E0E9"))
        }
        val thumbOnColor = if (isDark) {
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        } else {
            Color.WHITE
        }
        val thumbOffColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.parseColor("#79747E"))

        val followSwitch = LayoutInflater.from(this)
            .inflate(R.layout.switch_material, null) as MaterialSwitch

        val trackStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val trackColors = intArrayOf(trackOnColor, trackOffColor)
        followSwitch.trackTintList = ColorStateList(trackStates, trackColors)

        val thumbStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val thumbColors = intArrayOf(thumbOnColor, thumbOffColor)
        followSwitch.thumbTintList = ColorStateList(thumbStates, thumbColors)

        val iconStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val iconColors = intArrayOf(accentColor, trackOffColor)
        followSwitch.thumbIconTintList = ColorStateList(iconStates, iconColors)

        val outlineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.GRAY)
        followSwitch.trackDecorationTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(Color.TRANSPARENT, outlineColor)
        )

        followSwitch.isChecked = followSystem

        followSystemRow.addView(followTextContainer)
        followSystemRow.addView(followSwitch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        themeGroup.addView(followSystemRow)

        // 2. Theme Mode row
        val themeModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGroupItemBg(innerRadiusPx, outerRadiusPx)
            setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setMinimumHeight(dpToPx(56f))
        }

        val themeModeLabel = TextView(this).apply {
            text = getString(R.string.setting_theme_mode)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val themeModeSubtitle = TextView(this).apply {
            text = if (themeMode == 0) getString(R.string.theme_light) else getString(R.string.theme_dark)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        themeModeRow.addView(themeModeLabel)
        themeModeRow.addView(themeModeSubtitle)

        themeModeRow.isClickable = true
        themeModeRow.isFocusable = true
        themeModeRow.setOnTouchListener(pressScaleTouchListener)
        themeModeRow.setOnClickListener { view ->
            if (!followSystem) {
                showThemeModePopup(view)
            }
        }
        themeGroup.addView(themeModeRow)

        fun updateRowState() {
            val enabled = !followSystem
            themeModeRow.isEnabled = enabled
            themeModeRow.alpha = if (enabled) 1.0f else 0.5f
            if (enabled) {
                themeModeSubtitle.text = if (themeMode == 0) getString(R.string.theme_light) else getString(R.string.theme_dark)
                themeModeSubtitle.setTextColor(onSurfaceVariantColor)
            } else {
                themeModeSubtitle.text = getString(R.string.theme_system)
                themeModeSubtitle.setTextColor(onSurfaceVariantColor)
            }
        }
        updateRowState()

        followSwitch.setOnCheckedChangeListener { _, isChecked ->
            followSystem = isChecked
            prefs.edit().putBoolean("follow_system", followSystem).apply()
            updateRowState()
            applyThemeFromPrefs()
            followSwitch.postDelayed({
                recreate()
            }, 200)
        }

        settingsContentLayout.addView(themeGroup)

        // ================================================================
        // EDITOR SETTINGS CARD
        // ================================================================
        val editorSectionTitle = TextView(this).apply {
            text = getString(R.string.settings_editor_category)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24f)
                bottomMargin = dpToPx(8f)
                marginStart = dpToPx(12f)
            }
        }

        val editorSettingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f).toFloat()
                setColor(surfaceContainerHighestColor)
            }
            setPadding(dpToPx(16f), dpToPx(4f), dpToPx(16f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val prefsNotes = getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
        val showLinesSwitch = LayoutInflater.from(this)
            .inflate(R.layout.switch_material, null) as MaterialSwitch
        showLinesSwitch.isChecked = prefsNotes.getBoolean("show_lines_under_text", false)
        showLinesSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefsNotes.edit().putBoolean("show_lines_under_text", isChecked).apply()
        }

        val showLinesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4f), dpToPx(8f), dpToPx(4f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setMinimumHeight(dpToPx(48f))
        }

        val linesTextContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val linesLabel = TextView(this).apply {
            text = getString(R.string.show_lines_under_text)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val linesSubtitle = TextView(this).apply {
            text = getString(R.string.show_lines_under_text_desc)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2f)
            }
        }

        linesTextContainer.addView(linesLabel)
        linesTextContainer.addView(linesSubtitle)

        showLinesSwitch.trackTintList = ColorStateList(trackStates, trackColors)
        showLinesSwitch.thumbTintList = ColorStateList(thumbStates, thumbColors)
        showLinesSwitch.thumbIconTintList = ColorStateList(iconStates, iconColors)
        showLinesSwitch.trackDecorationTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(Color.TRANSPARENT, outlineColor)
        )

        showLinesRow.addView(linesTextContainer)
        showLinesRow.addView(showLinesSwitch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        editorSettingsCard.addView(showLinesRow)
        settingsContentLayout.addView(editorSectionTitle)
        settingsContentLayout.addView(editorSettingsCard)

        // ================================================================
        // NETWORK & ABOUT SETTINGS CARD
        // ================================================================
        val networkAboutSectionTitle = TextView(this).apply {
            text = getString(R.string.settings_network_about_category)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24f)
                bottomMargin = dpToPx(8f)
                marginStart = dpToPx(12f)
            }
        }

        val networkAboutGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. App Network Connection Row
        val appNetworkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGroupItemBg(outerRadiusPx, innerRadiusPx)
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(2f)
            }
            setMinimumHeight(dpToPx(56f))
        }

        val networkTextContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val networkLabel = TextView(this).apply {
            text = getString(R.string.setting_app_network)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
        }

        val networkSubtitle = TextView(this).apply {
            text = getString(R.string.setting_app_network_desc)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2f)
            }
        }

        networkTextContainer.addView(networkLabel)
        networkTextContainer.addView(networkSubtitle)

        val appNetworkSwitch = LayoutInflater.from(this)
            .inflate(R.layout.switch_material, null) as MaterialSwitch

        appNetworkSwitch.trackTintList = ColorStateList(trackStates, trackColors)
        appNetworkSwitch.thumbTintList = ColorStateList(thumbStates, thumbColors)
        appNetworkSwitch.thumbIconTintList = ColorStateList(iconStates, iconColors)
        appNetworkSwitch.trackDecorationTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(Color.TRANSPARENT, outlineColor)
        )

        appNetworkSwitch.isChecked = prefsNotes.getBoolean("app_network_connection", true)
        appNetworkSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefsNotes.edit().putBoolean("app_network_connection", isChecked).apply()
        }

        appNetworkRow.addView(networkTextContainer)
        appNetworkRow.addView(appNetworkSwitch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 2. Check for Updates & About App Row
        val updatesAndAboutRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGroupItemBg(innerRadiusPx, outerRadiusPx)
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setMinimumHeight(dpToPx(56f))
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                val intent = Intent(this@NotesMainAct, NotesUpdate::class.java)
                startActivity(intent)
            }
        }

        val updatesTextContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val updatesLabel = TextView(this).apply {
            text = getString(R.string.setting_updates_and_about)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
        }

        val updatesSubtitle = TextView(this).apply {
            text = getString(R.string.setting_updates_and_about_desc)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2f)
            }
        }

        updatesTextContainer.addView(updatesLabel)
        updatesTextContainer.addView(updatesSubtitle)
        updatesAndAboutRow.addView(updatesTextContainer)

        networkAboutGroup.addView(appNetworkRow)
        networkAboutGroup.addView(updatesAndAboutRow)

        settingsContentLayout.addView(networkAboutSectionTitle)
        settingsContentLayout.addView(networkAboutGroup)

        settingsScrollView.addView(settingsContentLayout)
        settingsContainer.addView(settingsScrollView)

        // ---- Content holder ----
        contentHolder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(notesContainer)
            addView(settingsContainer)
        }
        rootFrame.addView(contentHolder)

        // ---- Dim overlay ----
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

        // ---- Menu overlay (FAB popup) ----
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

        val tintDrawableFunc: (android.graphics.drawable.Drawable?, Int) -> android.graphics.drawable.Drawable? = { drawable, color ->
            drawable?.let {
                val wrapped = DrawableCompat.wrap(it).mutate()
                DrawableCompat.setTint(wrapped, color)
                wrapped
            }
        }

        val editNoteDrawable = try { ContextCompat.getDrawable(this, R.drawable.edit_note_24px) } catch (_: Exception) { null }
        val boxAddDrawable = try { ContextCompat.getDrawable(this, R.drawable.box_add_24px) } catch (_: Exception) { null }
        val createIcon = tintDrawableFunc(editNoteDrawable, onPrimaryContainerColor)
        val importIcon = tintDrawableFunc(boxAddDrawable, onPrimaryContainerColor)

        val createNotesItem = TextView(this).apply {
            text = getString(R.string.create_notes)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
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
        val importNotesItem = TextView(this).apply {
            text = getString(R.string.import_notes)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
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

        // ---- Bottom bar ----
        bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        }
        val tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(surfaceContainerLowColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        }
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
        val notesDrawable = try { ContextCompat.getDrawable(this, R.drawable.note_stack_24px) } catch (_: Exception) { null }
        val settingsDrawable = try { ContextCompat.getDrawable(this, R.drawable.settings_24px) } catch (_: Exception) { null }

        notesTabBtn = TextView(this).apply {
            text = getString(R.string.nav_notes)
            textSize = 14f
            setTextColor(if (isNotesActive) onPrimaryContainerColor else onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
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
        settingsTabBtn = TextView(this).apply {
            text = getString(R.string.nav_settings)
            textSize = 14f
            setTextColor(if (!isNotesActive) onPrimaryContainerColor else onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
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
                if (isMenuExpanded) closeMenu() else openMenu()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        bottomBarLayout.addView(tabPillContainer)
        bottomBarLayout.addView(plusBtnRef)
        rootFrame.addView(bottomBarLayout)

        setContentView(rootFrame)

        notesAdapter = NotesListAdapter(emptyList()) { note ->
            val intent = Intent(this, NoteEditorActivity::class.java).apply {
                putExtra("note_id", note.id)
            }
            startActivity(intent)
        }
        notesRecyclerView.adapter = notesAdapter
        loadNotesList()

        val tintDrawableColor: (TextView, Int) -> Unit = { textView, color ->
            val drawables = textView.compoundDrawables
            val left = drawables[0]?.let {
                val wrapped = DrawableCompat.wrap(it).mutate()
                DrawableCompat.setTint(wrapped, color)
                wrapped
            }
            textView.setCompoundDrawablesWithIntrinsicBounds(left, drawables[1], drawables[2], drawables[3])
        }
        if (isNotesActive) tintDrawableColor(notesTabBtn, onPrimaryContainerColor)
        else tintDrawableColor(settingsTabBtn, onPrimaryContainerColor)

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
                addUpdateListener { anim -> updatePillPosition(anim.animatedValue as Float) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { onEnd() }
                })
                start()
            }
        }

        val switchTab: (Boolean) -> Unit = { toNotes ->
            if (isNotesActive != toNotes) {
                isNotesActive = toNotes
                notesContainer.visibility = if (toNotes) View.VISIBLE else View.GONE
                settingsContainer.visibility = if (toNotes) View.GONE else View.VISIBLE

                contentHolder.setPadding(0, currentTopInset, 0, 0)
            }
        }

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

        tabPillContainer.doOnLayout {
            val targetBtn = if (isNotesActive) notesTabBtn else settingsTabBtn
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = targetBtn.width }
            slidingPillView.translationX = targetBtn.left.toFloat()
            slidingPillView.requestLayout()
        }

        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val statusBarInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetTop
            }
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }

            currentTopInset = statusBarInset

            contentHolder.setPadding(0, currentTopInset, 0, 0)

            val totalBottomMargin = dpToPx(16f) + bottomInset
            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin = totalBottomMargin
            (menuOverlayContainer.layoutParams as FrameLayout.LayoutParams).bottomMargin = totalBottomMargin + dpToPx(60f)

            insets
        }
    }

    private fun getRelativeTimeSpan(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = (now - timestamp).coerceAtLeast(0)
        val minutes = diffMillis / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        val timeString = when {
            minutes < 1 -> context.getString(R.string.time_now)
            hours < 1 -> context.getString(R.string.time_minutes_ago, minutes)
            days < 1 -> context.getString(R.string.time_hours_ago, hours)
            else -> context.getString(R.string.time_days_ago, days)
        }
        return context.getString(R.string.last_edit_time, timeString)
    }

    private fun applyThemeFromPrefs() {
        val mode = if (followSystem) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            if (themeMode == 0) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showThemeModePopup(anchor: View) {
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(dpToPx(16f).toFloat())
                setColor(primaryContainerColor)
            }
            elevation = dpToPx(8f).toFloat()
        }

        var popupWindow: PopupWindow? = null

        val lightItem = TextView(this).apply {
            text = getString(R.string.theme_light)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(24f), dpToPx(14f), dpToPx(24f), dpToPx(14f))
            background = GradientDrawable().apply {
                setCornerRadius(dpToPx(16f).toFloat())
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                themeMode = 0
                prefs.edit().putInt("theme_mode", themeMode).apply()
                applyThemeFromPrefs()
                recreate()
                popupWindow?.dismiss()
            }
        }
        popupView.addView(lightItem)

        val darkItem = TextView(this).apply {
            text = getString(R.string.theme_dark)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(24f), dpToPx(14f), dpToPx(24f), dpToPx(14f))
            background = GradientDrawable().apply {
                setCornerRadius(dpToPx(16f).toFloat())
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                themeMode = 1
                prefs.edit().putInt("theme_mode", themeMode).apply()
                applyThemeFromPrefs()
                recreate()
                popupWindow?.dismiss()
            }
        }
        popupView.addView(darkItem)

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dpToPx(8f).toFloat()
            isOutsideTouchable = true
            isFocusable = true
            animationStyle = android.R.style.Animation_Dialog
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            showAsDropDown(anchor, anchor.width - popupView.measuredWidth, dpToPx(8f))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::notesAdapter.isInitialized) loadNotesList()
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
        plusBtnRef.animate().rotation(45f).setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
        dimOverlay.visibility = View.VISIBLE
        dimOverlay.animate().alpha(1f).setDuration(200).start()
        menuOverlayContainer.visibility = View.VISIBLE
        menuOverlayContainer.alpha = 0f
        menuOverlayContainer.scaleX = 0.8f
        menuOverlayContainer.scaleY = 0.8f
        menuOverlayContainer.translationY = dpToPx(16f).toFloat()
        menuOverlayContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
    }

    private fun closeMenu() {
        isMenuExpanded = false
        plusBtnRef.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        plusBtnRef.animate().rotation(0f).setDuration(250)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
        dimOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            dimOverlay.visibility = View.GONE
        }.start()
        menuOverlayContainer.pivotX = menuOverlayContainer.width.toFloat()
        menuOverlayContainer.pivotY = menuOverlayContainer.height.toFloat()
        menuOverlayContainer.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).translationY(dpToPx(16f).toFloat())
            .setDuration(200).withEndAction {
                menuOverlayContainer.visibility = View.GONE
            }.start()
    }

    private fun setGoogleSansFlexDefault(textView: TextView, bold: Boolean = false) {
        googleSansFlex?.let { font ->
            if (bold) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    textView.typeface = Typeface.create(font, 700, false)
                    textView.fontVariationSettings = "'wght' 700, 'ROND' 100"
                } else {
                    textView.setTypeface(font, Typeface.BOLD)
                }
            } else {
                textView.typeface = font
            }
        }
    }

    inner class NotesListAdapter(
        private var items: List<Note>,
        private val onItemClick: (Note) -> Unit
    ) : RecyclerView.Adapter<NotesListAdapter.ViewHolder>() {
        private val expandedPositions = mutableSetOf<Int>()

        inner class ViewHolder(
            val root: LinearLayout,
            val cardContainer: LinearLayout,
            val titleText: TextView,
            val timeText: TextView,
            val actionContainer: LinearLayout
        ) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8f)
                    leftMargin = dpToPx(12f)
                    rightMargin = dpToPx(12f)
                }
            }

            val cardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(12f).toFloat()
                    setColor(surfaceContainerHighestColor)
                }
                setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                isClickable = true
                isFocusable = true
                isLongClickable = true
            }

            val titleText = TextView(context).apply {
                textSize = 16f
                setTextColor(onPrimaryContainerColor)
                setGoogleSansFlexDefault(this, true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val timeText = TextView(context).apply {
                textSize = 12f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                setGoogleSansFlexDefault(this, false)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(4f)
                }
            }

            cardContainer.addView(titleText)
            cardContainer.addView(timeText)

            val actionContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(8f)
                }
            }

            val deleteBtn = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.delete_24px))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(redColor)
                }
                setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
                layoutParams = LinearLayout.LayoutParams(dpToPx(40f), dpToPx(40f)).apply {
                    marginEnd = dpToPx(8f)
                }
                isClickable = true
                isFocusable = true
                setOnTouchListener(pressScaleTouchListener)
            }

            val cancelBtn = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.close_24px))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(surfaceContainerLowColor)
                }
                setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
                layoutParams = LinearLayout.LayoutParams(dpToPx(40f), dpToPx(40f))
                isClickable = true
                isFocusable = true
                setOnTouchListener(pressScaleTouchListener)
            }

            actionContainer.addView(deleteBtn)
            actionContainer.addView(cancelBtn)

            root.addView(cardContainer)
            root.addView(actionContainer)

            return ViewHolder(root, cardContainer, titleText, timeText, actionContainer)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = items[position]

            holder.titleText.text = note.title
            holder.timeText.text = getRelativeTimeSpan(holder.root.context, note.lastModified)

            holder.cardContainer.setOnClickListener {
                onItemClick(note)
            }

            holder.cardContainer.setOnLongClickListener {
                toggleExpansion(position)
                true
            }

            val deleteBtn = holder.actionContainer.getChildAt(0) as ImageView
            deleteBtn.setOnClickListener {
                NoteRepository.deleteNote(note.id)
                expandedPositions.remove(position)
                loadNotesList()
            }

            val cancelBtn = holder.actionContainer.getChildAt(1) as ImageView
            cancelBtn.setOnClickListener {
                expandedPositions.remove(position)
                notifyItemChanged(position)
            }

            val isExpanded = expandedPositions.contains(position)
            holder.actionContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }

        private fun toggleExpansion(position: Int) {
            if (expandedPositions.contains(position)) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.clear()
                expandedPositions.add(position)
            }
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<Note>) {
            items = newItems
            expandedPositions.clear()
            notifyDataSetChanged()
        }
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

    private fun dpToPx(dp: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}
