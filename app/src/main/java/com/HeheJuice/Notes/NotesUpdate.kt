package com.HeheJuice.Notes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class NotesUpdate : AppCompatActivity() {

    private var googleSansFlex: Typeface? = null
    private lateinit var updateStatusView: TextView
    private lateinit var updateActionView: TextView
    private lateinit var releaseNotesView: TextView
    private lateinit var downloadProgressText: TextView
    private var downloadTask: DownloadApkTask? = null
    private var downloadedApkFile: File? = null

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
        val onSurfaceVariantColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.parseColor("#49454F"))
        val primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))

        // Monet Gradient Colors
        val primaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        val tertiaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, Color.parseColor("#FFD8E4"))

        // ---------------------------------------------------------------------
        // TRANSPARENT FLOATING TOP BAR + SCROLLVIEW WITH CLIPTOPADDING = FALSE
        // ---------------------------------------------------------------------
        val mainContainer = FrameLayout(this).apply {
            setBackgroundColor(surfaceColor)
        }

        // ScrollView: clipToPadding = false so content scrolls under the top bar
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

        // Top bar: transparent, floats above ScrollView
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

        // Assemble: ScrollView first, topBar on top
        scrollView.addView(root)
        mainContainer.addView(scrollView)
        mainContainer.addView(topBar)
        setContentView(mainContainer)

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
            cornerRadius = dpToPx(16f).toFloat()
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
val appVersionText = TextView(this).apply {
    text = getString(R.string.version_format, getVersionName())
    textSize = 14f
    setTextColor(onPrimaryContainerColor)
    alpha = 0.8f
    gravity = Gravity.CENTER
    // Safe zone padding to prevent text glyph clipping in various languages
    setPadding(dpToPx(16f), dpToPx(2f), dpToPx(16f), dpToPx(2f))
    clipToPadding = false
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

        // ---- Update Checker Card ----
        val updateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surfaceContainerHighestColor)
                cornerRadius = dpToPx(16f).toFloat()
            }
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16f) }
        }

        updateStatusView = TextView(this).apply {
            text = getString(R.string.update_checking)
            textSize = 15f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, false)
            gravity = Gravity.CENTER
        }
        updateCard.addView(updateStatusView)

        releaseNotesView = TextView(this).apply {
            visibility = View.GONE
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, false)
        }
        updateCard.addView(releaseNotesView)

        downloadProgressText = TextView(this).apply {
            text = getString(R.string.zero_percent)
            visibility = View.GONE
            textSize = 14f
            setTextColor(primaryColor)
            setGoogleSansFlexDefault(this, false)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(8f)
            }
        }
        updateCard.addView(downloadProgressText)

        updateActionView = TextView(this).apply {
            text = getString(R.string.action_download)
            textSize = 15f
            setTextColor(onPrimaryContainerColor)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(primaryContainerColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        updateCard.addView(updateActionView)
        root.addView(updateCard)

        // ---- Info Card (Source Code & License) ----
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

        val infoGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        // 1. Source Code Row
        val sourceRow = LinearLayout(this).apply {
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
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HeheJuice/Material-Notes")))
            }
        }

        val sourceTextContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val sourceLabel = TextView(this).apply {
            text = getString(R.string.view_source_code)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
        }

        val sourceSubtitle = TextView(this).apply {
            text = getString(R.string.desc_source)
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

        sourceTextContainer.addView(sourceLabel)
        sourceTextContainer.addView(sourceSubtitle)
        sourceRow.addView(sourceTextContainer)

        // 2. License Row
        val licenseRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGroupItemBg(innerRadiusPx, outerRadiusPx)
            setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setMinimumHeight(dpToPx(56f))
            isClickable = true
            isFocusable = true
            setOnTouchListener(pressScaleTouchListener)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HeheJuice/Material-Notes/blob/main/LICENSE")))
            }
        }

        val licenseTextContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val licenseLabel = TextView(this).apply {
            text = getString(R.string.view_license)
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
        }

        val licenseSubtitle = TextView(this).apply {
            text = getString(R.string.desc_license)
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

        licenseTextContainer.addView(licenseLabel)
        licenseTextContainer.addView(licenseSubtitle)
        licenseRow.addView(licenseTextContainer)

        infoGroup.addView(sourceRow)
        infoGroup.addView(licenseRow)
        root.addView(infoGroup)

        // ---- ACKNOWLEDGMENTS CARD ----
        val creditsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surfaceContainerHighestColor)
                cornerRadius = dpToPx(16f).toFloat()
            }
            setPadding(dpToPx(20f), dpToPx(20f), dpToPx(20f), dpToPx(20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val creditsTitle = TextView(this).apply {
            text = getString(R.string.acknowledgments)
            textSize = 18f
            setTextColor(onPrimaryContainerColor)
            setGoogleSansFlexDefault(this, true)
            setPadding(0, 0, 0, dpToPx(12f))
        }
        creditsCard.addView(creditsTitle)

        // Credit 1: HeheJuice
        val hehejuiceName = "HeheJuice"
        val rowHehejuice = LinearLayout(this).apply {
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

        val hehejuiceAvatarRes = resources.getIdentifier("hehejuice", "drawable", packageName)
        val hehejuiceAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
                marginEnd = dpToPx(16f)
            }
            if (hehejuiceAvatarRes != 0) {
                setImageResource(hehejuiceAvatarRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                }
                clipToOutline = true
            } else {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(primaryColor)
                }
            }
        }

        if (hehejuiceAvatarRes == 0) {
            val initialTv = TextView(this).apply {
                text = "H"
                textSize = 20f
                setTextColor(Color.WHITE)
                setGoogleSansFlexDefault(this, true)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(dpToPx(44f), dpToPx(44f))
            }
            val avatarContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
                    marginEnd = dpToPx(16f)
                }
                addView(hehejuiceAvatar)
                addView(initialTv)
            }
            rowHehejuice.addView(avatarContainer)
        } else {
            rowHehejuice.addView(hehejuiceAvatar)
        }

        val textContainer1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView1 = TextView(this).apply {
            text = hehejuiceName
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            isClickable = true
            isFocusable = true
            setOnClickListener { openTelegram("HeheJuice") }
            setOnTouchListener(pressScaleTouchListener)
        }

        val descView1 = TextView(this).apply {
            text = getString(R.string.credit_hehejuice_desc)
            textSize = 13f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.8f
            setGoogleSansFlexDefault(this, false)
        }
        textContainer1.addView(nameView1)
        textContainer1.addView(descView1)
        rowHehejuice.addView(textContainer1)
        creditsCard.addView(rowHehejuice)

        // Credit 2: Material Design
        val materialName = "Material Design"
        val rowMaterial = LinearLayout(this).apply {
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

        val materialAvatarRes = resources.getIdentifier("androidcredit", "drawable", packageName)
        val materialAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
                marginEnd = dpToPx(16f)
            }
            if (materialAvatarRes != 0) {
                setImageResource(materialAvatarRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                }
                clipToOutline = true
            } else {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(primaryColor)
                }
            }
        }

        if (materialAvatarRes == 0) {
            val initialTv = TextView(this).apply {
                text = "M"
                textSize = 20f
                setTextColor(Color.WHITE)
                setGoogleSansFlexDefault(this, true)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(dpToPx(44f), dpToPx(44f))
            }
            val avatarContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
                    marginEnd = dpToPx(16f)
                }
                addView(materialAvatar)
                addView(initialTv)
            }
            rowMaterial.addView(avatarContainer)
        } else {
            rowMaterial.addView(materialAvatar)
        }

        val textContainer2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView2 = TextView(this).apply {
            text = materialName
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            setGoogleSansFlexDefault(this, true)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m3.material.io/get-started")))
            }
            setOnTouchListener(pressScaleTouchListener)
        }

        val descView2 = TextView(this).apply {
            text = getString(R.string.credit_material_desc)
            textSize = 13f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.8f
            setGoogleSansFlexDefault(this, false)
        }
        textContainer2.addView(nameView2)
        textContainer2.addView(descView2)
        rowMaterial.addView(textContainer2)
        creditsCard.addView(rowMaterial)

