package com.HeheJuice.Notes

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

class NotesEditorHelp : AppCompatActivity() {

    private var googleSansFlex: Typeface? = null

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

        googleSansFlex = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (_: Exception) { null }

        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.parseColor("#FEF7FF"))
        val surfaceContainerHighestColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        val onPrimaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))
        val onSurfaceVariantColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.parseColor("#49454F"))

        val primaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        val tertiaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, Color.parseColor("#FFD8E4"))

        val outerRadiusPx = dpToPx(16f).toFloat()
        val innerRadiusPx = dpToPx(4f).toFloat()

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

        val mainContainer = FrameLayout(this).apply {
            setBackgroundColor(surfaceColor)
        }

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Floating Top Bar with Back Button matching NotesUpdate
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@NotesEditorHelp, R.drawable.arrow_back_24px))
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
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        // Big Title matching NotesUpdate
        val bigTitle = TextView(this).apply {
            text = getString(R.string.helper_title)
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

        // Gradient Card with centered drawable
        val monetGradientBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(primaryContainerColor, tertiaryContainerColor)
        ).apply {
            cornerRadius = dpToPx(16f).toFloat()
        }

        val gradientCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = monetGradientBg
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(16f)
            }
        }

        val headerImage = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@NotesEditorHelp, R.drawable.hehejuicebar))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(48f)
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        gradientCard.addView(headerImage)

        // Options Group 1 (Main Editor Options)
        val optionsGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val optionsHeader = TextView(this).apply {
            text = getString(R.string.help_sub_buttom)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            setPadding(dpToPx(8f), dpToPx(12f), dpToPx(8f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        optionsGroup.addView(optionsHeader)

        val optionsList = listOf(
            Triple(R.drawable.content_copy_24px, R.string.copy_title, R.string.copy_desc),
            Triple(R.drawable.content_paste_24px, R.string.paste_title, R.string.paste_desc),
            Triple(R.drawable.format_bold_24px, R.string.bold_title, R.string.bold_desc),
            Triple(R.drawable.title_24px, R.string.title_title, R.string.title_desc)
        )

        optionsList.forEachIndexed { index, (iconRes, titleRes, descRes) ->
            val isFirst = index == 0
            val isLast = index == optionsList.lastIndex

            val topR = if (isFirst) outerRadiusPx else innerRadiusPx
            val bottomR = if (isLast) outerRadiusPx else innerRadiusPx

            val optionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGroupItemBg(topR, bottomR)
                setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (!isLast) bottomMargin = dpToPx(2f)
                }
                setMinimumHeight(dpToPx(56f))
            }

            val iconIv = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@NotesEditorHelp, iconRes))
                setColorFilter(onSurfaceVariantColor)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    marginEnd = dpToPx(16f)
                }
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val titleTv = TextView(this).apply {
                text = getString(titleRes)
                textSize = 16f
                setTextColor(onSurfaceVariantColor)
                setGoogleSansFlexDefault(this, true)
            }

            val descTv = TextView(this).apply {
                text = getString(descRes)
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

            textContainer.addView(titleTv)
            textContainer.addView(descTv)

            optionRow.addView(iconIv)
            optionRow.addView(textContainer)

            optionsGroup.addView(optionRow)
        }

        // ================================================================
        // TIPS GROUP (Landscape + Audio helper)
        // ================================================================
        val tipsGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val tipsHeader = TextView(this).apply {
            text = getString(R.string.help_tips)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            setPadding(dpToPx(8f), dpToPx(12f), dpToPx(8f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tipsGroup.addView(tipsHeader)

        // List of tips: (icon, titleRes, descRes)
        val tipsList = listOf(
            Triple(R.drawable.mobile_landscape_24px, R.string.landscape_title, R.string.landscape_desc),
            Triple(R.drawable.audio_file_24px, R.string.audio_helper_title, R.string.audio_helper_desc),
            Triple(R.drawable.text_select_move_down_24px, R.string.texts_helper_title, R.string.texts_helper_desc)
        )

        tipsList.forEachIndexed { index, (iconRes, titleRes, descRes) ->
            val isFirst = index == 0
            val isLast = index == tipsList.lastIndex

            val topR = if (isFirst) outerRadiusPx else innerRadiusPx
            val bottomR = if (isLast) outerRadiusPx else innerRadiusPx

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGroupItemBg(topR, bottomR)
                setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (!isLast) bottomMargin = dpToPx(2f)
                }
                setMinimumHeight(dpToPx(56f))
            }

            val iconIv = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@NotesEditorHelp, iconRes))
                setColorFilter(onSurfaceVariantColor)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    marginEnd = dpToPx(16f)
                }
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val titleTv = TextView(this).apply {
                text = getString(titleRes)
                textSize = 16f
                setTextColor(onSurfaceVariantColor)
                setGoogleSansFlexDefault(this, true)
            }

            val descTv = TextView(this).apply {
                text = getString(descRes)
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

            textContainer.addView(titleTv)
            textContainer.addView(descTv)

            row.addView(iconIv)
            row.addView(textContainer)

            tipsGroup.addView(row)
        }

        root.addView(bigTitle)
        root.addView(gradientCard)
        root.addView(optionsGroup)
        root.addView(tipsGroup)  // Replaces old landscapeGroup

        scrollView.addView(root)
        mainContainer.addView(scrollView)
        mainContainer.addView(topBar)
        setContentView(mainContainer)

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            topBar.setPadding(
                dpToPx(16f),
                statusBarTop + dpToPx(8f),
                dpToPx(16f),
                dpToPx(8f)
            )

            val totalTopBarHeight = statusBarTop + dpToPx(8f + 50f + 8f)

            root.setPadding(
                dpToPx(16f),
                totalTopBarHeight,
                dpToPx(16f),
                navBarBottom + dpToPx(16f)
            )
            insets
        }
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
}