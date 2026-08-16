package com.HeheJuice.Notes

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.util.TypedValue
import android.graphics.Typeface

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var titleEdit: EditText
    private lateinit var contentEdit: EditText
    private var noteId: String = ""
    private var isNewNote = true

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Initialize repository
        NoteRepository.init(applicationContext)

        noteId = intent.getStringExtra("note_id") ?: ""
        isNewNote = noteId.isEmpty()

        // Resolve colors
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
        val surfaceContainer = typedValue.data
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerLow, typedValue, true)
        val surfaceLow = typedValue.data
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
        val primaryContainer = typedValue.data
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
        val onPrimaryContainer = typedValue.data
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        val onSurfaceVariant = typedValue.data

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceContainer)
            setPadding(dpToPx(20f), dpToPx(48f), dpToPx(20f), dpToPx(20f))
        }

        // Top bar: back, title, save
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(24f) }
        }
        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 28f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        val topTitle = TextView(this).apply {
            text = if (isNewNote) "New Note" else "Edit Note"
            textSize = 22f
            setTextColor(onPrimaryContainer)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val saveBtn = TextView(this).apply {
            text = "Save"
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

        // Title input
        val titleLabel = TextView(this).apply {
            text = "Title"
            textSize = 14f
            setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4f) }
        }
        root.addView(titleLabel)

        titleEdit = EditText(this).apply {
            hint = "Enter title..."
            setHintTextColor(onSurfaceVariant)
            setTextColor(onPrimaryContainer)
            textSize = 18f
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

        // Content input
        val contentLabel = TextView(this).apply {
            text = "Content"
            textSize = 14f
            setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4f) }
        }
        root.addView(contentLabel)

        contentEdit = EditText(this).apply {
            hint = "Write your note here..."
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

        // Load existing note if editing
        if (!isNewNote) {
            NoteRepository.getNote(noteId)?.let { note ->
                titleEdit.setText(note.title)
                contentEdit.setText(note.content)
            }
        }

        setContentView(root)
    }

    private fun saveNote() {
        val title = titleEdit.text.toString().trim()
        val content = contentEdit.text.toString().trim()
        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        val finalContent = if (content.isNotEmpty()) content else title
        val id = if (isNewNote) {
            val base = if (title.isNotEmpty()) title else "note"
            val sanitized = base.replace(Regex("[^a-zA-Z0-9\\-_]"), "_")
            val timestamp = System.currentTimeMillis()
            "$sanitized-$timestamp"
        } else {
            noteId
        }
        NoteRepository.saveNote(id, finalContent)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}