// Credit 3: Google Sans Flex
val fontCreditName = "Google Sans Flex"
val rowFont = LinearLayout(this).apply {
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

val fontAvatarRes = resources.getIdentifier("googlesansflex", "drawable", packageName)
val fontAvatar = ImageView(this).apply {
    layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
        marginEnd = dpToPx(16f)
    }
    if (fontAvatarRes != 0) {
        setImageResource(fontAvatarRes)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
        }
        clipToOutline = true
    } else {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primaryColor)
        }
    }
}

if (fontAvatarRes == 0) {
    val fontInitialTv = TextView(this).apply {
        text = "G"
        textSize = 20f
        setTextColor(Color.WHITE)
        setGoogleSansFlexDefault(this, true)
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(dpToPx(44f), dpToPx(44f))
    }
    val fontAvatarContainer = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
            marginEnd = dpToPx(16f)
        }
        addView(fontAvatar)
        addView(fontInitialTv)
    }
    rowFont.addView(fontAvatarContainer)
} else {
    rowFont.addView(fontAvatar)
}

val textContainer3 = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
}

val nameView3 = TextView(this).apply {
    text = fontCreditName
    textSize = 16f
    setTextColor(onSurfaceVariantColor)
    setGoogleSansFlexDefault(this, true)
    isClickable = true
    isFocusable = true
    setOnClickListener {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fonts.google.com")))
    }
    setOnTouchListener(pressScaleTouchListener)
}

