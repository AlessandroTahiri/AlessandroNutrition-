package com.alessandro.nutrition

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

class NutritionWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{update(context,manager,it)}}
    override fun onReceive(context:Context,intent:Intent){
        super.onReceive(context,intent)
        val prefs=context.getSharedPreferences("nutrition_v22",Context.MODE_PRIVATE)
        val today=LocalDate.now().toString()
        when(intent.action){
            ACTION_ADD_250->prefs.edit().putInt("water_$today",prefs.getInt("water_$today",0)+250).apply()
            ACTION_ADD_500->prefs.edit().putInt("water_$today",prefs.getInt("water_$today",0)+500).apply()
        }
        if(intent.action in setOf(ACTION_ADD_250,ACTION_ADD_500,ACTION_REFRESH)){
            val m=AppWidgetManager.getInstance(context)
            m.getAppWidgetIds(ComponentName(context,NutritionWidgetProvider::class.java)).forEach{update(context,m,it)}
        }
    }
    companion object{
        const val ACTION_ADD_250="com.alessandro.nutrition.WIDGET_ADD_250"
        const val ACTION_ADD_500="com.alessandro.nutrition.WIDGET_ADD_500"
        const val ACTION_REFRESH="com.alessandro.nutrition.WIDGET_REFRESH"
        fun requestRefresh(context:Context){context.sendBroadcast(Intent(context,NutritionWidgetProvider::class.java).apply{action=ACTION_REFRESH})}
    }
}

private fun update(context:Context,manager:AppWidgetManager,id:Int){
    val prefs=context.getSharedPreferences("nutrition_v22",Context.MODE_PRIVATE)
    val today=LocalDate.now()
    val water=prefs.getInt("water_$today",0)
    val goal=prefs.getInt("water_goal",2000)
    val views=RemoteViews(context.packageName,R.layout.widget_nutrition_large)

    views.setTextViewText(R.id.w_date,today.toString())
    views.setTextViewText(R.id.w_water,"💧 $water / $goal ml")
    views.setTextViewText(R.id.w_meal,"🍽️ Prossimo pasto: apri per vedere il piano")
    views.setTextViewText(R.id.w_supplements,"💊 Integratori: controlla le assunzioni di oggi")
    views.setTextViewText(R.id.w_activity,"🏃 Attività: controlla gli impegni di oggi")
    views.setTextViewText(R.id.w_personal,"🌟 Vita personale: prossimo impegno")
    views.setTextViewText(R.id.w_shopping,"🛒 Lista della spesa: tocca per aprire")
    views.setTextViewText(R.id.w_reminders,"⏰ Promemoria: pasti · integratori · attività · impegni")

    val open=PendingIntent.getActivity(context,2001,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val p250=PendingIntent.getBroadcast(context,2002,Intent(context,NutritionWidgetProvider::class.java).apply{action=NutritionWidgetProvider.ACTION_ADD_250},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val p500=PendingIntent.getBroadcast(context,2003,Intent(context,NutritionWidgetProvider::class.java).apply{action=NutritionWidgetProvider.ACTION_ADD_500},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    views.setOnClickPendingIntent(R.id.widget_root,open)
    views.setOnClickPendingIntent(R.id.w_add250,p250)
    views.setOnClickPendingIntent(R.id.w_add500,p500)
    manager.updateAppWidget(id,views)
}
