package com.HeheJuice.Notes

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

class NotesUpdate : AppCompatActivity() {

    private var googleSansFlex: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Edge-to-edge bar configuration matching NoteEditorActivity
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

        // Dynamic material theme colors
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.parseColor("#FEF7FF"))
        val surfaceContainerHighestColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        val onPrimaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))

        // Monet Gradient Colors
        val primaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        val tertiaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, Color.parseColor("#FFD8E4"))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            setPadding(dpToPx(16f), 0, dpToPx(16f), dpToPx(16f))
        }

        // ---- Back Button Header ----
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(8f)
            }
        }

        val backBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@NotesUpdate, R.drawable.arrow_back_24px))
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
        root.addView(topBar)

        // ---- Big Title ----
        val bigTitle = TextView(this).apply {
            text = getString(R.string.setting_updates_and_about)
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
        root.addView(bigTitle)

        // ---- Monet Gradient App Info Card ----
        val monetGradientBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(primaryContainerColor, tertiaryContainerColor)
        ).apply {
            cornerRadius = dpToPx(28f).toFloat()
        }

        val appInfoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = monetGradientBg
            setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(28f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(16f)
            }
        }

        // App Icon
        val appIconDrawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) { null }

        if (appIconDrawable != null) {
            val appIconView = ImageView(this).apply {
                setImageDrawable(appIconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpToPx(64f), dpToPx(64f)).apply {
                    bottomMargin = dpToPx(12f)
                }
            }
            appInfoCard.addView(appIconView)
        }

        // App Name
        val appNameText = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(onPrimaryContainerColor)
            gravity = Gravity.CENTER
            setGoogleSansFlexDefault(this, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        appInfoCard.addView(appNameText)

        // App Version
        val versionName = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        val appVersionText = TextView(this).apply {
            text = "Version $versionName"
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            alpha = 0.8f
            gravity = Gravity.CENTER
            setGoogleSansFlexDefault(this, false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4f)
            }
        }
        appInfoCard.addView(appVersionText)

        root.addView(appInfoCard)

        setContentView(root)

        // Apply dynamic system bar paddings
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            root.setPadding(
                dpToPx(16f),
                statusBarTop + dpToPx(8f),
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