val descView3 = TextView(this).apply {
    text = getString(R.string.credit_google_sans_flex_desc)
    textSize = 13f
    setTextColor(onSurfaceVariantColor)
    alpha = 0.8f
    setGoogleSansFlexDefault(this, false)
}
textContainer3.addView(nameView3)
textContainer3.addView(descView3)
rowFont.addView(textContainer3)
creditsCard.addView(rowFont)


        root.addView(creditsCard)

        // Restore cached downloaded APK state if present
        val cachedFile = File(cacheDir, "app-release.apk")
        if (cachedFile.exists()) {
            downloadedApkFile = cachedFile
            updateActionView.text = getString(R.string.action_install)
            updateActionView.isEnabled = true
            updateActionView.visibility = View.VISIBLE
            downloadProgressText.visibility = View.GONE
        }

        updateActionView.setOnClickListener {
            if (updateActionView.text == getString(R.string.action_install) && downloadedApkFile?.exists() == true) {
                installApk(downloadedApkFile!!)
            } else {
                downloadApk()
            }
        }

        val prefsNotes = getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
        val isNetworkAllowed = prefsNotes.getBoolean("app_network_connection", true)
        val versionName = getVersionName()

        if (versionName.contains("Debug", ignoreCase = true)) {
            updateStatusView.text = getString(R.string.update_disabled_debug)
            updateActionView.visibility = View.GONE
            releaseNotesView.visibility = View.GONE
            downloadProgressText.visibility = View.GONE
            deleteCachedApk()
        } else if (!isNetworkAllowed) {
            updateStatusView.text = getString(R.string.please_enable_network_permission)
            updateActionView.visibility = View.GONE
            releaseNotesView.visibility = View.GONE
            downloadProgressText.visibility = View.GONE
        } else {
            checkForUpdates()
        }

        // Apply dynamic system bar paddings
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Position topBar relative to status bar
            topBar.setPadding(
                dpToPx(16f),
                statusBarTop + dpToPx(8f),
                dpToPx(16f),
                dpToPx(8f)
            )

            // Calculate total height occupied by top bar (padding + 50dp button + bottom padding)
            val totalTopBarHeight = statusBarTop + dpToPx(8f + 50f + 8f)

            // Set initial content padding so title sits right below the back button
            root.setPadding(
                dpToPx(16f),
                totalTopBarHeight,
                dpToPx(16f),
                navBarBottom + dpToPx(16f)
            )
            insets
        }
    }

    // ========== Check For Updates Logic ==========
    private fun checkForUpdates() {
        updateStatusView.text = getString(R.string.update_checking)
        updateActionView.visibility = View.GONE
        releaseNotesView.visibility = View.GONE
        downloadProgressText.visibility = View.GONE

        Thread {
            try {
                val url = URL("https://api.github.com/repos/HeheJuice/Material-Notes/releases/latest")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val responseCode = connection.responseCode
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestTag = json.getString("tag_name")
                    val currentVersion = getVersionName()

                    val latestVersion = latestTag.replace(Regex("^[^0-9]*"), "")
                    val currentVer = currentVersion.replace(Regex("^[^0-9]*"), "")
                    val comparison = compareVersions(latestVersion, currentVer)

                    runOnUiThread {
                        if (comparison > 0) {
                            updateStatusView.text = getString(R.string.update_new_version, latestVersion)
                            val releaseBody = json.optString("body", "")
                            if (releaseBody.isNotEmpty()) {
                                releaseNotesView.text = formatReleaseNotes(releaseBody)
                                releaseNotesView.visibility = View.VISIBLE
                                val lp = releaseNotesView.layoutParams as LinearLayout.LayoutParams
                                lp.topMargin = dpToPx(8f)
                                lp.bottomMargin = dpToPx(8f)
                                releaseNotesView.layoutParams = lp
                            } else {
                                releaseNotesView.visibility = View.GONE
                            }

                            updateActionView.text = getString(R.string.action_download)
                            updateActionView.visibility = View.VISIBLE
                            updateActionView.isEnabled = true
                            downloadedApkFile = null
                        } else {
                            updateStatusView.text = getString(R.string.update_latest, currentVer)
                            releaseNotesView.visibility = View.GONE
                            updateActionView.visibility = View.GONE
                            deleteCachedApk()
                        }
                    }
                } else {
                    runOnUiThread {
                        updateStatusView.text = getString(R.string.update_server_error)
                        releaseNotesView.visibility = View.GONE
                        updateActionView.visibility = View.GONE
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    updateStatusView.text = getString(R.string.update_connection_error)
                    releaseNotesView.visibility = View.GONE
                    updateActionView.visibility = View.GONE
                }
            }
        }.start()
    }

    // ========== APK Download Task ==========
    private fun downloadApk() {
        downloadTask?.cancel(true)
        downloadTask = DownloadApkTask()
        downloadTask?.execute()
    }

    inner class DownloadApkTask : AsyncTask<Void, Int, File?>() {
        override fun onPreExecute() {
            updateActionView.text = getString(R.string.zero_percent)
            updateActionView.isEnabled = false
            updateActionView.visibility = View.VISIBLE
            downloadProgressText.visibility = View.GONE
        }

        override fun doInBackground(vararg params: Void?): File? {
            try {
                val url = URL("https://api.github.com/repos/HeheJuice/Material-Notes/releases/latest")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode != HttpsURLConnection.HTTP_OK) return null

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                connection.disconnect()
                if (apkUrl == null) return null

                val apkConnection = URL(apkUrl).openConnection() as HttpsURLConnection
                apkConnection.connectTimeout = 10000
                apkConnection.readTimeout = 10000
                val contentLength = apkConnection.contentLength
                val inputStream = apkConnection.inputStream

                val outputFile = File(cacheDir, "app-release.apk")
                if (outputFile.exists()) outputFile.delete()

                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = (totalBytesRead * 100 / contentLength)
                        publishProgress(progress)
                    }
                }
                outputStream.close()
                inputStream.close()
                apkConnection.disconnect()
                return outputFile
            } catch (e: Exception) {
                Log.e("NotesUpdate", "Download failed", e)
                return null
            }
        }

        override fun onProgressUpdate(vararg values: Int?) {
            val progress = values[0] ?: 0
            updateActionView.text = getString(R.string.progress_format, progress)
        }

        override fun onPostExecute(result: File?) {
            if (result != null && result.exists()) {
                downloadedApkFile = result
                updateActionView.text = getString(R.string.action_install)
                updateActionView.isEnabled = true
            } else {
                Toast.makeText(this@NotesUpdate, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                updateActionView.text = getString(R.string.action_download)
                updateActionView.isEnabled = true
                downloadedApkFile = null
            }
            downloadTask = null
            downloadProgressText.visibility = View.GONE
        }
    }

    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, getString(R.string.allow_unknown_sources), Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                return
            }
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.no_app_found_install), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("NotesUpdate", "Install failed", e)
            Toast.makeText(this, getString(R.string.install_error, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteCachedApk() {
        try {
            val file = File(cacheDir, "app-release.apk")
            if (file.exists()) {
                file.delete()
                if (downloadedApkFile == file) downloadedApkFile = null
                if (updateActionView.text == getString(R.string.action_install)) {
                    updateActionView.text = getString(R.string.action_download)
                    updateActionView.isEnabled = true
                }
            }
        } catch (_: Exception) { }
    }

    private fun openTelegram(username: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username")))
            } catch (_: Exception) { }
        }
    }

    private fun formatReleaseNotes(body: String): SpannableStringBuilder {
        val lines = body.split("\n")
        val spannable = SpannableStringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") || trimmed.startsWith("#### ") -> {
                    val text = trimmed.replace(Regex("^#+\\s*"), "")
                    val span = SpannableString(text + "\n")
                    span.setSpan(AbsoluteSizeSpan(dpToPx(16f)), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.append(span)
                }
                trimmed.startsWith("- ") -> spannable.append("• " + trimmed.substring(2) + "\n")
                else -> if (trimmed.isNotEmpty()) spannable.append(trimmed + "\n") else spannable.append("\n")
            }
        }
        return spannable
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.replace(Regex("[^0-9.]"), "")
        val clean2 = v2.replace(Regex("[^0-9.]"), "")
        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = if (i < parts1.size) parts1[i] else 0
            val p2 = if (i < parts2.size) parts2[i] else 0
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun getVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
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