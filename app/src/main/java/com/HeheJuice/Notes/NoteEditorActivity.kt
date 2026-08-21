package com.HeheJuice.Notes

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import java.io.File

class LinedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    var showLines: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    fun setLineColor(color: Int) {
        linePaint.color = ColorUtils.setAlphaComponent(color, 50)
        invalidate()
    }

    override fun getText(): Editable {
        return super.getText() ?: SpannableStringBuilder("")
    }

    override fun onDraw(canvas: Canvas) {
        if (showLines && lineCount > 0) {
            val count = lineCount
            val rect = Rect()
            for (i in 0 until count) {
                val baseline = getLineBounds(i, rect)
                canvas.drawLine(
                    paddingLeft.toFloat(),
                    (baseline + 8).toFloat(),
                    (width - paddingRight).toFloat(),
                    (baseline + 8).toFloat(),
                    linePaint
                )
            }
        }
        super.onDraw(canvas)
    }
}

class GoogleSansFlexBoldRoundSpan(private val typeface: Typeface) : android.text.style.TypefaceSpan("sans-serif") {
    override fun updateDrawState(ds: android.text.TextPaint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ds.typeface = Typeface.create(typeface, 800, false)
            ds.fontVariationSettings = "'wght' 800, 'ROND' 100"
        } else {
            ds.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
    }
    override fun updateMeasureState(paint: android.text.TextPaint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.typeface = Typeface.create(typeface, 800, false)
            paint.fontVariationSettings = "'wght' 800, 'ROND' 100"
        } else {
            paint.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
    }
}

class NoteEditorActivity : AppCompatActivity() {

    private data class AudioHolder(
        val filePath: String,
        val uri: Uri,
        var mediaPlayer: MediaPlayer? = null,
        var isPlaying: Boolean = false,
        var playerView: View? = null,
        var playPauseBtnIcon: ImageView? = null,
        var updateShape: ((Boolean) -> Unit)? = null
    )

    private lateinit var titleEdit: EditText
    private lateinit var tagPillContainer: LinearLayout
    private lateinit var tagTextView: TextView
    private lateinit var contentEdit: LinedEditText
    private lateinit var contentWrapper: LinearLayout
    private var noteId: String = ""
    private var isNewNote = true
    private var noteTag: String? = null
    private var googleSansFlex: Typeface? = null

    private val audioHolders = mutableListOf<AudioHolder>()
    private var initialAudioPaths: List<String> = emptyList()
    private lateinit var createAudioItem: TextView
    private var audioDrawable: android.graphics.drawable.Drawable? = null

    private var initialTitle: String = ""
    private var initialContent: String = ""
    private var initialTag: String? = null
    private var initialSpans: List<SpanData> = emptyList()
    // ✅ added to store pin state when editing
    private var initialIsPinned: Boolean = false

    private lateinit var copyBtn: ImageView
    private lateinit var pasteBtn: ImageView
    private lateinit var boldBtn: ImageView
    private lateinit var biggerBtn: ImageView
    private lateinit var toolbarContainer: LinearLayout

    private lateinit var splitButton: LinearLayout
    private lateinit var trailingBg: MaterialButton
    private lateinit var trailingChevronIcon: ImageView
    private var innerCornerPx: Float = 0f
    private var outerCornerPx: Float = 0f
    private var menuPopup: android.widget.PopupWindow? = null
    private var isMenuOpen = false

    private var isFabMenuExpanded = false
    private lateinit var menuOverlayContainer: LinearLayout
    private lateinit var dimOverlay: View
    private lateinit var fabPlusBtn: ImageView
    private lateinit var plusIconDrawable: PlusDrawable

    private var surfaceContainerLowColor: Int = 0
    private var surfaceContainerHighestColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var primaryContainerColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceLow: Int = 0
    private var cardBorderColor: Int = 0

    private val REQUEST_SAVE_IMAGE = 1002
    private var lastGeneratedBitmap: Bitmap? = null

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        val springBackInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
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

