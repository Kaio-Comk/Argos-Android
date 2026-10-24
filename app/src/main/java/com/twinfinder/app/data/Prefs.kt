package com.twinfinder.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** Ordem dos itens dentro de uma limpeza. */
enum class SortOption { DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, NAME_ASC }

/** Ordem dos grupos (meses, álbuns, tipos) na escolha da limpeza. */
enum class GroupSort { DEFAULT, OLDEST, TOTAL_DESC, TOTAL_ASC, REMAINING_DESC, REMAINING_ASC, NAME_ASC, NAME_DESC, CUSTOM }

/** Ajustes do usuário, todos guardados só no aparelho. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("twinfinder", Context.MODE_PRIVATE)

    var onboardingDone by bool("onboarding_done", false)
    var themeMode by int("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    /** true = manda para a lixeira do sistema (Android 11+); false = apaga de vez. */
    var useTrash by bool("use_trash", true)
    var randomCount by int("random_count", 50)
    var sortOption: SortOption
        get() = runCatching { SortOption.valueOf(sp.getString("sort_option", null)!!) }.getOrDefault(SortOption.DATE_DESC)
        set(v) = sp.edit { putString("sort_option", v.name) }

    // Tela de limpeza
    var showStrip by bool("show_strip", true)
    var showFileInfo by bool("show_file_info", false)
    var videoSound by bool("video_sound", false)
    var animateButtons by bool("animate_buttons", true)
    var proceedOnTop by bool("proceed_on_top", false)
    var shareWithText by bool("share_with_text", false)
    var swipeTutorialSeen by bool("swipe_tutorial_seen", false)
    /** Modo organizado: pastas de destino sempre visíveis na tela de limpeza, mover com um toque. */
    var organizeMode by bool("organize_mode", false)

    // Escolha da limpeza
    var groupSort: GroupSort
        get() = runCatching { GroupSort.valueOf(sp.getString("group_sort", null)!!) }.getOrDefault(GroupSort.DEFAULT)
        set(v) = sp.edit { putString("group_sort", v.name) }
    var hideCompleted by bool("hide_completed", false)

    /** Na primeira exclusão o app pergunta: lixeira ou apagar de vez. */
    var deleteChoiceAsked by bool("delete_choice_asked", false)
    var ratingAsked by bool("rating_asked", false)

    // Lembretes
    var weeklyReminder by bool("weekly_reminder", false)
    var monthlyReminder by bool("monthly_reminder", false)
    var reminderAsked by bool("reminder_asked", false)

    /** Início da contagem das estatísticas (zera junto com "Zerar app"). */
    var statsSince: Long
        get() = sp.getLong("stats_since", 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { statsSince = it }
        set(v) = sp.edit { putLong("stats_since", v) }

    fun clear() = sp.edit { clear() }

    private fun bool(key: String, default: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = sp.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) = sp.edit { putBoolean(key, value) }
    }

    private fun int(key: String, default: Int) = object : kotlin.properties.ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = sp.getInt(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) = sp.edit { putInt(key, value) }
    }

    companion object {
        const val RANDOM_MIN = 30
        const val RANDOM_MAX = 500
    }
}
