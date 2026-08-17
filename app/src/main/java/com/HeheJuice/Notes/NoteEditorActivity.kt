package com.HeheJuice.Notes

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.util.TypedValue
import android.graphics.Typeface
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import com.google.android.material.color.DynamicColors

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var titleEdit: EditText
    private lateinit var contentEdit: EditText
    private var noteId: String = ""
    private var isNewNote = true
    private var googleSansFlex: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        DynamicColors.applyToActivityIfAvailable(this)
        NoteRepository.init(applicationContext)

        googleSansFlex = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (e: Exception) { null }

        noteId = intent.getStringExtra("note_id") ?: ""
        isNewNote = noteId.isEmpty()

        // Resolve colours with fallbacks
        val typedValue = TypedValue()
        fun resolveColor(attrRes: Int, fallback: Int): Int {
            return if (theme.resolveAttribute(attrRes, typedValue, true)) {
                typedValue.data
            } else {
                fallback
            }
        }

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val surfaceContainer = resolveColor(
            com.google.android.material.R.attr.colorSurfaceContainer,
            if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
        )
        val surfaceLow = resolveColor(
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            if (isDark) Color.parseColor("#2B2B2E") else Color.parseColor("#F2F2F7")
        )
        val primaryContainer = resolveColor(
            com.google.android.material.R.attr.colorPrimaryContainer,
            if (isDark) Color.parseColor("#4F378B") else Color.parseColor("#E8DEF8")
        )
        val onPrimaryContainer = resolveColor(
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B")
        )
        val onSurfaceVariant = resolveColor(
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
        )

        // Build UI
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceContainer)
            setPadding(dpToPx(20f), dpToPx(48f), dpToPx(20f), dpToPx(20f))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(24f) }
        }

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

        val topTitle = TextView(this).apply {
            text = if (isNewNote) getString(R.string.new_note) else getString(R.string.edit_note)
            textSize = 22f
            setTextColor(onPrimaryContainer)
            applyGoogleSansBoldRound(this)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val saveBtn = TextView(this).apply {
            text = getString(R.string.save)
            textSize = 16f
            setTextColor(onPrimaryContainer)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(primaryContainer)
            }
            setPadding(dpToPx(20f), dpToPx(10f), dpToPx(20f), dpToPx(10f))
            isClickable = true
            isFocusable = true
            setOnClickListener { saveNote() }
        }

        topBar.addView(backBtn)
        topBar.addView(topTitle)
        topBar.addView(saveBtn)
        root.addView(topBar)

        val titleLabel = TextView(this).apply {
            text = getString(R.string.title)
            textSize = 14f
            setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4f) }
        }
        root.addView(titleLabel)

        titleEdit = EditText(this).apply {
            hint = getString(R.string.enter_title_hint)
            setHintTextColor(onSurfaceVariant)
            setTextColor(onPrimaryContainer)
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
        }
        root.addView(titleEdit)

        val contentLabel = TextView(this).apply {
            text = getString(R.string.content)
            textSize = 14f
            setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4f) }
        }
        root.addView(contentLabel)

        contentEdit = EditText(this).apply {
            hint = getString(R.string.write_content_hint)
            setHintTextColor(onSurfaceVariant)
            setTextColor(onPrimaryContainer)
            textSize = 16f
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f).toFloat()
                setColor(surfaceLow)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dpToPx(8f) }
        }
        root.addView(contentEdit)

        if (!isNewNote) {
            NoteRepository.getNote(noteId)?.let { note ->
                titleEdit.setText(note.title)
                contentEdit.setText(note.content)
            }
        }

        setContentView(root)
    }

    private fun applyGoogleSansBoldRound(textView: TextView) {
        googleSansFlex?.let { font ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                textView.typeface = Typeface.create(font, 700, false)
                textView.fontVariationSettings = "'wght' 700, 'ROND' 100"
            } else {
                textView.setTypeface(font, Typeface.BOLD)
            }
        }
    }

    // Updated save logic: for edits, delete old note and create a new one with updated title
    private fun saveNote() {
        val title = titleEdit.text.toString().trim()
        val content = contentEdit.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.title_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (isNewNote) {
            NoteRepository.saveNote(title, content)
        } else {
            // Delete old note and create a new one with the updated title (filename will change)
            NoteRepository.deleteNote(noteId)
            NoteRepository.saveNote(title, content)
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}