    private val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { sourceUri ->
            if (audioHolders.size >= 5) {
                Toast.makeText(this, getString(R.string.max_audio_reached), Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val savedPath = NoteRepository.copyAudioToAppStorage(this, sourceUri)
            if (savedPath != null) {
                val savedUri = Uri.fromFile(File(savedPath))
                addAudioPlayerToEditor(savedUri, savedPath)
            } else {
                Toast.makeText(this, "Failed to copy audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateButtonState(btn: ImageView, enabled: Boolean) {
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1.0f else 0.5f
        val iconColor = if (enabled) onPrimaryContainerColor else Color.parseColor("#888888")
        btn.imageTintList = android.content.res.ColorStateList.valueOf(iconColor)
    }

    private fun updateAddAudioButtonState() {
        if (!::createAudioItem.isInitialized) return
        val maxReached = audioHolders.size >= 5
        createAudioItem.isClickable = !maxReached
        createAudioItem.isFocusable = !maxReached

        val activeColor = if (maxReached) Color.parseColor("#888888") else onPrimaryContainerColor
        createAudioItem.setTextColor(activeColor)

        audioDrawable?.let {
            DrawableCompat.setTint(it, activeColor)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !isDark
        windowInsetsController.isAppearanceLightNavigationBars = !isDark

        DynamicColors.applyToActivityIfAvailable(this)
        NoteRepository.init(applicationContext)

        googleSansFlex = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (_: Exception) { null }

        noteId = intent.getStringExtra("note_id") ?: ""
        isNewNote = noteId.isEmpty()

        surfaceContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF"))
        surfaceLow = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, if (isDark) Color.parseColor("#2B2B2E") else Color.parseColor("#F2F2F7"))
        surfaceContainerHighestColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        primaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, if (isDark) Color.parseColor("#4F378B") else Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))
        onSurfaceVariantColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F"))
        surfaceContainerLowColor = surfaceLow
        cardBorderColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA"))

        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, if (isDark) Color.parseColor("#141218") else Color.parseColor("#FEF7FF"))
        val prefs = getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
        val isShowLinesEnabled = prefs.getBoolean("show_lines_under_text", false)

        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(surfaceColor)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20f), 0, dpToPx(20f), dpToPx(20f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@NoteEditorActivity, R.drawable.arrow_back_24px))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceContainerHighestColor)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(dpToPx(50f), dpToPx(50f))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_content_desc)
            setOnClickListener { handleBackNavigation() }
        }
        topBar.addView(backBtn)

        val topTitle = TextView(this).apply {
            text = if (isNewNote) getString(R.string.new_note) else getString(R.string.edit_note)
            textSize = 22f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
        }
        topBar.addView(topTitle)

        val buttonHeight = dpToPx(52f)
        innerCornerPx = dpToPx(4f).toFloat()
        outerCornerPx = buttonHeight / 2f

        splitButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            )
        }

        val leadingButton = MaterialButton(
            ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_UnelevatedButton)
        ).apply {
            text = getString(R.string.save)
            setTextColor(onPrimaryContainerColor)
            setTypeface(googleSansFlex, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryContainerColor)
            setPadding(dpToPx(18f), 0, dpToPx(14f), 0)
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(outerCornerPx)
                .setBottomLeftCornerSize(outerCornerPx)
                .setTopRightCornerSize(innerCornerPx)
                .setBottomRightCornerSize(innerCornerPx)
                .build()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = dpToPx(2f) }
        }
        leadingButton.setOnClickListener { saveNote() }

        val trailingButtonContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                buttonHeight,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
        }

        trailingBg = MaterialButton(
            ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_UnelevatedButton)
        ).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryContainerColor)
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            insetLeft = 0
            insetRight = 0
            isClickable = false
            isFocusable = false
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(innerCornerPx)
                .setBottomLeftCornerSize(innerCornerPx)
                .setTopRightCornerSize(outerCornerPx)
                .setBottomRightCornerSize(outerCornerPx)
                .build()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val chevronDrawable = ContextCompat.getDrawable(this, R.drawable.expand_more_24px)?.apply {
            DrawableCompat.setTint(this, onPrimaryContainerColor)
        }
        trailingChevronIcon = ImageView(this).apply {
            setImageDrawable(chevronDrawable)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        trailingButtonContainer.addView(trailingBg)
        trailingButtonContainer.addView(trailingChevronIcon)
        trailingButtonContainer.setOnClickListener {
            if (isMenuOpen) menuPopup?.dismiss() else showMenu(splitButton)
        }

        splitButton.addView(leadingButton)
        splitButton.addView(trailingButtonContainer)
        topBar.addView(splitButton)
        mainLayout.addView(topBar)

        val titleLabel = TextView(this).apply {
            text = getString(R.string.title)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4f) }
        }
        mainLayout.addView(titleLabel)

        val titleInputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setCornerRadius(dpToPx(12f).toFloat())
                setColor(surfaceContainerHighestColor)
            }
            setPadding(dpToPx(12f), dpToPx(4f), dpToPx(12f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16f) }
        }

        tagPillContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(primaryContainerColor)
            }
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpToPx(8f) }
        }

        tagTextView = TextView(this).apply {
            textSize = 13f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val closeDrawable = ContextCompat.getDrawable(this, R.drawable.close_24px)?.let {
            val wrapped = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(wrapped, onPrimaryContainerColor)
            wrapped
        }

        val deleteTagBtn = ImageView(this).apply {
            setImageDrawable(closeDrawable)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setPadding(dpToPx(2f), dpToPx(2f), dpToPx(2f), dpToPx(2f))
            layoutParams = LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f)).apply {
                marginStart = dpToPx(4f)
            }
            setOnClickListener {
                noteTag = null
                tagPillContainer.visibility = View.GONE
            }
        }

        tagPillContainer.addView(tagTextView)
        tagPillContainer.addView(deleteTagBtn)
        titleInputContainer.addView(tagPillContainer)

        titleEdit = EditText(this).apply {
            hint = getString(R.string.enter_title_hint)
            setHintTextColor(onSurfaceVariantColor)
            setTextColor(onPrimaryContainerColor)
            textSize = 18f
            filters = arrayOf(InputFilter.LengthFilter(50))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_NEXT
            background = null
            setPadding(dpToPx(4f), dpToPx(10f), dpToPx(4f), dpToPx(10f))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setGoogleSansFlexDefault(this, false)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) disableToolbar() else updateToolbarButtons()
            }
        }
        titleInputContainer.addView(titleEdit)
        mainLayout.addView(titleInputContainer)

        val contentLabel = TextView(this).apply {
            text = getString(R.string.content)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4f) }
        }
        mainLayout.addView(contentLabel)

        val scrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dpToPx(8f) }
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
        }

        contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        contentEdit = LinedEditText(this).apply {
            hint = getString(R.string.write_content_hint)
            setHintTextColor(onSurfaceVariantColor)
            setTextColor(onPrimaryContainerColor)
            textSize = 16f
            background = GradientDrawable().apply {
                setCornerRadius(dpToPx(12f).toFloat())
                setColor(surfaceContainerHighestColor)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            gravity = Gravity.TOP or Gravity.START

            layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
            )

            setGoogleSansFlexDefault(this, false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            showLines = isShowLinesEnabled
            setLineColor(onSurfaceVariantColor)
        }

        contentWrapper.addView(contentEdit)
        scrollContainer.addView(contentWrapper)
        mainLayout.addView(scrollContainer)

        // Formatting Toolbar
        toolbarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(16f) }
        }

        val toolPill = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(dpToPx(100f).toFloat())
                setColor(surfaceContainerLowColor)
            }
            setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createToolButton(drawableRes: Int, onClick: (ImageView) -> Unit): ImageView {
            return ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@NoteEditorActivity, drawableRes))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(primaryContainerColor)
                }
                setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
                layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
                    marginEnd = dpToPx(6f)
                    marginStart = dpToPx(6f)
                }
                isClickable = true
                isFocusable = true
                setOnTouchListener(pressScaleTouchListener)
                setOnClickListener { onClick(this) }
            }
        }

        copyBtn = createToolButton(R.drawable.content_copy_24px) {
            val selectedText = getSelectedText()
            if (selectedText.isNotEmpty()) {
                copyToClipboard(selectedText)
                Toast.makeText(this@NoteEditorActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.select_text_first), Toast.LENGTH_SHORT).show()
            }
        }

        pasteBtn = createToolButton(R.drawable.content_paste_24px) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).text.toString()
                val rawStart = contentEdit.selectionStart
                val rawEnd = contentEdit.selectionEnd
                val selStart = minOf(rawStart, rawEnd)
                val selEnd = maxOf(rawStart, rawEnd)
                val text = contentEdit.text
                if (selStart != selEnd) text.replace(selStart, selEnd, pasteText)
                else text.insert(selStart, pasteText)
            } else {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.nothing_to_paste), Toast.LENGTH_SHORT).show()
            }
        }
        updateButtonState(pasteBtn, true)

        boldBtn = createToolButton(R.drawable.format_bold_24px) { applyBoldRoundToSelection() }
        biggerBtn = createToolButton(R.drawable.title_24px) { applyBiggerRoundToSelection() }

        buttonRow.addView(copyBtn)
        buttonRow.addView(pasteBtn)
        buttonRow.addView(boldBtn)
        buttonRow.addView(biggerBtn)

        toolPill.addView(buttonRow)
        toolbarContainer.addView(toolPill)
        mainLayout.addView(toolbarContainer)

        rootFrame.addView(mainLayout)

        dimOverlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { if (isFabMenuExpanded) closeFabMenu() }
        }
        rootFrame.addView(dimOverlay)

        // FAB Menu Overlay
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

        audioDrawable = ContextCompat.getDrawable(this, R.drawable.audio_file_24px)?.let {
            val wrapped = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(wrapped, onPrimaryContainerColor)
            wrapped
        }

        createAudioItem = TextView(this).apply {
            text = getString(R.string.add_audio)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = createPillBackground()
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
            compoundDrawablePadding = dpToPx(12f)
            setCompoundDrawablesWithIntrinsicBounds(audioDrawable, null, null, null)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12f)
                gravity = Gravity.END
            }
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                if (audioHolders.size < 5) {
                    closeFabMenu()
                    selectAudioLauncher.launch(arrayOf("audio/*"))
                }
            }
        }
        menuOverlayContainer.addView(createAudioItem)

        val tagDrawable = ContextCompat.getDrawable(this, R.drawable.tag_24px)?.let {
            val wrapped = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(wrapped, onPrimaryContainerColor)
            wrapped
        }

        val createTagItem = TextView(this).apply {
            text = getString(R.string.editor_tags)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = createPillBackground()
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
            compoundDrawablePadding = dpToPx(12f)
            setCompoundDrawablesWithIntrinsicBounds(tagDrawable, null, null, null)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12f)
                gravity = Gravity.END
            }
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                closeFabMenu()
                showCreateTagDialog()
            }
        }
        menuOverlayContainer.addView(createTagItem)
        rootFrame.addView(menuOverlayContainer)

        plusIconDrawable = PlusDrawable(onSurfaceVariantColor, dpToPx(3f).toFloat())
        fabPlusBtn = ImageView(this).apply {
            setImageDrawable(plusIconDrawable)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceContainerLowColor)
            }
            contentDescription = getString(R.string.add_button_content_desc)
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(dpToPx(56f), dpToPx(56f)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dpToPx(20f)
                bottomMargin = dpToPx(28f)
            }
            setOnClickListener {
                if (isFabMenuExpanded) closeFabMenu() else openFabMenu()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        rootFrame.addView(fabPlusBtn)

        // ---- Load Note Data ----
        if (!isNewNote) {
            NoteRepository.getNote(noteId)?.let { note ->
                titleEdit.setText(note.title)
                noteTag = note.tag
                // ✅ store initial pin state
                initialIsPinned = note.isPinned
                if (!noteTag.isNullOrEmpty()) {
                    tagTextView.text = noteTag
                    tagPillContainer.visibility = View.VISIBLE
                }
                val spannable = SpannableStringBuilder(note.content)
                for (spanData in note.spans) {
                    when (spanData.type) {
                        "bold" -> {
                            val font = googleSansFlex
                            if (font != null) {
                                spannable.setSpan(
                                    GoogleSansFlexBoldRoundSpan(font),
                                    spanData.start, spanData.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            } else {
                                spannable.setSpan(StyleSpan(Typeface.BOLD), spanData.start, spanData.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                        "bigger" -> {
                            val size = spanData.size ?: 1.85f
                            spannable.setSpan(RelativeSizeSpan(size), spanData.start, spanData.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                contentEdit.setText(spannable)
                contentEdit.invalidate()

                initialAudioPaths = note.audioPaths
                loadAudioFilesAsync(note.audioPaths)
            }
        } else {
            initialAudioPaths = emptyList()
        }

        initialTitle = titleEdit.text.toString()
        initialContent = contentEdit.text.toString()
        initialTag = noteTag
        initialSpans = getCurrentSpans()

        setContentView(rootFrame)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackNavigation() }
        })

        contentEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateToolbarButtons() }
        })
        contentEdit.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) updateToolbarButtons()
            false
        }

        updateToolbarButtons()
        updateAddAudioButtonState()

        ViewCompat.setOnApplyWindowInsetsListener(rootFrame) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardHeight = imeInsets.bottom

            mainLayout.setPadding(
                dpToPx(20f),
                statusBarTop + dpToPx(12f),
                dpToPx(20f),
                dpToPx(20f)
            )

            val lp = toolbarContainer.layoutParams as LinearLayout.LayoutParams
            lp.bottomMargin = keyboardHeight + dpToPx(8f)
            toolbarContainer.layoutParams = lp

            val fabLp = fabPlusBtn.layoutParams as FrameLayout.LayoutParams
            fabLp.bottomMargin = keyboardHeight + dpToPx(28f)
            fabPlusBtn.layoutParams = fabLp

            val menuLp = menuOverlayContainer.layoutParams as FrameLayout.LayoutParams
            menuLp.bottomMargin = keyboardHeight + dpToPx(84f)
            menuLp.rightMargin = dpToPx(20f)
            menuOverlayContainer.layoutParams = menuLp

            insets
        }
    }

    // ------------------------------------------------------------------
    // AUDIO LOADING WITH CACHE + DOWNSAMPLED COVERS
    // ------------------------------------------------------------------
    private fun loadAudioFilesAsync(paths: List<String>) {
        Thread {
            for (path in paths) {
                val file = File(path)
                if (!file.exists()) continue

                val meta = NoteRepository.getOrExtractAudioMetadata(path)
                val coverBitmap = meta.coverPath?.let { decodeSampledBitmapFromFile(it, 192, 192) }

                runOnUiThread {
                    if (audioHolders.size < 5) {
                        val holder = AudioHolder(filePath = path, uri = Uri.fromFile(file))
                        buildAudioPlayerUi(holder, meta.title, meta.artist, coverBitmap)
                    }
                }
            }
        }.start()
    }

    private fun addAudioPlayerToEditor(uri: Uri, filePath: String) {
        if (audioHolders.size >= 5) return

        Thread {
            val meta = NoteRepository.getOrExtractAudioMetadata(filePath)
            val coverBitmap = meta.coverPath?.let { decodeSampledBitmapFromFile(it, 192, 192) }
            runOnUiThread {
                if (audioHolders.size < 5) {
                    val holder = AudioHolder(filePath = filePath, uri = uri)
                    buildAudioPlayerUi(holder, meta.title, meta.artist, coverBitmap)
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // REFINED AUDIO PLAYER UI
    // ------------------------------------------------------------------
    private fun buildAudioPlayerUi(holder: AudioHolder, finalTitle: String, finalSinger: String, coverBitmap: Bitmap?) {
        var monetBgColor = primaryContainerColor
        var playerTextColor = onPrimaryContainerColor
        var playerSubtextColor = ColorUtils.setAlphaComponent(onPrimaryContainerColor, 180)
        var iconTint = onPrimaryContainerColor

        if (coverBitmap != null) {
            try {
                val scaled = Bitmap.createScaledBitmap(coverBitmap, 1, 1, false)
                val pixel = scaled.getPixel(0, 0)
                scaled.recycle()
                monetBgColor = ColorUtils.setAlphaComponent(pixel, 255)
                monetBgColor = ColorUtils.blendARGB(monetBgColor, primaryContainerColor, 0.45f)

                val isBgLight = ColorUtils.calculateLuminance(monetBgColor) > 0.5
                playerTextColor = if (isBgLight) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
                playerSubtextColor = ColorUtils.setAlphaComponent(playerTextColor, 180)
                iconTint = playerTextColor
            } catch (_: Exception) {}
        }

        val monetEndColor = ColorUtils.blendARGB(monetBgColor, surfaceContainerLowColor, 0.5f)
        val monetGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(monetBgColor, monetEndColor)
        ).apply {
            cornerRadius = dpToPx(12f).toFloat()
        }

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = monetGradient
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10f)
            }
        }

        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val coverImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (coverBitmap != null) {
                setImageBitmap(coverBitmap)
            } else {
                setImageDrawable(ContextCompat.getDrawable(this@NoteEditorActivity, R.drawable.audio_file_24px))
                imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f).toFloat()
                setColor(ColorUtils.setAlphaComponent(iconTint, 30))
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                marginEnd = dpToPx(12f)
            }
        }
        topSection.addView(coverImageView)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(8f)
            }
        }

        val titleView = TextView(this).apply {
            text = finalTitle
            textSize = 15f
            setTextColor(playerTextColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setGoogleSansFlexDefault(this, true)
        }

        val singerView = TextView(this).apply {
            text = finalSinger
            textSize = 13f
            setTextColor(playerSubtextColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setGoogleSansFlexDefault(this, false)
        }

        textContainer.addView(titleView)
        textContainer.addView(singerView)
        topSection.addView(textContainer)

        val playPauseContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f))
            isClickable = true
            isFocusable = true
        }

        val pausedRadius = dpToPx(20f).toFloat()   // Round circle when paused
        val playingRadius = dpToPx(12f).toFloat()  // Rounded square when playing

        var shapeAnimator: ValueAnimator? = null
        var currentRadius = if (holder.isPlaying) playingRadius else pausedRadius

        val buttonBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = currentRadius
            setColor(ColorUtils.setAlphaComponent(iconTint, 45))
        }

        val playPauseBtnIcon = ImageView(this).apply {
            setImageResource(if (holder.isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px)
            imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = buttonBg
            layoutParams = FrameLayout.LayoutParams(dpToPx(40f), dpToPx(40f), Gravity.CENTER)
        }
        playPauseContainer.addView(playPauseBtnIcon)

        fun morphShape(toPlaying: Boolean) {
            val targetRadius = if (toPlaying) playingRadius else pausedRadius

            shapeAnimator?.cancel()
            // Ensure we start from the current visual radius (buttonBg.cornerRadius)
            val startRadius = buttonBg.cornerRadius
            shapeAnimator = ValueAnimator.ofFloat(startRadius, targetRadius).apply {
                duration = 280
                interpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    buttonBg.cornerRadius = value
                    currentRadius = value
                }
                // After animation ends or is cancelled, ensure final value is set
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        buttonBg.cornerRadius = targetRadius
                        currentRadius = targetRadius
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        buttonBg.cornerRadius = targetRadius
                        currentRadius = targetRadius
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                start()
            }
        }

        // Save UI references to AudioHolder
        holder.playPauseBtnIcon = playPauseBtnIcon
        holder.updateShape = { toPlaying -> morphShape(toPlaying) }

        // Touch feedback without shadow/glow
        playPauseContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(0.90f).scaleY(0.90f)
                        .setDuration(120).setInterpolator(DecelerateInterpolator(1.5f)).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1.0f).scaleY(1.0f)
                        .setDuration(300).setInterpolator(PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
                }
            }
            false
        }

        playPauseContainer.setOnClickListener {
            toggleAudioPlayback(holder)
        }

        topSection.addView(playPauseContainer)

        val deleteBtn = ImageView(this).apply {
            setImageResource(R.drawable.delete_24px)
            background = null
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
            imageTintList = ColorStateList.valueOf(iconTint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dpToPx(40f), dpToPx(40f)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }

            setOnClickListener {
                removeAudioPlayer(holder)
            }
        }
        topSection.addView(deleteBtn)

        playerCard.addView(topSection)

        holder.playerView = playerCard
        audioHolders.add(holder)

        contentWrapper.addView(playerCard, audioHolders.size - 1)
        updateAddAudioButtonState()
    }

    // ------------------------------------------------------------------
    // CAPTURE BITMAP GENERATOR
    // ------------------------------------------------------------------.

    private fun createAudioPlayerForCapture(holder: AudioHolder, scale: Float): View {
        val meta = NoteRepository.getOrExtractAudioMetadata(holder.filePath)
        val coverBitmap = meta.coverPath?.let { decodeSampledBitmapFromFile(it, 192, 192) }

        var monetBgColor = primaryContainerColor
        var playerTextColor = onPrimaryContainerColor
        var playerSubtextColor = ColorUtils.setAlphaComponent(onPrimaryContainerColor, 180)
        var iconTint = onPrimaryContainerColor

        if (coverBitmap != null) {
            try {
                val scaled = Bitmap.createScaledBitmap(coverBitmap, 1, 1, false)
                val pixel = scaled.getPixel(0, 0)
                scaled.recycle()
                monetBgColor = ColorUtils.setAlphaComponent(pixel, 255)
                monetBgColor = ColorUtils.blendARGB(monetBgColor, primaryContainerColor, 0.45f)

                val isBgLight = ColorUtils.calculateLuminance(monetBgColor) > 0.5
                playerTextColor = if (isBgLight) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
                playerSubtextColor = ColorUtils.setAlphaComponent(playerTextColor, 180)
                iconTint = playerTextColor
            } catch (_: Exception) {}
        }

        val monetEndColor = ColorUtils.blendARGB(monetBgColor, surfaceContainerLowColor, 0.5f)
        val monetGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(monetBgColor, monetEndColor)
        ).apply {
            cornerRadius = (dpToPx(12f) * scale).toFloat()
        }

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = monetGradient
            setPadding((dpToPx(12f) * scale).toInt(), (dpToPx(12f) * scale).toInt(), (dpToPx(12f) * scale).toInt(), (dpToPx(12f) * scale).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (dpToPx(8f) * scale).toInt()
            }
        }

        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val coverImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (coverBitmap != null) {
                val roundedDrawable = RoundedBitmapDrawableFactory.create(resources, coverBitmap).apply {
                    cornerRadius = (dpToPx(10f) * scale).toFloat()
                }
                setImageDrawable(roundedDrawable)
            } else {
                setImageDrawable(ContextCompat.getDrawable(this@NoteEditorActivity, R.drawable.audio_file_24px))
                imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (dpToPx(10f) * scale).toFloat()
                setColor(ColorUtils.setAlphaComponent(iconTint, 30))
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams((dpToPx(48f) * scale).toInt(), (dpToPx(48f) * scale).toInt()).apply {
                marginEnd = (dpToPx(12f) * scale).toInt()
            }
        }
        topSection.addView(coverImageView)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (dpToPx(8f) * scale).toInt()
            }
        }

        val titleView = TextView(this).apply {
            text = meta.title
            textSize = (15f * scale)
            setTextColor(playerTextColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setGoogleSansFlexDefault(this, true)
        }

        val singerView = TextView(this).apply {
            text = meta.artist
            textSize = (13f * scale)
            setTextColor(playerSubtextColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setGoogleSansFlexDefault(this, false)
        }

        textContainer.addView(titleView)
        textContainer.addView(singerView)
        topSection.addView(textContainer)

        playerCard.addView(topSection)

        return playerCard
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) result = cursor.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "Audio File"
    }

    private fun removeAudioPlayer(holder: AudioHolder) {
        // Release native resources before removing
        holder.mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {}
        }
        holder.mediaPlayer = null
        holder.playerView?.let { view ->
            contentWrapper.removeView(view)
        }
        audioHolders.remove(holder)
        updateAddAudioButtonState()
    }

    // ------------------------------------------------------------------
    // FIXED: Fully release all other players, then toggle current
    // ------------------------------------------------------------------
    private fun toggleAudioPlayback(holder: AudioHolder) {
        // 1. Fully release native resources of all OTHER media players
        for (other in audioHolders) {
            if (other != holder) {
                if (other.isPlaying) {
                    other.isPlaying = false
                }
                other.mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) mp.stop()
                        mp.release()
                    } catch (_: Exception) {}
                }
                other.mediaPlayer = null
                other.playPauseBtnIcon?.setImageResource(R.drawable.play_arrow_24px)
                other.updateShape?.invoke(false)
            }
        }

        // 2. Toggle current track playback safely
        if (holder.isPlaying) {
            try {
                holder.mediaPlayer?.pause()
            } catch (_: Exception) {}
            holder.isPlaying = false
            holder.playPauseBtnIcon?.setImageResource(R.drawable.play_arrow_24px)
            holder.updateShape?.invoke(false)
        } else {
            try {
                if (holder.mediaPlayer == null) {
                    holder.mediaPlayer = MediaPlayer().apply {
                        setDataSource(this@NoteEditorActivity, holder.uri)
                        prepare()
                        setOnCompletionListener { mp ->
                            holder.isPlaying = false
                            holder.playPauseBtnIcon?.setImageResource(R.drawable.play_arrow_24px)
                            holder.updateShape?.invoke(false)
                            try {
                                mp.seekTo(0)
                            } catch (_: Exception) {}
                        }
                    }
                }
                holder.mediaPlayer?.start()
                holder.isPlaying = true
                holder.playPauseBtnIcon?.setImageResource(R.drawable.pause_24px)
                holder.updateShape?.invoke(true)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show()
                // Clean up native resources on error to prevent freezes
                try {
                    holder.mediaPlayer?.release()
                } catch (_: Exception) {}
                holder.mediaPlayer = null
                holder.isPlaying = false
                holder.playPauseBtnIcon?.setImageResource(R.drawable.play_arrow_24px)
                holder.updateShape?.invoke(false)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for (holder in audioHolders) {
            holder.mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                } catch (_: Exception) {}
            }
            holder.mediaPlayer = null
        }
        audioHolders.clear()
    }

    private fun showCreateTagDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val dialogBgColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            if (isDark) Color.parseColor("#2B2B2E") else Color.parseColor("#F2F2F7")
        )

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(28f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(dpToPx(28f).toFloat())
                setColor(dialogBgColor)
            }
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.create_tags)
            textSize = 20f
            setTextColor(MaterialColors.getColor(this@NoteEditorActivity, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
            gravity = Gravity.CENTER
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(20f) }
        }
        dialogView.addView(titleView)

        val tagEditText = EditText(this).apply {
            hint = getString(R.string.tag_entering)
            setHintTextColor(onSurfaceVariantColor)
            setTextColor(onPrimaryContainerColor)
            textSize = 16f
            filters = arrayOf(InputFilter.LengthFilter(20))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            background = GradientDrawable().apply {
                setCornerRadius(dpToPx(12f).toFloat())
                setColor(surfaceContainerHighestColor)
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(20f) }
            setGoogleSansFlexDefault(this, false)
        }

        if (!noteTag.isNullOrEmpty()) {
            tagEditText.setText(noteTag)
        }
        dialogView.addView(tagEditText)

        fun createOptionButton(textRes: Int, onClick: () -> Unit): MaterialButton {
            return MaterialButton(
                ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_UnelevatedButton)
            ).apply {
                text = getString(textRes)
                setTextColor(onPrimaryContainerColor)
                setTypeface(googleSansFlex, Typeface.BOLD)
                backgroundTintList = android.content.res.ColorStateList.valueOf(primaryContainerColor)
                gravity = Gravity.CENTER
                insetTop = 0
                insetBottom = 0
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(dpToPx(100f).toFloat())
                    .build()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(52f)
                ).apply { bottomMargin = dpToPx(10f) }
                setOnClickListener { onClick() }
            }
        }

        val btnContinue = createOptionButton(R.string.option_delete_y) {
            val enteredText = tagEditText.text.toString().trim()
            val words = enteredText.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.tag_error), Toast.LENGTH_SHORT).show()
            } else {
                noteTag = enteredText
                tagTextView.text = noteTag
                tagPillContainer.visibility = View.VISIBLE
                dialog.dismiss()
            }
        }

        val btnCancel = createOptionButton(R.string.option_cancel) { dialog.dismiss() }
        (btnCancel.layoutParams as LinearLayout.LayoutParams).bottomMargin = 0

        dialogView.addView(btnContinue)
        dialogView.addView(btnCancel)

        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        dialog.show()
    }

    private fun openFabMenu() {
        isFabMenuExpanded = true
        fabPlusBtn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        fabPlusBtn.animate().rotation(45f).setDuration(250)
            .setInterpolator(PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
        dimOverlay.visibility = View.VISIBLE
        dimOverlay.animate().alpha(1f).setDuration(200).start()

        menuOverlayContainer.visibility = View.VISIBLE
        menuOverlayContainer.alpha = 0f
        menuOverlayContainer.scaleX = 0.8f
        menuOverlayContainer.scaleY = 0.8f
        menuOverlayContainer.translationY = dpToPx(16f).toFloat()

        menuOverlayContainer.doOnPreDraw { view ->
            view.pivotX = view.width.toFloat()
            view.pivotY = view.height.toFloat()
        }

        menuOverlayContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(250)
            .setInterpolator(PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
    }

    private fun closeFabMenu() {
        isFabMenuExpanded = false
        fabPlusBtn.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        fabPlusBtn.animate().rotation(0f).setDuration(250)
            .setInterpolator(PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start()
        dimOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            dimOverlay.visibility = View.GONE
        }.start()

        menuOverlayContainer.doOnPreDraw { view ->
            view.pivotX = view.width.toFloat()
            view.pivotY = view.height.toFloat()
        }

        menuOverlayContainer.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).translationY(dpToPx(16f).toFloat())
            .setDuration(200).withEndAction {
                menuOverlayContainer.visibility = View.GONE
            }.start()
    }

    private fun getCurrentSpans(): List<SpanData> {
        val spans = mutableListOf<SpanData>()
        val spannable = contentEdit.text as? Spannable ?: return spans

        val boldSpans = spannable.getSpans(0, spannable.length, GoogleSansFlexBoldRoundSpan::class.java)
        for (span in boldSpans) {
            val spanStart = spannable.getSpanStart(span)
            val spanEnd = spannable.getSpanEnd(span)
            if (spanStart >= 0 && spanEnd >= 0) {
                spans.add(SpanData(start = spanStart, end = spanEnd, type = "bold"))
            }
        }

        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        for (span in styleSpans) {
            if (span.style == Typeface.BOLD) {
                val spanStart = spannable.getSpanStart(span)
                val spanEnd = spannable.getSpanEnd(span)
                if (spanStart >= 0 && spanEnd >= 0) {
                    spans.add(SpanData(start = spanStart, end = spanEnd, type = "bold"))
                }
            }
        }

        val sizeSpans = spannable.getSpans(0, spannable.length, RelativeSizeSpan::class.java)
        for (span in sizeSpans) {
            val spanStart = spannable.getSpanStart(span)
            val spanEnd = spannable.getSpanEnd(span)
            if (spanStart >= 0 && spanEnd >= 0) {
                spans.add(SpanData(start = spanStart, end = spanEnd, type = "bigger", size = 1.85f))
            }
        }

        spans.sortWith(compareBy({ it.start }, { it.end }, { it.type }))
        return spans
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentAudioPaths = audioHolders.map { it.filePath }
        return titleEdit.text.toString() != initialTitle ||
                contentEdit.text.toString() != initialContent ||
                noteTag != initialTag ||
                getCurrentSpans() != initialSpans ||
                currentAudioPaths != initialAudioPaths
    }

    private fun handleBackNavigation() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val dialogBgColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            if (isDark) Color.parseColor("#2B2B2E") else Color.parseColor("#F2F2F7")
        )

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(28f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(dpToPx(28f).toFloat())
                setColor(dialogBgColor)
            }
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.warning_save_title)
            textSize = 20f
            setTextColor(MaterialColors.getColor(this@NoteEditorActivity, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
            gravity = Gravity.CENTER
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(24f) }
        }
        dialogView.addView(titleView)

        fun createOptionButton(textRes: Int, onClick: () -> Unit): MaterialButton {
            return MaterialButton(
                ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_UnelevatedButton)
            ).apply {
                text = getString(textRes)
                setTextColor(onPrimaryContainerColor)
                setTypeface(googleSansFlex, Typeface.BOLD)
                backgroundTintList = android.content.res.ColorStateList.valueOf(primaryContainerColor)
                gravity = Gravity.CENTER
                insetTop = 0
                insetBottom = 0
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(dpToPx(100f).toFloat())
                    .build()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(52f)
                ).apply { bottomMargin = dpToPx(10f) }
                setOnClickListener { onClick(); dialog.dismiss() }
            }
        }

        val btnSave = createOptionButton(R.string.save_and_exit) { saveNote() }
        val btnExit = createOptionButton(R.string.exit_directly) { finish() }
        val btnBack = createOptionButton(R.string.back_to_editor) {}
        (btnBack.layoutParams as LinearLayout.LayoutParams).bottomMargin = 0

        dialogView.addView(btnSave)
        dialogView.addView(btnExit)
        dialogView.addView(btnBack)

        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        dialog.show()
    }

    private fun animateTrailingButtonShape(expand: Boolean) {
        val startCorner = if (expand) innerCornerPx else outerCornerPx
        val endCorner = if (expand) outerCornerPx else innerCornerPx

        ValueAnimator.ofFloat(startCorner, endCorner).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedCorner = animator.animatedValue as Float
                trailingBg.shapeAppearanceModel = trailingBg.shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(animatedCorner)
                    .setBottomLeftCornerSize(animatedCorner)
                    .build()
            }
            start()
        }
    }

    private fun showMenu(anchor: View) {
        menuPopup?.dismiss()

        trailingChevronIcon.animate().rotation(180f).setDuration(200).start()
        animateTrailingButtonShape(expand = true)

        val menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(dpToPx(100f).toFloat())
                setColor(primaryContainerColor)
            }
            elevation = dpToPx(8f).toFloat()
        }

        val iconDrawable = ContextCompat.getDrawable(this, R.drawable.photo_24px)
        val tintedIcon = iconDrawable?.let {
            DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(it, onPrimaryContainerColor)
            it
        }

        val menuItem = TextView(this).apply {
            text = getString(R.string.capture_notes_to_image)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setCompoundDrawablesWithIntrinsicBounds(tintedIcon, null, null, null)
            compoundDrawablePadding = dpToPx(12f)
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
            background = GradientDrawable().apply {
                setCornerRadius(dpToPx(100f).toFloat())
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                captureNoteToImage()
                menuPopup?.dismiss()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        menuView.addView(menuItem)

        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        menuPopup = android.widget.PopupWindow(
            menuView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dpToPx(8f).toFloat()
            isOutsideTouchable = true
            isFocusable = true
            animationStyle = android.R.style.Animation_Dialog
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                trailingChevronIcon.animate().rotation(0f).setDuration(200).start()
                animateTrailingButtonShape(expand = false)
                isMenuOpen = false
            }
            val xOffset = anchor.width - menuView.measuredWidth
            showAsDropDown(anchor, xOffset, dpToPx(8f))
            isMenuOpen = true
        }
    }

    // ✅ FIXED: preserves the pin state when saving
    private fun saveNoteData(): Boolean {
        val title = titleEdit.text.toString().trim()
        val plainText = contentEdit.text.toString()
        val spans = getCurrentSpans()
        val audioPaths = audioHolders.map { it.filePath }

        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.title_required), Toast.LENGTH_SHORT).show()
            return false
        }

        val currentAudioPaths = audioPaths.toSet()
        for (oldPath in initialAudioPaths) {
            if (oldPath !in currentAudioPaths) {
                try {
                    val file = File(oldPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }

        if (isNewNote) {
            NoteRepository.saveNote(title, plainText, spans, noteTag, audioPaths, isPinned = false)
        } else {
            NoteRepository.deleteNote(noteId, deleteAudioFiles = false)
            NoteRepository.saveNote(title, plainText, spans, noteTag, audioPaths, isPinned = initialIsPinned)
        }
        return true
    }

    private fun captureNoteToImage() {
        if (!saveNoteData()) return

        val title = titleEdit.text.toString().trim()
        val contentSpannable = contentEdit.text

        if (title.isEmpty() && contentSpannable.isEmpty() && audioHolders.isEmpty()) {
            Toast.makeText(this, getString(R.string.nothing_to_capture), Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = generateHighResNoteBitmap(title, contentSpannable)
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.failed_to_generate_image), Toast.LENGTH_SHORT).show()
            return
        }

        lastGeneratedBitmap = bitmap

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
            val safeTitle = if (title.isNotEmpty()) title else "note"
            putExtra(Intent.EXTRA_TITLE, "${safeTitle}_note.png")
        }
        startActivityForResult(intent, REQUEST_SAVE_IMAGE)
    }

    private fun generateHighResNoteBitmap(title: String, contentSpannable: Spannable): Bitmap? {
        val baseWidth = maxOf(resources.displayMetrics.widthPixels, 720)
        val scale = 1.85f
        val maxWidth = (baseWidth * scale).toInt()

        val paddingPx = (dpToPx(20f) * scale).toInt()
        val labelBottomMargin = (dpToPx(4f) * scale).toInt()
        val titleBottomMargin = (dpToPx(16f) * scale).toInt()
        val cornerRadiusPx = (dpToPx(12f) * scale).toFloat()
        val textPaddingHorizontal = (dpToPx(16f) * scale).toInt()
        val textPaddingVertical = (dpToPx(14f) * scale).toInt()

        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.parseColor("#FEF7FF"))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        val titleLabel = TextView(this).apply {
            text = getString(R.string.title)
            textSize = (14f * scale)
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = labelBottomMargin }
        }
        root.addView(titleLabel)

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setCornerRadius(cornerRadiusPx)
                setColor(surfaceContainerHighestColor)
            }
            setPadding(textPaddingHorizontal, (dpToPx(8f) * scale).toInt(), textPaddingHorizontal, (dpToPx(8f) * scale).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = titleBottomMargin }
        }

        if (!noteTag.isNullOrEmpty()) {
            val tagPill = TextView(this).apply {
                text = noteTag
                textSize = (13f * scale)
                setTextColor(onPrimaryContainerColor)
                setGoogleSansFlexDefault(this, true)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = (100f * scale)
                    setColor(primaryContainerColor)
                }
                setPadding(
                    (dpToPx(10f) * scale).toInt(),
                    (dpToPx(4f) * scale).toInt(),
                    (dpToPx(10f) * scale).toInt(),
                    (dpToPx(4f) * scale).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (dpToPx(8f) * scale).toInt()
                }
            }
            titleContainer.addView(tagPill)
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = (18f * scale)
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        titleContainer.addView(titleText)
        root.addView(titleContainer)

        val contentLabel = TextView(this).apply {
            text = getString(R.string.content)
            textSize = (14f * scale)
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = labelBottomMargin }
        }
        root.addView(contentLabel)

        for (holder in audioHolders) {
            val audioView = createAudioPlayerForCapture(holder, scale)
            root.addView(audioView)
        }

        val contentText = TextView(this).apply {
            setText(contentSpannable)
            textSize = (16f * scale)
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, false)
            background = GradientDrawable().apply {
                setCornerRadius(cornerRadiusPx)
                setColor(surfaceContainerHighestColor)
            }
            setPadding(textPaddingHorizontal, textPaddingVertical, textPaddingHorizontal, textPaddingVertical)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(contentText)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        return Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply { root.draw(this) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SAVE_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        lastGeneratedBitmap?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        Toast.makeText(this, getString(R.string.image_saved_successfully), Toast.LENGTH_SHORT).show()
                        lastGeneratedBitmap = null
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.failed_to_save_image, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            lastGeneratedBitmap?.recycle()
            lastGeneratedBitmap = null
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun saveNote() {
        if (saveNoteData()) {
            hideKeyboard()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun disableToolbar() {
        updateButtonState(copyBtn, false)
        updateButtonState(pasteBtn, false)
        updateButtonState(boldBtn, false)
        updateButtonState(biggerBtn, false)
    }

    private fun updateToolbarButtons() {
        val hasFocus = contentEdit.hasFocus()
        val hasSelection = contentEdit.selectionStart != contentEdit.selectionEnd
        val enabled = hasFocus && hasSelection
        updateButtonState(copyBtn, enabled)
        updateButtonState(boldBtn, enabled)
        updateButtonState(biggerBtn, enabled)
        updateButtonState(pasteBtn, hasFocus)
    }

    private fun getSelectedText(): String {
        val rawStart = contentEdit.selectionStart
        val rawEnd = contentEdit.selectionEnd
        if (rawStart == rawEnd) return ""
        val selStart = minOf(rawStart, rawEnd)
        val selEnd = maxOf(rawStart, rawEnd)
        if (selStart >= 0 && selEnd <= contentEdit.text.length) {
            return contentEdit.text.substring(selStart, selEnd)
        }
        return ""
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note_text", text))
    }

    private fun applyBoldRoundToSelection() {
        val rawStart = contentEdit.selectionStart
        val rawEnd = contentEdit.selectionEnd
        if (rawStart == rawEnd) return
        val selStart = minOf(rawStart, rawEnd)
        val selEnd = maxOf(rawStart, rawEnd)

        val spannable = contentEdit.text as Spannable
        val spanClass = if (googleSansFlex != null) GoogleSansFlexBoldRoundSpan::class.java else StyleSpan::class.java
        val spans = spannable.getSpans(selStart, selEnd, spanClass)
        if (spans.isNotEmpty()) {
            for (span in spans) {
                spannable.removeSpan(span)
            }
        } else {
            val font = googleSansFlex
            if (font != null) {
                spannable.setSpan(
                    GoogleSansFlexBoldRoundSpan(font),
                    selStart, selEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(StyleSpan(Typeface.BOLD), selStart, selEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        contentEdit.setText(spannable)
        Selection.setSelection(contentEdit.text, selStart, selEnd)
        updateToolbarButtons()
    }

    private fun applyBiggerRoundToSelection() {
        val rawStart = contentEdit.selectionStart
        val rawEnd = contentEdit.selectionEnd
        if (rawStart == rawEnd) return
        val selStart = minOf(rawStart, rawEnd)
        val selEnd = maxOf(rawStart, rawEnd)

        val editableText = contentEdit.text

        val sizeSpans = editableText.getSpans(selStart, selEnd, RelativeSizeSpan::class.java)
        if (sizeSpans.isNotEmpty()) {
            for (span in sizeSpans) {
                editableText.removeSpan(span)
            }
            val boldSpans = editableText.getSpans(selStart, selEnd, GoogleSansFlexBoldRoundSpan::class.java)
            for (span in boldSpans) {
                editableText.removeSpan(span)
            }
        } else {
            val font = googleSansFlex
            if (font != null) {
                editableText.setSpan(
                    GoogleSansFlexBoldRoundSpan(font),
                    selStart, selEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                editableText.setSpan(StyleSpan(Typeface.BOLD), selStart, selEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            editableText.setSpan(RelativeSizeSpan(1.85f), selStart, selEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        contentEdit.setText(editableText)
        Selection.setSelection(contentEdit.text, selStart, selEnd)
        updateToolbarButtons()
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

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    // ------------------------------------------------------------------
    // BITMAP DOWNSAMPLING HELPERS (prevents memory pressure)
    // ------------------------------------------------------------------
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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
            val size = dpToPx(11f).toFloat()
            canvas.drawLine(cx - size, cy, cx + size, cy, paint)
            canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}