package com.HeheJuice.Notes

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ContextThemeWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.shape.ShapeAppearanceModel

// Custom TypefaceSpan for GoogleSansFlex Bold Round (wght 800, ROND 100)
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

    private lateinit var titleEdit: EditText
    private lateinit var contentEdit: EditText
    private var noteId: String = ""
    private var isNewNote = true
    private var googleSansFlex: Typeface? = null

    private lateinit var copyBtn: ImageView
    private lateinit var pasteBtn: ImageView
    private lateinit var boldBtn: ImageView
    private lateinit var biggerBtn: ImageView
    private lateinit var toolbarContainer: LinearLayout

    private lateinit var splitButton: LinearLayout
    private lateinit var trailingChevronIcon: ImageView  // rotating chevron
    private var menuPopup: android.widget.PopupWindow? = null
    private var isMenuOpen = false

    private var surfaceContainerLowColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var primaryContainerColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceLow: Int = 0

    private fun updateButtonState(btn: ImageView, enabled: Boolean) {
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1.0f else 0.5f
        val iconColor = if (enabled) onPrimaryContainerColor else Color.parseColor("#888888")
        btn.imageTintList = android.content.res.ColorStateList.valueOf(iconColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // ---- Match main activity's window insets behaviour ----
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

        val typedValue = TypedValue()
        fun resolveColor(attrRes: Int, fallback: Int): Int {
            return if (theme.resolveAttribute(attrRes, typedValue, true)) {
                typedValue.data
            } else {
                fallback
            }
        }

        surfaceContainerColor = resolveColor(
            com.google.android.material.R.attr.colorSurfaceContainer,
            if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
        )
        surfaceLow = resolveColor(
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            if (isDark) Color.parseColor("#2B2B2E") else Color.parseColor("#F2F2F7")
        )
        primaryContainerColor = resolveColor(
            com.google.android.material.R.attr.colorPrimaryContainer,
            if (isDark) Color.parseColor("#4F378B") else Color.parseColor("#E8DEF8")
        )
        onPrimaryContainerColor = resolveColor(
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B")
        )
        onSurfaceVariantColor = resolveColor(
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
        )
        surfaceContainerLowColor = surfaceLow

        // Root layout – top padding will be set dynamically by insets
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceContainerColor)
            setPadding(dpToPx(20f), 0, dpToPx(20f), dpToPx(20f))
        }

        // ---- Top bar (with clip disabled) ----
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

        // Back button (50dp)
        val backBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@NoteEditorActivity, R.drawable.arrow_back_24px))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceLow)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(dpToPx(50f), dpToPx(50f))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_content_desc)
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        // Title
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

        // ---- Custom Split Pill Container ----
        val buttonHeight = dpToPx(52f)
        val innerCorner = dpToPx(4f).toFloat()
        val outerCorner = buttonHeight / 2f

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

        // Leading button – "Save" (round left, flat right)
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
                .setTopLeftCornerSize(outerCorner)
                .setBottomLeftCornerSize(outerCorner)
                .setTopRightCornerSize(innerCorner)
                .setBottomRightCornerSize(innerCorner)
                .build()

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = dpToPx(2f) }
        }
        leadingButton.setOnClickListener { saveNote() }

        // ---- Trailing button container (fixed background + rotating chevron) ----
        val trailingButtonContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                buttonHeight,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
        }

        // Background pill (flat left, round right)
        val trailingBg = MaterialButton(
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
                .setTopLeftCornerSize(innerCorner)
                .setBottomLeftCornerSize(innerCorner)
                .setTopRightCornerSize(outerCorner)
                .setBottomRightCornerSize(outerCorner)
                .build()

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Rotating chevron icon
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
            if (isMenuOpen) {
                menuPopup?.dismiss()
            } else {
                showMenu(splitButton) // anchor to splitButton
            }
        }

        splitButton.addView(leadingButton)
        splitButton.addView(trailingButtonContainer)
        topBar.addView(splitButton)

        root.addView(topBar)

        // Title label
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
        root.addView(titleLabel)

        titleEdit = EditText(this).apply {
            hint = getString(R.string.enter_title_hint)
            setHintTextColor(onSurfaceVariantColor)
            setTextColor(onPrimaryContainerColor)
            textSize = 18f
            filters = arrayOf(InputFilter.LengthFilter(50))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_NEXT
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f).toFloat()
                setColor(surfaceLow)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16f) }
            setGoogleSansFlexDefault(this, false)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) disableToolbar() else updateToolbarButtons()
            }
        }
        root.addView(titleEdit)

        // Content label
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
        root.addView(contentLabel)

        // ---- SCROLLVIEW + CONTENT EDIT ----
        val scrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(8f)
            }
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
        }

        contentEdit = EditText(this).apply {
            hint = getString(R.string.write_content_hint)
            setHintTextColor(onSurfaceVariantColor)
            setTextColor(onPrimaryContainerColor)
            textSize = 16f
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f).toFloat()
                setColor(surfaceLow)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            gravity = Gravity.TOP or Gravity.START
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setGoogleSansFlexDefault(this, false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scrollContainer.addView(contentEdit)
        root.addView(scrollContainer)

        // ---- Toolbar ----
        toolbarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
        }

        val toolPill = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
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
                val start = contentEdit.selectionStart
                val end = contentEdit.selectionEnd
                val text = contentEdit.text
                if (start != end) text.replace(start, end, pasteText)
                else text.insert(start, pasteText)
            } else {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.nothing_to_paste), Toast.LENGTH_SHORT).show()
            }
        }
        updateButtonState(pasteBtn, true)

        boldBtn = createToolButton(R.drawable.format_bold_24px) {
            applyBoldRoundToSelection()
        }

        biggerBtn = createToolButton(R.drawable.title_24px) {
            applyBiggerRoundToSelection()
        }

        buttonRow.addView(copyBtn)
        buttonRow.addView(pasteBtn)
        buttonRow.addView(boldBtn)
        buttonRow.addView(biggerBtn)

        toolPill.addView(buttonRow)
        toolbarContainer.addView(toolPill)
        root.addView(toolbarContainer)

        // ---- Load existing note ----
        if (!isNewNote) {
            NoteRepository.getNote(noteId)?.let { note ->
                titleEdit.setText(note.title)
                val spannable = SpannableStringBuilder(note.content)

                for (spanData in note.spans) {
                    when (spanData.type) {
                        "bold" -> {
                            if (googleSansFlex != null) {
                                spannable.setSpan(
                                    GoogleSansFlexBoldRoundSpan(googleSansFlex!!),
                                    spanData.start, spanData.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            } else {
                                spannable.setSpan(StyleSpan(Typeface.BOLD), spanData.start, spanData.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                        "bigger" -> {
                            val size = spanData.size ?: 2.0f
                            spannable.setSpan(RelativeSizeSpan(size), spanData.start, spanData.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }

                contentEdit.setText(spannable)
                contentEdit.invalidate()
            }
        }

        setContentView(root)

        // Selection listeners
        contentEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateToolbarButtons()
            }
        })
        contentEdit.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                updateToolbarButtons()
            }
            false
        }

        updateToolbarButtons()

        // ---- Dynamic status bar and keyboard insets ----
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardHeight = imeInsets.bottom

            root.setPadding(
                dpToPx(20f),
                statusBarTop + dpToPx(12f),   // exact match to main activity
                dpToPx(20f),
                dpToPx(20f)
            )

            val lp = toolbarContainer.layoutParams as LinearLayout.LayoutParams
            lp.bottomMargin = keyboardHeight + dpToPx(8f)
            toolbarContainer.layoutParams = lp

            insets
        }
    }

    // ========== CUSTOM POPUP MENU (right-aligned, pre-measured) ==========
    private fun showMenu(anchor: View) {
        menuPopup?.dismiss()

        // Rotate only the inner chevron icon
        trailingChevronIcon.animate().rotation(180f).setDuration(200).start()

        val menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
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
            setCompoundDrawablesWithIntrinsicBounds(tintedIcon, null, null, null)
            compoundDrawablePadding = dpToPx(12f)
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(24f), dpToPx(14f))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.capture_notes_to_image), Toast.LENGTH_SHORT).show()
                menuPopup?.dismiss()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        menuView.addView(menuItem)

        // Pre‑measure to get the exact width for alignment
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
                isMenuOpen = false
            }
            // Calculate offset so right edge of menu aligns with right edge of split button
            val xOffset = anchor.width - menuView.measuredWidth
            showAsDropDown(anchor, xOffset, dpToPx(8f))
            isMenuOpen = true
        }
    }

    // ========== Existing methods ==========
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
        val start = contentEdit.selectionStart
        val end = contentEdit.selectionEnd
        if (start != end && start >= 0 && end <= contentEdit.text.length) {
            return contentEdit.text.substring(start, end)
        }
        return ""
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note_text", text))
    }

    private fun applyBoldRoundToSelection() {
        val start = contentEdit.selectionStart
        val end = contentEdit.selectionEnd
        if (start == end) return
        val spannable = contentEdit.text as Spannable
        val spanClass = if (googleSansFlex != null) GoogleSansFlexBoldRoundSpan::class.java else StyleSpan::class.java
        val spans = spannable.getSpans(start, end, spanClass)
        if (spans.isNotEmpty()) {
            for (span in spans) {
                spannable.removeSpan(span)
            }
        } else {
            if (googleSansFlex != null) {
                spannable.setSpan(
                    GoogleSansFlexBoldRoundSpan(googleSansFlex!!),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        contentEdit.setText(spannable)
        Selection.setSelection(contentEdit.text, start, end)
        updateToolbarButtons()
    }

    private fun applyBiggerRoundToSelection() {
        val start = contentEdit.selectionStart
        val end = contentEdit.selectionEnd
        if (start == end) return
        val spannable = contentEdit.text as Spannable

        val sizeSpans = spannable.getSpans(start, end, RelativeSizeSpan::class.java)
        if (sizeSpans.isNotEmpty()) {
            for (span in sizeSpans) {
                spannable.removeSpan(span)
            }
            val boldSpans = spannable.getSpans(start, end, GoogleSansFlexBoldRoundSpan::class.java)
            for (span in boldSpans) {
                spannable.removeSpan(span)
            }
        } else {
            if (googleSansFlex != null) {
                spannable.setSpan(
                    GoogleSansFlexBoldRoundSpan(googleSansFlex!!),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            spannable.setSpan(RelativeSizeSpan(2.0f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        contentEdit.setText(spannable)
        Selection.setSelection(contentEdit.text, start, end)
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

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        val springBackInterpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.88f)
                    .setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                    .setDuration(350).setInterpolator(springBackInterpolator).start()
            }
        }
        false
    }

    private fun saveNote() {
        val title = titleEdit.text.toString().trim()
        val plainText = contentEdit.text.toString()
        val spans = mutableListOf<SpanData>()
        val spannable = contentEdit.text as? Spannable
        if (spannable != null) {
            val boldSpans = spannable.getSpans(0, spannable.length, GoogleSansFlexBoldRoundSpan::class.java)
            for (span in boldSpans) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                if (start >= 0 && end >= 0) {
                    spans.add(SpanData(start = start, end = end, type = "bold"))
                }
            }
            val sizeSpans = spannable.getSpans(0, spannable.length, RelativeSizeSpan::class.java)
            for (span in sizeSpans) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                if (start >= 0 && end >= 0) {
                    spans.add(SpanData(start = start, end = end, type = "bigger", size = 2.0f))
                }
            }
        }

        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.title_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (isNewNote) {
            NoteRepository.saveNote(title, plainText, spans)
        } else {
            NoteRepository.deleteNote(noteId)
            NoteRepository.saveNote(title, plainText, spans)
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}