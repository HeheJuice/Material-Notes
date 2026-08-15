package com.HeheJuice.Notes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class NotesMainAct : Activity() {

    // Colors (use nullable or default values – not lateinit for primitives)
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0
    private var cardBorderColor = 0
    private var inputBgColor = 0
    private var secondaryBtnColor = 0
    private var bottomBarBg = 0

    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var fab: FloatingActionButton

    // Storage
    private lateinit var prefs: SharedPreferences
    private val notesList = mutableListOf<Note>()

    // Bottom bar views
    private lateinit var slidingPillView: View
    private lateinit var notesTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var notesContainer: LinearLayout
    private lateinit var settingsContainer: LinearLayout
    private var isNotesTabActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        // 1. Load dynamic Monet colors from theme
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        accentColor = typedValue.data

        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        primaryTextColor = typedValue.data

        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        secondaryTextColor = typedValue.data

        // Fallback card colours (adapt to dark/light)
        cardBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.WHITE
        cardBorderColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        inputBgColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7")
        secondaryBtnColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        bottomBarBg = if (isDark) Color.parseColor("#1C1C1E") else Color.WHITE

        // 2. Load notes from SharedPreferences
        prefs = getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
        loadNotes()

        // 3. Root layout
        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(if (isDark) Color.BLACK else Color.parseColor("#F2F2F7"))
        }

        val statusBarHeight = getStatusBarHeight()

        // 4. Scroll container
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(68f), dpToPx(16f), dpToPx(140f))
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 5. Notes container (RecyclerView + title)
        notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val notesTitle = TextView(this).apply {
            text = "My Notes"
            textSize = 28f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(16f))
        }
        notesContainer.addView(notesTitle)

        notesRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@NotesMainAct)
            noteAdapter = NoteAdapter(notesList) { position ->
                val note = notesList[position]
                showNoteDialog(note, position)
            }
            adapter = noteAdapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        notesContainer.addView(notesRecyclerView)

        // 6. Settings container (placeholder)
        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val settingsTitle = TextView(this).apply {
            text = "Settings"
            textSize = 28f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(16f))
        }
        settingsContainer.addView(settingsTitle)

        val dummySetting = TextView(this).apply {
            text = "Dark mode, About, etc.\n(Work in progress)"
            textSize = 16f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(40f), 0, 0)
        }
        settingsContainer.addView(dummySetting)

        // Add containers to scroll content
        scrollContent.addView(notesContainer)
        scrollContent.addView(settingsContainer)
        scrollView.addView(scrollContent)
        rootFrame.addView(scrollView)

        // 7. Top bar
        val topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(12f), dpToPx(16f), dpToPx(12f))
        }
        val topBarTitle = TextView(this).apply {
            text = "NoteApp"
            textSize = 20f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        topBarLayout.addView(topBarTitle)
        rootFrame.addView(topBarLayout)

        // 8. Floating Action Button (Add note)
        fab = FloatingActionButton(this).apply {
            setImageDrawable(createPlusIcon())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor))
            setOnClickListener {
                showNoteDialog(null, -1)
            }
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(56f),
                dpToPx(56f),
                Gravity.END or Gravity.BOTTOM
            ).apply {
                marginEnd = dpToPx(16f)
                bottomMargin = dpToPx(16f)
            }
        }
        rootFrame.addView(fab)

        // 9. Bottom bar with sliding pill (no search button)
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
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(bottomBarBg)
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        }

        val activeTabBg = GradientDrawable().apply {
            setColor(secondaryBtnColor)
            cornerRadius = dpToPx(100f).toFloat()
        }
        slidingPillView = View(this).apply {
            background = activeTabBg
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(44f))
        }

        val tabButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        notesTabBtn = TextView(this).apply {
            text = "Notes"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
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
            setTypeface(null, Typeface.BOLD)
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

        // ----- Tab switching logic (unchanged) -----
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
                notesTabBtn.setTextColor(primaryTextColor)
                settingsTabBtn.setTextColor(secondaryTextColor)
            } else {
                notesTabBtn.setTextColor(secondaryTextColor)
                settingsTabBtn.setTextColor(primaryTextColor)
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
            if (isNotesTabActive != toNotes) {
                isNotesTabActive = toNotes
                if (toNotes) {
                    notesContainer.visibility = View.VISIBLE
                    settingsContainer.visibility = View.GONE
                } else {
                    notesContainer.visibility = View.GONE
                    settingsContainer.visibility = View.VISIBLE
                }
                scrollView.scrollTo(0, 0)
            }
        }

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

        notesTabBtn.setOnClickListener {
            if (!isNotesTabActive) {
                animatePillTo(0f) { switchTab(true) }
            }
        }
        settingsTabBtn.setOnClickListener {
            if (isNotesTabActive) {
                animatePillTo(1f) { switchTab(false) }
            }
        }

        // Window insets
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
            val effectiveTop = if (topInset > 0) topInset else statusBarHeight

            topBarLayout.setPadding(dpToPx(16f), effectiveTop + dpToPx(12f), dpToPx(16f), dpToPx(12f))
            scrollView.setPadding(dpToPx(16f), effectiveTop + dpToPx(68f), dpToPx(16f), dpToPx(140f))
            bottomBarLayout.layoutParams = (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = dpToPx(16f) + bottomInset
            }
            insets
        }

        setContentView(rootFrame)

        rootFrame.post {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply {
                width = notesTabBtn.width
            }
            slidingPillView.translationX = notesTabBtn.left.toFloat()
            slidingPillView.requestLayout()
        }
    }

    // ---------- Note data class ----------
    data class Note(
        val id: Long,
        val title: String,
        val content: String,
        val timestamp: Long
    )

    // ---------- RecyclerView Adapter (fixed) ----------
    inner class NoteAdapter(
        private val items: MutableList<Note>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<NoteAdapter.ViewHolder>() {

        inner class ViewHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = MaterialCardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(12f)
                }
                setCardBackgroundColor(cardBgColor)
                strokeColor = cardBorderColor
                strokeWidth = dpToPx(1f)
                radius = dpToPx(16f).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onItemClick(pos)
                }
                // Inner layout
                val innerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
                }
                val titleTv = TextView(context).apply {
                    textSize = 18f
                    setTextColor(primaryTextColor)
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val contentTv = TextView(context).apply {
                    textSize = 14f
                    setTextColor(secondaryTextColor)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val dateTv = TextView(context).apply {
                    textSize = 12f
                    setTextColor(secondaryTextColor)
                }
                innerLayout.addView(titleTv)
                innerLayout.addView(contentTv)
                innerLayout.addView(dateTv)
                addView(innerLayout)
            }
            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = items[position]
            val inner = holder.card.getChildAt(0) as LinearLayout
            (inner.getChildAt(0) as TextView).text = note.title.ifEmpty { "Untitled" }
            (inner.getChildAt(1) as TextView).text = note.content
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            (inner.getChildAt(2) as TextView).text = sdf.format(Date(note.timestamp))
        }

        override fun getItemCount() = items.size
    }

    // ---------- Dialog for adding/editing note (fixed window calls) ----------
    private fun showNoteDialog(note: Note? = null, position: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(20f))
        }

        val title = TextView(this).apply {
            text = if (note == null) "New Note" else "Edit Note"
            textSize = 20f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val titleInput = EditText(this).apply {
            hint = "Title"
            setHintTextColor(secondaryTextColor)
            setTextColor(primaryTextColor)
            textSize = 16f
            setText(note?.title ?: "")
            background = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dpToPx(16f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(16f) }
        }

        val contentInput = EditText(this).apply {
            hint = "Content"
            setHintTextColor(secondaryTextColor)
            setTextColor(primaryTextColor)
            textSize = 16f
            setText(note?.content ?: "")
            background = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dpToPx(16f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12f) }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54f)
            ).apply { topMargin = dpToPx(16f) }
        }

        val cancelBtn = createAnimatedButton(
            "Cancel",
            primaryTextColor,
            secondaryBtnColor,
            LinearLayout.LayoutParams.MATCH_PARENT
        ) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dpToPx(6f)
            }
        }

        val saveBtn = createAnimatedButton(
            if (note == null) "Add" else "Update",
            Color.WHITE,
            accentColor,
            LinearLayout.LayoutParams.MATCH_PARENT
        ) {
            val titleText = titleInput.text.toString().trim()
            val contentText = contentInput.text.toString().trim()
            if (titleText.isEmpty() && contentText.isEmpty()) {
                Toast.makeText(this@NotesMainAct, "Note cannot be empty", Toast.LENGTH_SHORT).show()
                return@createAnimatedButton
            }

            if (note == null) {
                val newNote = Note(
                    id = System.currentTimeMillis(),
                    title = titleText,
                    content = contentText,
                    timestamp = System.currentTimeMillis()
                )
                notesList.add(0, newNote)
            } else {
                val updated = note.copy(
                    title = titleText,
                    content = contentText,
                    timestamp = System.currentTimeMillis()
                )
                notesList[position] = updated
            }
            saveNotes()
            noteAdapter.notifyDataSetChanged()
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dpToPx(6f)
            }
        }

        if (note != null) {
            val deleteBtn = createAnimatedButton(
                "Delete",
                Color.WHITE,
                Color.parseColor("#FF3B30"),
                LinearLayout.LayoutParams.MATCH_PARENT
            ) {
                notesList.removeAt(position)
                saveNotes()
                noteAdapter.notifyDataSetChanged()
                dialog.dismiss()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dpToPx(6f)
                }
            }
            btnRow.addView(cancelBtn)
            btnRow.addView(deleteBtn)
            btnRow.addView(saveBtn)
        } else {
            btnRow.addView(cancelBtn)
            btnRow.addView(saveBtn)
        }

        cardLayout.addView(title)
        cardLayout.addView(titleInput)
        cardLayout.addView(contentInput)
        cardLayout.addView(btnRow)

        dialog.setContentView(cardLayout)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    // ---------- Storage helpers ----------
    private fun loadNotes() {
        val json = prefs.getString("notes", "[]") ?: "[]"
        val array = JSONArray(json)
        notesList.clear()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            notesList.add(
                Note(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
    }

    private fun saveNotes() {
        val array = JSONArray()
        for (note in notesList) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("timestamp", note.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("notes", array.toString()).apply()
    }

    // ---------- Utility: create animated button ----------
    private fun createAnimatedButton(
        text: String,
        textColor: Int,
        bgColor: Int,
        width: Int = LinearLayout.LayoutParams.MATCH_PARENT,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(width, dpToPx(54f))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener(pressScaleTouchListener)
        }
    }

    // ---------- Touch feedback for scaling buttons ----------
    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        val springBack = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .alpha(0.85f)
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
                    .setInterpolator(springBack)
                    .start()
            }
        }
        false
    }

    // ---------- Plus icon (search icon removed) ----------
    private fun createPlusIcon(): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(3f).toFloat()
                strokeCap = Paint.Cap.ROUND
            }
            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val size = dpToPx(9f).toFloat()
                canvas.drawLine(cx - size, cy, cx + size, cy, paint)
                canvas.drawLine(cx, cy - size, cx, cy + size, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    // ---------- Helpers ----------
    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }
}