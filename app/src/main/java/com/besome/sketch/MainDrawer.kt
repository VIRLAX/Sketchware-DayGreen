package com.besome.sketch

import a.a.a.mB
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.besome.sketch.help.ProgramInfoActivity
import com.besome.sketch.tools.NewKeyStoreActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.Side
import extensions.anbui.daydream.activity.DayDreamCleanUp
import extensions.anbui.daydream.activity.project.settings.DayDreamUniversalSettingsActivity
import mod.hilal.saif.activities.tools.AppSettings
import pro.sketchware.R
import pro.sketchware.utility.LocaleManager
import pro.sketchware.utility.UI
import pro.sketchware.utility.theme.ThemeManager

class MainDrawer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.navigationViewStyle) : NavigationView(MaterialThemeOverlay.wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr) {
    companion object { private val DEF_STYLE_RES = R.style.Widget_SketchwarePro_NavigationView_Main }

    init {
        val ctx = getContext()
        val layoutDirection = ctx.resources.configuration.layoutDirection
        Insetter.builder().margin(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.navigationBars(), Side.create(layoutDirection == LAYOUT_DIRECTION_LTR, false, layoutDirection == LAYOUT_DIRECTION_RTL, false)).applyToView(this)
        val headerView = LayoutInflater.from(ctx).inflate(R.layout.main_drawer_header, null) as ViewGroup
        headerView.findViewById<View>(R.id.status_bar_overlapper).minimumHeight = UI.getStatusBarHeight(ctx)
        addHeaderView(headerView)
        inflateMenu(R.menu.main_drawer_menu)
        setNavigationItemSelectedListener { item ->
            initializeSocialLinks(item.itemId)
            initializeDrawerItems(item.itemId)
            close()
            false
        }
    }

    private fun initializeSocialLinks(@IdRes id: Int) {
        if (!mB.a()) {
            @StringRes val url = when (id) {
                R.id.social_discord -> R.string.link_discord_invite
                R.id.social_github -> R.string.link_github_url
                R.id.social_telegram -> R.string.link_telegram_invite
                else -> -1
            }
            if (url != -1) openUrl(context.getString(url))
        }
    }

    private fun initializeDrawerItems(@IdRes id: Int) {
        val activity = unwrap(context) ?: return
        when (id) {
            R.id.program_info -> activity.startActivityForResult(Intent(activity, ProgramInfoActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }, 105)
            R.id.daydream_cleanup -> activity.startActivity(Intent(activity, DayDreamCleanUp::class.java))
            R.id.app_settings -> activity.startActivity(Intent(activity, AppSettings::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP })
            R.id.language_theme_settings -> showLanguageAndThemePicker(activity)
            R.id.create_release_keystore -> activity.startActivity(Intent(activity, NewKeyStoreActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP })
            R.id.daydream_settings -> activity.startActivity(Intent(activity, DayDreamUniversalSettingsActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP })
        }
    }

    private fun showLanguageAndThemePicker(activity: Activity) {
        val languages = arrayOf(
            activity.getString(R.string.language_english), activity.getString(R.string.language_indonesian), activity.getString(R.string.language_chinese),
            activity.getString(R.string.language_arabic), activity.getString(R.string.language_spanish), activity.getString(R.string.language_javanese)
        )
        val codes = arrayOf(LocaleManager.ENGLISH, LocaleManager.INDONESIAN, LocaleManager.CHINESE, LocaleManager.ARABIC, LocaleManager.SPANISH, LocaleManager.JAVANESE)
        val selected = codes.indexOf(LocaleManager.getLanguage(activity))
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.title_language)
            .setSingleChoiceItems(languages, selected) { dialog: DialogInterface, which: Int ->
                LocaleManager.setLanguage(activity, codes[which])
                dialog.dismiss()
                activity.recreate()
            }
            .setNeutralButton(R.string.title_editor_theme) { _: DialogInterface, _: Int -> showThemePicker(activity) }
            .setNegativeButton(R.string.common_word_cancel, null)
            .show()
    }

    private fun showThemePicker(activity: Activity) {
        val themes = arrayOf(activity.getString(R.string.theme_rainbow), activity.getString(R.string.theme_one_dark_pro), activity.getString(R.string.theme_synthwave), activity.getString(R.string.theme_tokyo_night))
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.title_editor_theme)
            .setItems(themes) { _: DialogInterface, which: Int ->
                activity.getSharedPreferences("daygreen_editor_theme", Context.MODE_PRIVATE).edit().putInt("selected", which).apply()
                ThemeManager.applyTheme(activity, if (which == 0) ThemeManager.THEME_LIGHT else ThemeManager.THEME_DARK)
                activity.recreate()
            }
            .setNegativeButton(R.string.common_word_cancel, null)
            .show()
    }

    private fun openUrl(url: String) { unwrap(context)?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    private fun unwrap(context: Context): Activity? { var current: Context? = context; while (current !is Activity && current is ContextWrapper) current = current.baseContext; return current as? Activity }
    private fun close() { var p: ViewParent? = parent; while (p != null) { if (p is DrawerLayout) { p.closeDrawer(this); return }; p = p.parent } }
}
