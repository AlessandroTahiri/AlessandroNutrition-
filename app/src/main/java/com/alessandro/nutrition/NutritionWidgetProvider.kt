package com.alessandro.nutrition

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class NutritionWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        updateWidget(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        when (intent.action) {
            ACTION_ADD_250 -> prefs.edit().putInt("water_$today", prefs.getInt("water_$today", 0) + 250).apply()
            ACTION_ADD_500 -> prefs.edit().putInt("water_$today", prefs.getInt("water_$today", 0) + 500).apply()
        }
        if (intent.action == ACTION_ADD_250 || intent.action == ACTION_ADD_500 || intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {
        private const val PREFS = "nutrition_v22"
        const val ACTION_ADD_250 = "com.alessandro.nutrition.WIDGET_ADD_250"
        const val ACTION_ADD_500 = "com.alessandro.nutrition.WIDGET_ADD_500"
        const val ACTION_REFRESH = "com.alessandro.nutrition.WIDGET_REFRESH"

        fun requestRefresh(context: Context) {
            context.sendBroadcast(Intent(context, NutritionWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                setPackage(context.packageName)
            })
        }

        private fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NutritionWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }
    }
}

private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
    val prefs = context.getSharedPreferences("nutrition_v22", Context.MODE_PRIVATE)
    val today = LocalDate.now()
    val todayKey = today.toString()
    val water = prefs.getInt("water_$today", 0)
    val goal = prefs.getInt("water_goal", 2000).coerceAtLeast(250)
    val views = RemoteViews(context.packageName, R.layout.widget_nutrition_large)

    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
    views.setTextViewText(R.id.w_date, dateLabel)
    views.setTextViewText(R.id.w_water, "💧 $water / $goal ml")

    val meals = jsonArray(prefs.getString("meals_$todayKey", "[]"))
    val nextMeal = firstPendingLabel(meals, "name", "done") ?: "Apri il piano alimentare"
    views.setTextViewText(R.id.w_meal, "🍽️ $nextMeal")

    val supplements = jsonArray(prefs.getString("supplements", "[]"))
    val pendingSupplements = (0 until supplements.length()).count { i ->
        supplements.optJSONObject(i)?.optString("doneDate") != todayKey
    }
    views.setTextViewText(R.id.w_supplements, "💊 Integratori da assumere: $pendingSupplements")

    val activities = jsonArray(prefs.getString("activities", "[]"))
    val todayActivities = (0 until activities.length()).mapNotNull { activities.optJSONObject(it) }
        .filter { it.optString("date") == todayKey && !it.optBoolean("done") }
    views.setTextViewText(R.id.w_activity, "🏃 Attività: ${todayActivities.firstOrNull()?.optString("title") ?: "nessuna"}")

    val personal = jsonArray(prefs.getString("personal", "[]"))
    val todayPersonal = (0 until personal.length()).mapNotNull { personal.optJSONObject(it) }
        .filter { it.optString("date") == todayKey && !it.optBoolean("done") }
    views.setTextViewText(R.id.w_personal, "🌟 Impegno: ${todayPersonal.firstOrNull()?.optString("title") ?: "nessuno"}")

    val shopping = jsonArray(prefs.getString("shopping_v24", "[]"))
    val toBuy = (0 until shopping.length()).count { i -> !shopping.optJSONObject(i).let { it?.optBoolean("bought") ?: true } }
    views.setTextViewText(R.id.w_shopping, "🛒 Da comprare: $toBuy")
    views.setTextViewText(R.id.w_reminders, "Tocca il widget per aprire Alessandro Nutrition")

    val open = PendingIntent.getActivity(
        context, 2001,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val p250 = PendingIntent.getBroadcast(
        context, 2002,
        Intent(context, NutritionWidgetProvider::class.java).apply { action = NutritionWidgetProvider.ACTION_ADD_250; setPackage(context.packageName) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val p500 = PendingIntent.getBroadcast(
        context, 2003,
        Intent(context, NutritionWidgetProvider::class.java).apply { action = NutritionWidgetProvider.ACTION_ADD_500; setPackage(context.packageName) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    views.setOnClickPendingIntent(R.id.widget_root, open)
    views.setOnClickPendingIntent(R.id.w_add250, p250)
    views.setOnClickPendingIntent(R.id.w_add500, p500)
    manager.updateAppWidget(id, views)
}

private fun jsonArray(raw: String?): JSONArray = try { JSONArray(raw ?: "[]") } catch (_: Exception) { JSONArray() }

private fun firstPendingLabel(array: JSONArray, labelKey: String, doneKey: String): String? {
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        if (!o.optBoolean(doneKey)) return o.optString(labelKey).takeIf { it.isNotBlank() }
    }
    return null
}
