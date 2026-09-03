package com.alessandro.nutrition

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val PREFS = "nutrition_v22"
private const val CHANNEL = "nutrition_reminders"

private val DarkBg = Color(0xFF07111E)
private val DarkCard = Color(0xFF101D2B)
private val DarkCard2 = Color(0xFF142435)
private val LightBg = Color(0xFFF4F7FA)

data class PlanDay(val date:String,val breakfast:String,val snack:String,val lunch:String)
data class ReminderSettings(val enabled:Boolean=true,val daysBefore:Int=0,val hoursBefore:Int=0,val minutesBefore:Int=10)
data class MealEntry(val id:String,val date:String,val name:String,val time:String,val description:String,val optional:Boolean=false,val done:Boolean=false,val reminder:ReminderSettings=ReminderSettings())
data class SimpleEntry(val id:Long,val title:String,val date:String,val time:String="",val notes:String="",val done:Boolean=false,val type:String="",val reminder:Boolean=false)
data class SupplementEntry(val id:Long,val name:String,val dose:String,val days:String,val time:String,val notes:String,val doneDate:String="",val reminder:Boolean=true)
data class ShoppingItem(val id:Long,val name:String,val quantity:Double=1.0,val unit:String="pz",val category:String="",val note:String="",val bought:Boolean=false)
data class GoalEntry(val id:Long,val title:String,val category:String,val target:Double,val progress:Double,val unit:String,val automatic:Boolean,val linkedMetric:String,val deadline:String="",val notes:String="")
data class CalendarEvent(val id:Long,val title:String,val date:String,val time:String,val category:String,val notes:String="",val reminder:Boolean=false,val recurrence:String="Nessuna",val durationMinutes:Int=60,val reminderMinutes:Int=10)
data class ProfessionalLink(val id:Long,val name:String,val role:String,val inviteCode:String,val active:Boolean=true,val permissions:Set<String> = emptySet())
data class Proposal(val id:Long,val professional:String,val area:String,val note:String,val date:String,val status:String="Da valutare")
data class EventCategory(val name:String,val icon:String,val color:Color)

private val defaultEventCategories=listOf(
    EventCategory("Palestra","🏋️",Color(0xFF7E57C2)),
    EventCategory("Camminata","🚶",Color(0xFF43A047)),
    EventCategory("Alimentazione","🍽️",Color(0xFFFB8C00)),
    EventCategory("Integratori","💊",Color(0xFF26A69A)),
    EventCategory("Salute / Visite","🩺",Color(0xFFE53935)),
    EventCategory("Lavoro","💼",Color(0xFF1E88E5)),
    EventCategory("Famiglia","👨‍👩‍👦",Color(0xFFEC407A)),
    EventCategory("Vita personale","🏠",Color(0xFF8D6E63)),
    EventCategory("Promemoria","📌",Color(0xFFFDD835)),
    EventCategory("Altro","✨",Color(0xFF78909C))
)

private fun categoryFor(name:String)=defaultEventCategories.firstOrNull{it.name==name}?:defaultEventCategories.last()

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        val plan=loadPlan(this)
        setContent{ NutritionApp(plan) }
    }
}

fun loadPlan(context:Context):List<PlanDay>{
    return try{
        val arr=JSONArray(context.assets.open("annual_plan.json").bufferedReader().use{it.readText()})
        buildList{
            for(i in 0 until arr.length()){
                val o=arr.getJSONObject(i)
                add(PlanDay(
                    o.optString("date"),
                    o.optString("breakfast","Colazione da definire"),
                    o.optString("snack","Spuntino da definire"),
                    o.optString("lunch","Pranzo da definire")
                ))
            }
        }
    }catch(_:Exception){ emptyList() }
}

@Composable
fun NutritionApp(plan:List<PlanDay>){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)}
    var nav by rememberSaveable{mutableIntStateOf(0)}
    var secondary by rememberSaveable{mutableStateOf<String?>(null)}
    var theme by rememberSaveable{mutableStateOf(prefs.getString("theme","system")?:"system")}
    var accent by rememberSaveable{mutableStateOf(prefs.getString("accent","aqua")?:"aqua")}
    val dark=when(theme){"light"->false;"dark"->true;else->androidx.compose.foundation.isSystemInDarkTheme()}
    val primary=accentColor(accent,dark)

    NotificationPermissionRequest()
    var showSetup by remember{mutableStateOf(!prefs.getBoolean("profile_setup_done",false))}

    val scheme=if(dark) darkColorScheme(
        primary=primary,background=DarkBg,surface=DarkCard,surfaceVariant=DarkCard2,
        onBackground=Color(0xFFF6FAFF),onSurface=Color(0xFFF6FAFF),onSurfaceVariant=Color(0xFFB8C7D9)
    ) else lightColorScheme(
        primary=primary,background=LightBg,surface=Color.White,surfaceVariant=Color(0xFFEAF0F4),
        onBackground=Color(0xFF101820),onSurface=Color(0xFF101820),onSurfaceVariant=Color(0xFF52606D)
    )

    MaterialTheme(colorScheme=scheme){
        if(showSetup){ ProfileSetupDialog(prefs,onDismiss={showSetup=false}) }
        Scaffold(
            containerColor=MaterialTheme.colorScheme.background,
            bottomBar={
                if(secondary==null){
                    NavigationBar{
                        listOf("🏠" to "Home","🏋️" to "Allenamenti","🍽️" to "Piano","📅" to "Calendario").forEachIndexed{i,p->
                            NavigationBarItem(
                                selected=nav==i,
                                onClick={nav=i},
                                icon={Text(p.first,fontSize=20.sp)},
                                label={Text(p.second,fontSize=10.sp)}
                            )
                        }
                    }
                }
            }
        ){pad->
            Box(Modifier.padding(pad).fillMaxSize()){
                if(secondary!=null){
                    SecondaryScreen(
                        secondary!!,plan,prefs,context,
                        onBack={secondary=null},
                        theme=theme,onTheme={theme=it;prefs.edit().putString("theme",it).apply()},
                        accent=accent,onAccent={accent=it;prefs.edit().putString("accent",it).apply()}
                    )
                }else{
                    when(nav){
                        0->HomeScreen(plan,prefs,context,onOpen={secondary=it})
                        1->TrainingScreen(prefs,context)
                        2->PlanScreen(plan,prefs,context)
                        else->CalendarScreen(prefs,context)
                    }
                }
            }
        }
    }
}

private fun accentColor(name:String,dark:Boolean)=when(name){
    "blue"->if(dark)Color(0xFF64B5F6) else Color(0xFF1565C0)
    "green"->if(dark)Color(0xFF81C784) else Color(0xFF2E7D32)
    "orange"->if(dark)Color(0xFFFFB74D) else Color(0xFFEF6C00)
    "purple"->if(dark)Color(0xFFBA68C8) else Color(0xFF7B1FA2)
    else->if(dark)Color(0xFF32D6C5) else Color(0xFF008C82)
}

@Composable
fun NotificationPermissionRequest(){
    if(Build.VERSION.SDK_INT>=33){
        val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
        LaunchedEffect(Unit){launcher.launch(Manifest.permission.POST_NOTIFICATIONS)}
    }
}

@Composable
fun ScreenTitle(title:String,subtitle:String?=null,trailing:(@Composable () -> Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
            subtitle?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}
        }
        trailing?.invoke()
    }
}

@Composable
fun AppCard(content:@Composable ColumnScope.()->Unit){
    Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

@Composable
fun BackHeader(title:String,onBack:()->Unit){
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
        TextButton(onClick=onBack){Text("←")}
        Text(title,fontWeight=FontWeight.ExtraBold,fontSize=22.sp)
    }
}

@Composable
fun CircleCheck(done:Boolean,onClick:()->Unit){
    Surface(
        Modifier.size(42.dp).clickable{onClick()},
        shape=CircleShape,
        color=if(done)MaterialTheme.colorScheme.primary.copy(alpha=.18f) else Color.Transparent,
        border=BorderStroke(2.dp,if(done)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ){Box(contentAlignment=Alignment.Center){Text(if(done)"✓" else "",fontWeight=FontWeight.Bold)}}
}

@Composable
fun HomeScreen(plan:List<PlanDay>,prefs:SharedPreferences,context:Context,onOpen:(String)->Unit){
    val today=LocalDate.now()
    var refresh by remember{mutableIntStateOf(0)}
    val waterKey="water_$today"
    var water by remember(refresh){mutableIntStateOf(prefs.getInt(waterKey,0))}
    val goal=prefs.getInt("water_goal",2000).coerceAtLeast(250)
    val meals=remember(refresh){loadMealsFor(today,plan,prefs)}
    val supplements=remember(refresh){loadSupplements(prefs)}
    val activities=remember(refresh){loadSimple(prefs,"activities").filter{it.date==today.toString()}}
    val personal=remember(refresh){loadSimple(prefs,"personal").filter{it.date==today.toString()}}
    var addActivity by remember{mutableStateOf(false)}
    var addPersonal by remember{mutableStateOf(false)}
    var addSupplement by remember{mutableStateOf(false)}

    if(addActivity){
        SimpleEntryDialog("Nuova attività",today.toString(),onDismiss={addActivity=false}){e->
            saveSimple(prefs,"activities",loadSimple(prefs,"activities")+e.copy(type="Attività"))
            addActivity=false;refresh++;NutritionWidgetProvider.requestRefresh(context)
        }
    }
    if(addPersonal){
        SimpleEntryDialog("Nuovo impegno personale",today.toString(),onDismiss={addPersonal=false}){e->
            saveSimple(prefs,"personal",loadSimple(prefs,"personal")+e.copy(type="Personale"))
            addPersonal=false;refresh++;NutritionWidgetProvider.requestRefresh(context)
        }
    }
    if(addSupplement){
        SupplementDialog(onDismiss={addSupplement=false}){s->
            saveSupplements(prefs,loadSupplements(prefs)+s)
            addSupplement=false;refresh++;NutritionWidgetProvider.requestRefresh(context)
        }
    }

    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            ScreenTitle(
                "Ciao ${prefs.getString("profile_name","Alessandro") ?: "Alessandro"} 👋",
                "${today.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)}",
                trailing={TextButton(onClick={onOpen("menu")}){Text("☰",fontSize=22.sp)}}
            )
        }
        item{
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("💧 Idratazione",fontWeight=FontWeight.Bold)
                        Text("$water / $goal ml · ${((water.toFloat()/goal)*100).coerceIn(0f,100f).roundToInt()}%",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick={onOpen("water")}){Text("Dettagli")}
                }
                LinearProgressIndicator(progress={ (water.toFloat()/goal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={water+=250;prefs.edit().putInt(waterKey,water).apply();NutritionWidgetProvider.requestRefresh(context)},modifier=Modifier.weight(1f)){Text("+250")}
                    OutlinedButton(onClick={water+=500;prefs.edit().putInt(waterKey,water).apply();NutritionWidgetProvider.requestRefresh(context)},modifier=Modifier.weight(1f)){Text("+500")}
                }
            }
        }
        item{
            AppCard{
                Text("🍽️ Alimentazione di oggi",fontWeight=FontWeight.Bold)
                meals.forEach{m->
                    Row(verticalAlignment=Alignment.CenterVertically){
                        CircleCheck(m.done){
                            val updated=meals.map{if(it.id==m.id)it.copy(done=!m.done)else it}
                            saveMeals(prefs,today,updated);refresh++;NutritionWidgetProvider.requestRefresh(context)
                        }
                        Column(Modifier.weight(1f).padding(start=10.dp)){
                            Text("${m.time} · ${m.name}",fontWeight=FontWeight.SemiBold)
                            Text(m.description,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                        }
                        TextButton(onClick={
                            val updated=meals.map{if(it.id==m.id) it.copy(reminder=m.reminder.copy(enabled=!m.reminder.enabled)) else it}
                            saveMeals(prefs,today,updated);refresh++;NutritionWidgetProvider.requestRefresh(context)
                        }){Text(if(m.reminder.enabled)"🔔" else "🔕",fontSize=20.sp)}
                    }
                }
                TextButton(onClick={onOpen("plan")}){Text("Apri piano alimentare")}
            }
        }
        item{
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("💊 Integratori di oggi",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    FilledTonalButton(onClick={addSupplement=true}){Text("+")}
                }
                supplements.forEach{s->
                    val done=s.doneDate==today.toString()
                    Row(verticalAlignment=Alignment.CenterVertically){
                        CircleCheck(done){
                            val upd=loadSupplements(prefs).map{
                                if(it.id==s.id)it.copy(doneDate=if(done)"" else today.toString()) else it
                            }
                            saveSupplements(prefs,upd);refresh++;NutritionWidgetProvider.requestRefresh(context)
                        }
                        Column(Modifier.weight(1f).padding(start=10.dp)){
                            Text(s.name,fontWeight=FontWeight.SemiBold)
                            Text("${s.dose} · ${s.time}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick={
                            val upd=loadSupplements(prefs).map{if(it.id==s.id)it.copy(reminder=!s.reminder)else it}
                            saveSupplements(prefs,upd);refresh++;NutritionWidgetProvider.requestRefresh(context)
                        }){Text(if(s.reminder)"🔔" else "🔕",fontSize=20.sp)}
                    }
                }
                if(supplements.isEmpty()) Text("Aggiungi Vitamine, Magnesio o altri integratori.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{
            AppCard{
                Row{Text("🏃 Attività di oggi",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));FilledTonalButton(onClick={addActivity=true}){Text("+")}}
                activities.forEach{e->SimpleHomeRow(e,prefs,"activities"){refresh++;NutritionWidgetProvider.requestRefresh(context)}}
                if(activities.isEmpty())Text("Nessuna attività programmata.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{
            AppCard{
                Row{Text("🌟 Vita personale",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));FilledTonalButton(onClick={addPersonal=true}){Text("+")}}
                personal.forEach{e->SimpleHomeRow(e,prefs,"personal"){refresh++;NutritionWidgetProvider.requestRefresh(context)}}
                if(personal.isEmpty())Text("Nessun impegno per oggi.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("🛒 Lista della spesa",fontWeight=FontWeight.Bold);val sh=loadShopping(prefs);Text("${sh.count{!it.bought}} da comprare · ${sh.count{it.bought}} acquistati",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Button(onClick={onOpen("shopping")}){Text("Apri")}
                }
            }
        }
        item{
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("📊 Resoconto della giornata",fontWeight=FontWeight.Bold);Text("Registra costanza, umore e note.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Button(onClick={onOpen("report")}){Text("Compila")}
                }
            }
        }
    }
}

@Composable
fun SimpleHomeRow(e:SimpleEntry,prefs:SharedPreferences,key:String,onChange:()->Unit){
    Row(verticalAlignment=Alignment.CenterVertically){
        CircleCheck(e.done){
            saveSimple(prefs,key,loadSimple(prefs,key).map{if(it.id==e.id)it.copy(done=!e.done)else it});onChange()
        }
        Column(Modifier.weight(1f).padding(start=10.dp)){Text(e.title,fontWeight=FontWeight.SemiBold);Text("${e.time} ${e.notes}".trim(),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        TextButton(onClick={
            saveSimple(prefs,key,loadSimple(prefs,key).map{if(it.id==e.id)it.copy(reminder=!e.reminder)else it});onChange()
        }){Text(if(e.reminder)"🔔" else "🔕",fontSize=19.sp)}
    }
}

@Composable
fun TrainingScreen(prefs:SharedPreferences,context:Context){
    var refresh by remember{mutableIntStateOf(0)}
    var add by remember{mutableStateOf(false)}
    var preset by remember{mutableStateOf("")}
    val list=remember(refresh){loadSimple(prefs,"activities")}
    val presets=listOf("🏋️ Palestra","🚶 Camminata","🤸 Allenamento corpo libero")

    if(add){
        SimpleEntryDialog(
            if(preset.isBlank()) "Nuovo allenamento" else preset,
            LocalDate.now().toString(),
            onDismiss={add=false;preset=""}
        ){e->
            val title=if(preset.isBlank()) e.title else preset
            saveSimple(prefs,"activities",list+e.copy(title=title,type="Allenamento"))
            add=false;preset="";refresh++;NutritionWidgetProvider.requestRefresh(context)
        }
    }

    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ScreenTitle("Allenamenti","Attività predefinite e storico",trailing={
            Button(onClick={preset="";add=true}){Text("+ Altro")}
        })}
        item{
            AppCard{
                Text("Tipi di allenamento",fontWeight=FontWeight.ExtraBold)
                presets.forEach{kind->
                    FilledTonalButton(
                        onClick={preset=kind;add=true},
                        modifier=Modifier.fillMaxWidth()
                    ){Text(kind)}
                }
                Text("Puoi aggiungere anche qualsiasi altro tipo di attività.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{Text("Storico",fontWeight=FontWeight.ExtraBold,fontSize=18.sp)}
        items(list.sortedWith(compareByDescending<SimpleEntry>{it.date}.thenByDescending{it.time})){e->
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    CircleCheck(e.done){
                        saveSimple(prefs,"activities",list.map{if(it.id==e.id)it.copy(done=!e.done)else it})
                        refresh++;NutritionWidgetProvider.requestRefresh(context)
                    }
                    Column(Modifier.weight(1f).padding(start=10.dp)){
                        Text(e.title,fontWeight=FontWeight.Bold)
                        Text("${e.date} · ${e.time}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(e.notes.isNotBlank())Text(e.notes,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick={
                        saveSimple(prefs,"activities",list.map{if(it.id==e.id)it.copy(reminder=!e.reminder)else it})
                        refresh++;NutritionWidgetProvider.requestRefresh(context)
                    }){Text(if(e.reminder)"🔔" else "🔕")}
                }
                TextButton(onClick={saveSimple(prefs,"activities",list.filterNot{it.id==e.id});refresh++;NutritionWidgetProvider.requestRefresh(context)}){Text("Elimina")}
            }
        }
    }
}

@Composable
fun PlanScreen(plan:List<PlanDay>,prefs:SharedPreferences,context:Context){
    val today=LocalDate.now()
    val firstOfMonth=today.withDayOfMonth(1)
    val cycleStart=firstOfMonth.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val currentIndex=((java.time.temporal.ChronoUnit.DAYS.between(cycleStart,today)/7).toInt()).coerceIn(0,3)
    var weekIndex by rememberSaveable{mutableIntStateOf(currentIndex)}
    var selected by rememberSaveable{mutableStateOf(cycleStart.plusWeeks(currentIndex.toLong()).plusDays((today.dayOfWeek.value-1).toLong()).toString())}
    var refresh by remember{mutableIntStateOf(0)}
    val start=cycleStart.plusWeeks(weekIndex.toLong())
    val selectedDate=LocalDate.parse(selected)
    val date=if(selectedDate.isBefore(start)||selectedDate.isAfter(start.plusDays(6))) start else selectedDate
    val meals=remember(date.toString(),refresh){loadMealsFor(date,plan,prefs)}
    val dayNames=listOf("Lun","Mar","Mer","Gio","Ven","Sab","Dom")

    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            ScreenTitle(
                "Piano alimentare",
                "Ciclo di 4 settimane · ${today.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${today.year}"
            )
        }
        item{
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    OutlinedButton(
                        onClick={if(weekIndex>0){weekIndex--;selected=cycleStart.plusWeeks(weekIndex.toLong()).toString()}},
                        enabled=weekIndex>0
                    ){Text("‹")}
                    Text("Settimana ${weekIndex+1}",fontWeight=FontWeight.ExtraBold,fontSize=20.sp,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(
                        onClick={if(weekIndex<3){weekIndex++;selected=cycleStart.plusWeeks(weekIndex.toLong()).toString()}},
                        enabled=weekIndex<3
                    ){Text("›")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    (0..3).forEach{i->
                        FilterChip(selected=weekIndex==i,onClick={weekIndex=i;selected=cycleStart.plusWeeks(i.toLong()).toString()},label={Text("${i+1}")},modifier=Modifier.weight(1f))
                    }
                }
                Text("${start.format(DateTimeFormatter.ofPattern("dd/MM"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{
            AppCard{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    (0..6).forEach{i->
                        val d=start.plusDays(i.toLong())
                        val isSel=d==date
                        val isToday=d==today
                        Surface(
                            modifier=Modifier.weight(1f).clickable{selected=d.toString()},
                            shape=RoundedCornerShape(12.dp),
                            color=if(isSel)MaterialTheme.colorScheme.primary.copy(alpha=.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),
                            border=BorderStroke(if(isToday)2.dp else 1.dp,if(isToday)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        ){
                            Column(Modifier.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Text(dayNames[i],fontSize=10.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                                Text("${d.dayOfMonth}",fontWeight=FontWeight.ExtraBold,fontSize=15.sp)
                            }
                        }
                    }
                }
            }
        }
        item{
            Text(
                "${date.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)}",
                fontWeight=FontWeight.ExtraBold,fontSize=18.sp
            )
        }
        item{
            if(date.dayOfWeek==DayOfWeek.SUNDAY){
                var free by remember(date.toString(),refresh){mutableStateOf(prefs.getBoolean("sunday_free_$date",true))}
                AppCard{
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("Domenica libera",fontWeight=FontWeight.Bold)
                            Text("Se attiva non vengono inseriti pasti automatici.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked=free,onCheckedChange={free=it;prefs.edit().putBoolean("sunday_free_$date",it).apply();refresh++})
                    }
                }
            }
        }
        items(meals){m->
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    CircleCheck(m.done){saveMeals(prefs,date,meals.map{if(it.id==m.id)it.copy(done=!m.done)else it});refresh++}
                    Column(Modifier.weight(1f).padding(start=10.dp)){
                        Text("${m.time} · ${m.name}",fontWeight=FontWeight.Bold)
                        Text(m.description,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(m.optional)Text("Facoltativo",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick={
                        saveMeals(prefs,date,meals.map{if(it.id==m.id)it.copy(reminder=m.reminder.copy(enabled=!m.reminder.enabled))else it})
                        refresh++;NutritionWidgetProvider.requestRefresh(context)
                    }){Text(if(m.reminder.enabled)"🔔" else "🔕",fontSize=20.sp)}
                }
            }
        }
        item{
            if(meals.none{it.name.contains("Cena",true)}){
                OutlinedButton(onClick={
                    val dinner=MealEntry("${date}_dinner",date.toString(),"🍲 Cena","20:00","Cena da definire",optional=true)
                    saveMeals(prefs,date,meals+dinner);prefs.edit().putBoolean("dinner_enabled_$date",true).apply();refresh++;NutritionWidgetProvider.requestRefresh(context)
                },modifier=Modifier.fillMaxWidth()){Text("+ Aggiungi cena facoltativa")}
            }
        }
    }
}

@Composable
fun CalendarScreen(prefs:SharedPreferences,context:Context){
    val today=LocalDate.now()
    var mode by rememberSaveable{mutableStateOf(prefs.getString("calendar_mode","Mese")?:"Mese")}
    var anchor by rememberSaveable{mutableStateOf(today.toString())}
    var selected by rememberSaveable{mutableStateOf(today.toString())}
    var refresh by remember{mutableIntStateOf(0)}
    var add by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<CalendarEvent?>(null)}
    val anchorDate=LocalDate.parse(anchor)
    val events=remember(refresh){loadEvents(prefs)}

    if(add || editing!=null){
        CalendarDialog(
            date=editing?.let{LocalDate.parse(it.date)}?:LocalDate.parse(selected),
            existing=editing,
            onDismiss={add=false;editing=null}
        ){e->
            val updated=if(editing==null) events+e else events.map{if(it.id==editing!!.id)e.copy(id=editing!!.id)else it}
            saveEvents(prefs,updated);add=false;editing=null;refresh++;NutritionWidgetProvider.requestRefresh(context)
        }
    }

    fun move(delta:Int){
        val next=when(mode){
            "Settimana"->anchorDate.plusWeeks(delta.toLong())
            "Anno"->anchorDate.plusYears(delta.toLong())
            else->anchorDate.plusMonths(delta.toLong())
        }
        anchor=next.toString()
        selected=when(mode){
            "Settimana"->next.with(DayOfWeek.MONDAY).toString()
            "Anno"->next.withDayOfYear(1).toString()
            else->next.withDayOfMonth(1).toString()
        }
    }

    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            ScreenTitle("Calendario","Scegli settimana, mese o anno",trailing={Button(onClick={add=true}){Text("+")}})
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                listOf("Settimana","Mese","Anno").forEach{m->
                    FilterChip(
                        selected=mode==m,
                        onClick={mode=m;prefs.edit().putString("calendar_mode",m).apply()},
                        label={Text(m,fontSize=12.sp)},
                        modifier=Modifier.weight(1f)
                    )
                }
            }
        }
        item{
            AppCard{
                val title=when(mode){
                    "Settimana"->{
                        val ws=anchorDate.with(DayOfWeek.MONDAY)
                        "${ws.format(DateTimeFormatter.ofPattern("dd MMM"))} – ${ws.plusDays(6).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                    }
                    "Anno"->"${anchorDate.year}"
                    else->"${anchorDate.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${anchorDate.year}"
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    OutlinedButton(onClick={move(-1)}){Text("‹")}
                    Text(title,fontWeight=FontWeight.ExtraBold,fontSize=18.sp,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(onClick={move(1)}){Text("›")}
                }
                Button(onClick={anchor=today.toString();selected=today.toString()},modifier=Modifier.fillMaxWidth()){Text("Oggi")}
            }
        }

        if(mode=="Settimana"){
            item{
                val ws=anchorDate.with(DayOfWeek.MONDAY)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    (0..6).forEach{i->
                        val d=ws.plusDays(i.toLong())
                        val isToday=d==today
                        val isSel=d.toString()==selected
                        Surface(
                            modifier=Modifier.weight(1f).clickable{selected=d.toString()},
                            shape=RoundedCornerShape(12.dp),
                            color=if(isSel)MaterialTheme.colorScheme.primary.copy(alpha=.28f) else MaterialTheme.colorScheme.surface,
                            border=BorderStroke(if(isToday)3.dp else 1.dp,if(isToday)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        ){Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.ITALIAN),fontSize=10.sp,maxLines=1)
                            Text("${d.dayOfMonth}",fontWeight=FontWeight.Bold)
                        }}
                    }
                }
            }
        } else if(mode=="Mese"){
            item{
                val first=anchorDate.withDayOfMonth(1)
                val days=first.lengthOfMonth()
                val leading=first.dayOfWeek.value-1
                AppCard{
                    Row(Modifier.fillMaxWidth()){
                        listOf("L","M","M","G","V","S","D").forEach{Text(it,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=FontWeight.Bold,fontSize=12.sp)}
                    }
                    var day=1-leading
                    repeat(6){
                        Row(Modifier.fillMaxWidth()){
                            repeat(7){
                                if(day<1 || day>days){
                                    Box(Modifier.weight(1f).height(44.dp))
                                }else{
                                    val d=first.withDayOfMonth(day)
                                    val isToday=d==today
                                    val isSel=d.toString()==selected
                                    Surface(
                                        modifier=Modifier.weight(1f).height(44.dp).padding(2.dp).clickable{selected=d.toString()},
                                        shape=RoundedCornerShape(10.dp),
                                        color=if(isSel)MaterialTheme.colorScheme.primary.copy(alpha=.28f) else Color.Transparent,
                                        border=if(isToday)BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null
                                    ){Box(contentAlignment=Alignment.Center){Text("$day",fontWeight=if(isToday||isSel)FontWeight.ExtraBold else FontWeight.Normal)}}
                                }
                                day++
                            }
                        }
                    }
                }
            }
        } else {
            item{
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    for(row in 0..3){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            for(col in 0..2){
                                val month=row*3+col+1
                                val d=LocalDate.of(anchorDate.year,month,1)
                                MiniMonth(d,today,events,Modifier.weight(1f)){
                                    anchor=d.toString();selected=d.toString();mode="Mese";prefs.edit().putString("calendar_mode","Mese").apply()
                                }
                            }
                        }
                    }
                }
            }
        }

        item{
            val d=LocalDate.parse(selected)
            Text("${d.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)} ${d.year}",fontWeight=FontWeight.ExtraBold,fontSize=18.sp)
            if(d==today)Text("● Oggi",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        }
        items(events.filter{it.date==selected}.sortedBy{it.time}){e->
            AppCard{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        val ec=categoryFor(e.category)
                        Text("${ec.icon} ${e.time} · ${e.title}",fontWeight=FontWeight.Bold)
                        Text("${e.category} · ${e.recurrence} · ${e.durationMinutes} min",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(e.notes.isNotBlank())Text(e.notes)
                        if(e.reminder)Text("Promemoria ${e.reminderMinutes} min prima",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick={
                        saveEvents(prefs,events.map{if(it.id==e.id)it.copy(reminder=!e.reminder)else it});refresh++;NutritionWidgetProvider.requestRefresh(context)
                    }){Text(if(e.reminder)"🔔" else "🔕",fontSize=20.sp)}
                }
                Row{
                    TextButton(onClick={editing=e}){Text("Modifica")}
                    TextButton(onClick={saveEvents(prefs,events.filterNot{it.id==e.id});refresh++;NutritionWidgetProvider.requestRefresh(context)}){Text("Elimina")}
                }
            }
        }
        if(events.none{it.date==selected}) item{Text("Nessun evento per questo giorno.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
fun SecondaryScreen(screen:String,plan:List<PlanDay>,prefs:SharedPreferences,context:Context,onBack:()->Unit,theme:String,onTheme:(String)->Unit,accent:String,onAccent:(String)->Unit){
    when(screen){
        "menu"->MenuScreen(onBack){ }
        "goals"->GoalsScreen(prefs,onBack)
        "trophies"->TrophiesScreen(prefs,onBack)
        "report"->ReportScreen(prefs,onBack)
        "sleep"->SleepScreen(prefs,onBack)
        "weight"->WeightScreen(prefs,onBack)
        "water"->WaterScreen(prefs,context,onBack)
        "personal"->PersonalScreen(prefs,context,onBack)
        "professionals"->ProfessionalsScreen(prefs,onBack)
        "shopping"->ShoppingScreen(plan,prefs,onBack)
        "widget"->WidgetScreen(onBack)
        "settings"->SettingsScreen(prefs,theme,onTheme,accent,onAccent,onBack)
        "plan"->PlanScreen(plan,prefs,context)
        else->MenuScreen(onBack){}
    }
}

@Composable
fun MenuScreen(onBack:()->Unit,dummy:()->Unit){
    val context=LocalContext.current
    val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    // Menu navigation is handled through persisted request consumed by Home; for this replacement
    // we show dedicated buttons as a compact launcher within this screen.
    var target by remember{mutableStateOf("")}
    if(target.isNotBlank()){
        SecondaryScreen(target,loadPlan(context),prefs,context,onBack={target=""},theme=prefs.getString("theme","system")?:"system",onTheme={prefs.edit().putString("theme",it).apply()},accent=prefs.getString("accent","aqua")?:"aqua",onAccent={prefs.edit().putString("accent",it).apply()})
        return
    }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{BackHeader("Menu",onBack)}
        items(listOf(
            "🎯 Obiettivi" to "goals","🏆 Trofei" to "trophies","📊 Resoconto" to "report","😴 Sonno" to "sleep",
            "⚖️ Peso e misure" to "weight","💧 Idratazione" to "water","🌟 Vita personale" to "personal",
            "👥 Professionisti" to "professionals","🛒 Lista della spesa" to "shopping","📱 Widget Android" to "widget","⚙️ Impostazioni" to "settings"
        )){p->AppCard{Row(Modifier.fillMaxWidth().clickable{target=p.second}.padding(vertical=8.dp)){Text(p.first,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("›")}}}
    }
}

@Composable
fun WaterScreen(prefs:SharedPreferences,context:Context,onBack:()->Unit){
    val today=LocalDate.now();var water by remember{mutableIntStateOf(prefs.getInt("water_$today",0))};var goal by remember{mutableStateOf(prefs.getInt("water_goal",2000).toString())}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{BackHeader("Idratazione",onBack)}
        item{AppCard{Text("$water ml",fontSize=32.sp,fontWeight=FontWeight.ExtraBold);val g=goal.toIntOrNull()?.coerceAtLeast(250)?:2000;LinearProgressIndicator(progress={(water.toFloat()/g).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=goal,onValueChange={goal=it.filter(Char::isDigit);it.toIntOrNull()?.let{v->prefs.edit().putInt("water_goal",v).apply()}},label={Text("Obiettivo giornaliero ml")});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={water+=250;prefs.edit().putInt("water_$today",water).apply();NutritionWidgetProvider.requestRefresh(context)}){Text("+250")};Button(onClick={water+=500;prefs.edit().putInt("water_$today",water).apply();NutritionWidgetProvider.requestRefresh(context)}){Text("+500")};OutlinedButton(onClick={water=(water-250).coerceAtLeast(0);prefs.edit().putInt("water_$today",water).apply();NutritionWidgetProvider.requestRefresh(context)}){Text("-250")}}}}
    }
}

@Composable
fun ShoppingScreen(plan:List<PlanDay>,prefs:SharedPreferences,onBack:()->Unit){
    var refresh by remember{mutableIntStateOf(0)}
    var add by remember{mutableStateOf(false)}
    var review by remember{mutableStateOf<List<ShoppingItem>?>(null)}
    val list=remember(refresh){loadShopping(prefs)}
    if(add)ShoppingDialog(onDismiss={add=false}){i->saveShopping(prefs,list+i);add=false;refresh++}
    if(review!=null){
        GeneratedShoppingReview(review!!,onCancel={review=null}){items->
            val merged=mergeShopping(list,items);saveShopping(prefs,merged);review=null;refresh++
        }
        return
    }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{BackHeader("Lista della spesa",onBack);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={add=true}){Text("+ Prodotto")};OutlinedButton(onClick={review=generateShoppingFromCurrentWeek(plan,prefs)}){Text("Genera dal piano settimanale")}}}
        item{Text("${list.count{it.bought}} di ${list.size} acquistati",fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={if(list.isEmpty())0f else list.count{it.bought}.toFloat()/list.size},modifier=Modifier.fillMaxWidth())}
        item{Text("Da comprare",fontWeight=FontWeight.ExtraBold)}
        items(list.filter{!it.bought}){i->ShoppingRow(i,list,prefs){refresh++}}
        item{Text("Acquistati",fontWeight=FontWeight.ExtraBold)}
        items(list.filter{it.bought}){i->ShoppingRow(i,list,prefs){refresh++}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={saveShopping(prefs,list.map{it.copy(bought=true)});refresh++}){Text("Segna tutti acquistati")};OutlinedButton(onClick={saveShopping(prefs,list.filterNot{it.bought});refresh++}){Text("Elimina acquistati")}}}
    }
}

@Composable
fun ShoppingRow(i:ShoppingItem,list:List<ShoppingItem>,prefs:SharedPreferences,onChange:()->Unit){
    AppCard{
        Row(verticalAlignment=Alignment.CenterVertically){
            CircleCheck(i.bought){saveShopping(prefs,list.map{if(it.id==i.id)it.copy(bought=!i.bought)else it});onChange()}
            Column(Modifier.weight(1f).padding(start=10.dp)){
                Text(i.name,fontWeight=FontWeight.Bold,textDecoration=if(i.bought)TextDecoration.LineThrough else null)
                Text("${fmt(i.quantity)} ${i.unit}${if(i.category.isNotBlank())" · ${i.category}" else ""}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(i.note.isNotBlank())Text(i.note,fontSize=12.sp)
            }
            TextButton(onClick={saveShopping(prefs,list.filterNot{it.id==i.id});onChange()}){Text("🗑")}
        }
    }
}

@Composable
fun GeneratedShoppingReview(items:List<ShoppingItem>,onCancel:()->Unit,onConfirm:(List<ShoppingItem>)->Unit){
    var selected by remember{mutableStateOf(items.map{it.id}.toSet())}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{BackHeader("Controlla lista generata",onCancel);Text("Togli la spunta agli alimenti che hai già in casa.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(items){i->AppCard{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=i.id in selected,onCheckedChange={selected=if(it)selected+i.id else selected-i.id});Column{Text(i.name,fontWeight=FontWeight.Bold);Text("${fmt(i.quantity)} ${i.unit}")}}}}
        item{Button(onClick={onConfirm(items.filter{it.id in selected})},modifier=Modifier.fillMaxWidth()){Text("Aggiungi alla lista")}}
    }
}

@Composable
fun GoalsScreen(prefs:SharedPreferences,onBack:()->Unit){
    var refresh by remember{mutableIntStateOf(0)};var add by remember{mutableStateOf(false)}
    val goals=remember(refresh){loadGoals(prefs)}
    if(add)GoalDialog(onDismiss={add=false}){g->saveGoals(prefs,goals+g);add=false;refresh++}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{BackHeader("Obiettivi",onBack);Button(onClick={add=true}){Text("+ Nuovo obiettivo")}}
        items(goals){g->
            val p=if(g.automatic)automaticGoalProgress(g,prefs) else g.progress
            AppCard{Text("${g.category} · ${if(g.automatic)"Automatico" else "Manuale"}",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(g.title,fontWeight=FontWeight.ExtraBold);Text("${fmt(p)} / ${fmt(g.target)} ${g.unit}");LinearProgressIndicator(progress={(p/g.target.coerceAtLeast(1.0)).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());if(!g.automatic)Row{Button(onClick={saveGoals(prefs,goals.map{if(it.id==g.id)it.copy(progress=(it.progress+1).coerceAtMost(it.target))else it});refresh++}){Text("+1")};TextButton(onClick={saveGoals(prefs,goals.filterNot{it.id==g.id});refresh++}){Text("Elimina")}}}
        }
    }
}

@Composable
fun TrophiesScreen(prefs:SharedPreferences,onBack:()->Unit){
    val workouts=loadSimple(prefs,"activities").count{it.done}
    val reports=prefs.all.keys.count{it.startsWith("report_")}
    val shopping=loadShopping(prefs).count{it.bought}
    val goals=loadGoals(prefs).count{automaticGoalProgress(it,prefs)>=it.target || it.progress>=it.target}
    val hydrationDays=prefs.all.keys.count{it.startsWith("water_") && (prefs.getInt(it,0)>=prefs.getInt("water_goal",2000))}
    val catalog=buildList{
        fun series(label:String,icon:String,value:Int,targets:List<Int>){targets.forEach{t->add(Triple("$icon $label $t","$value / $t",value>=t))}}
        series("Allenamenti","🏋️",workouts,listOf(1,3,7,15,30,50,100,250,365))
        series("Giorni idratazione","💧",hydrationDays,listOf(1,3,7,15,30,50,100,250,365))
        series("Resoconti","📊",reports,listOf(1,7,30,50,100,250,365))
        series("Prodotti acquistati","🛒",shopping,listOf(1,10,25,50,100,250,500))
        series("Obiettivi completati","🎯",goals,listOf(1,3,7,15,30,50,100))
        add(Triple("🔒 Trofeo segreto","Continua a usare l'app",false))
        add(Triple("🔒 Trofeo segreto raro","Condizione nascosta",false))
    }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{BackHeader("Trofei",onBack);Text("${catalog.count{it.third}} / ${catalog.size} sbloccati",fontWeight=FontWeight.Bold)}
        items(catalog){t->AppCard{Row{Text(if(t.third)"🏆" else "🔒",fontSize=26.sp);Column(Modifier.padding(start=10.dp)){Text(t.first,fontWeight=FontWeight.Bold);Text(t.second,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
    }
}

@Composable
fun ReportScreen(prefs:SharedPreferences,onBack:()->Unit){
    val key="report_${LocalDate.now()}";val old=prefs.getString(key,"")?:"";var note by remember{mutableStateOf(old)};var mood by remember{mutableIntStateOf(prefs.getInt("${key}_mood",3))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{BackHeader("Resoconto giornaliero",onBack)}
        item{AppCard{Text("Umore",fontWeight=FontWeight.Bold);Row{(1..5).forEach{m->FilterChip(selected=mood==m,onClick={mood=m},label={Text("$m")})}};OutlinedTextField(value=note,onValueChange={note=it},label={Text("Note della giornata")},modifier=Modifier.fillMaxWidth(),minLines=4);Button(onClick={prefs.edit().putString(key,note).putInt("${key}_mood",mood).apply()}){Text("Salva resoconto")}}}
    }
}

@Composable
fun SleepScreen(prefs:SharedPreferences,onBack:()->Unit){
    val date=LocalDate.now();var bed by remember{mutableStateOf(prefs.getString("sleep_bed_$date","23:00")?:"23:00")};var wake by remember{mutableStateOf(prefs.getString("sleep_wake_$date","07:00")?:"07:00")};var quality by remember{mutableIntStateOf(prefs.getInt("sleep_quality_$date",3))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{BackHeader("Sonno",onBack)};item{AppCard{OutlinedTextField(bed,{bed=it},label={Text("Ora di sonno")});OutlinedTextField(wake,{wake=it},label={Text("Risveglio")});Text("Qualità");Row{(1..5).forEach{q->FilterChip(selected=quality==q,onClick={quality=q},label={Text("$q")})}};Button(onClick={prefs.edit().putString("sleep_bed_$date",bed).putString("sleep_wake_$date",wake).putInt("sleep_quality_$date",quality).apply()}){Text("Salva")}}}}
}

@Composable
fun WeightScreen(prefs:SharedPreferences,onBack:()->Unit){
    var weight by remember{mutableStateOf("")};var waist by remember{mutableStateOf("")};var abdomen by remember{mutableStateOf("")};var chest by remember{mutableStateOf("")}
    val hist=remember{mutableStateListOf<String>().apply{addAll(prefs.getStringSet("weight_history",emptySet())!!.sortedDescending())}}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{BackHeader("Peso e misure",onBack)};item{AppCard{OutlinedTextField(weight,{weight=it},label={Text("Peso kg")});OutlinedTextField(waist,{waist=it},label={Text("Vita cm")});OutlinedTextField(abdomen,{abdomen=it},label={Text("Addome cm")});OutlinedTextField(chest,{chest=it},label={Text("Torace cm")});Button(onClick={val s="${LocalDate.now()} | $weight kg | vita $waist | addome $abdomen | torace $chest";val set=(prefs.getStringSet("weight_history",emptySet())?:emptySet()).toMutableSet();set+=s;prefs.edit().putStringSet("weight_history",set).putFloat("last_weight",weight.replace(",",".").toFloatOrNull()?:0f).apply();hist.clear();hist.addAll(set.sortedDescending())}){Text("Salva misure")}}};items(hist){Text(it)}}
}

@Composable
fun PersonalScreen(prefs:SharedPreferences,context:Context,onBack:()->Unit){
    var refresh by remember{mutableIntStateOf(0)};var add by remember{mutableStateOf(false)};val list=remember(refresh){loadSimple(prefs,"personal")}
    if(add)SimpleEntryDialog("Nuovo impegno",LocalDate.now().toString(),onDismiss={add=false}){e->saveSimple(prefs,"personal",list+e.copy(type="Personale"));add=false;refresh++;NutritionWidgetProvider.requestRefresh(context)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{BackHeader("Vita personale",onBack);Button(onClick={add=true}){Text("+ Aggiungi")}};items(list.sortedBy{it.date}){e->AppCard{Text(e.title,fontWeight=FontWeight.Bold);Text("${e.date} · ${e.time}");Text(e.notes);TextButton(onClick={saveSimple(prefs,"personal",list.filterNot{it.id==e.id});refresh++}){Text("Elimina")}}}}
}

@Composable
fun ProfessionalsScreen(prefs:SharedPreferences,onBack:()->Unit){
    var refresh by remember{mutableIntStateOf(0)};var add by remember{mutableStateOf(false)}
    val links=remember(refresh){loadProfessionals(prefs)}
    val proposals=remember(refresh){loadProposals(prefs)}
    if(add)ProfessionalDialog(onDismiss={add=false}){p->saveProfessionals(prefs,links+p);add=false;refresh++}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{BackHeader("Professionisti",onBack);Text("Il professionista può usare app oppure portale web. Non può modificare direttamente i tuoi piani.",color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick={add=true}){Text("+ Collega professionista")}}
        items(links){p->AppCard{Text("${if(p.role=="Nutrizionista")"🥗" else "🏋️"} ${p.name}",fontWeight=FontWeight.ExtraBold);Text(p.role);Text("Codice invito: ${p.inviteCode}",fontWeight=FontWeight.Bold);Text("Permessi: ${p.permissions.joinToString().ifBlank{"Nessuno"}}",fontSize=12.sp);Button(onClick={saveProfessionals(prefs,links.map{if(it.id==p.id)it.copy(active=false)else it});refresh++}){Text("Revoca accesso")}}}
        item{Text("📝 Modifiche proposte",fontWeight=FontWeight.ExtraBold)}
        items(proposals){p->AppCard{Text("${p.professional} · ${p.area}",fontWeight=FontWeight.Bold);Text(p.note);Text("${p.date} · ${p.status}",color=MaterialTheme.colorScheme.onSurfaceVariant);Row{Button(onClick={saveProposals(prefs,proposals.map{if(it.id==p.id)it.copy(status="Accettata")else it});refresh++}){Text("Accetta")};TextButton(onClick={saveProposals(prefs,proposals.map{if(it.id==p.id)it.copy(status="Rifiutata")else it});refresh++}){Text("Rifiuta")}}}}
        item{AppCard{Text("🔐 Privacy e accessi",fontWeight=FontWeight.Bold);Text("Accesso minimo necessario, revocabile in ogni momento. Vita personale esclusa per impostazione predefinita.",color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Il collegamento remoto reale richiede il backend sicuro configurato con credenziali del progetto.")}}
    }
}

@Composable
fun WidgetScreen(onBack:()->Unit){
    val context=LocalContext.current
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{BackHeader("Widget Android",onBack)};item{AppCard{Text("Widget grande",fontWeight=FontWeight.ExtraBold);Text("Pensato per occupare quasi tutta una pagina della Home: acqua, pasti, integratori, attività, impegni, spesa e accessi rapidi.");Button(onClick={NutritionWidgetProvider.requestRefresh(context)}){Text("Aggiorna widget")};Text("Per aggiungerlo: pressione lunga sulla Home → Widget → Alessandro Nutrition → trascinalo sullo schermo.",color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
}

@Composable
fun SettingsScreen(prefs:SharedPreferences,theme:String,onTheme:(String)->Unit,accent:String,onAccent:(String)->Unit,onBack:()->Unit){
    var setup by remember{mutableStateOf(false)}
    if(setup)ProfileSetupDialog(prefs,onDismiss={setup=false},forceEdit=true)
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{BackHeader("Impostazioni",onBack)}
        item{AppCard{
            Text("👤 Profilo e preferenze",fontWeight=FontWeight.ExtraBold)
            Text("Domande iniziali, obiettivi, abitudini e note personali.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={setup=true}){Text("Modifica le mie risposte")}
        }}
        item{AppCard{Text("🎨 Aspetto e tema",fontWeight=FontWeight.ExtraBold);listOf("system" to "Automatico","light" to "Chiaro","dark" to "Scuro").forEach{p->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=theme==p.first,onClick={onTheme(p.first)});Text(p.second)}};Text("Colore principale",fontWeight=FontWeight.Bold);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("aqua","blue","green","orange","purple").forEach{c->FilterChip(selected=accent==c,onClick={onAccent(c)},label={Text(c.replaceFirstChar{it.uppercase()})})}}}}
        item{AppCard{Text("🔔 Notifiche",fontWeight=FontWeight.Bold);var sound by remember{mutableStateOf(prefs.getBoolean("sound",true))};var vibration by remember{mutableStateOf(prefs.getBoolean("vibration",true))};Row{Text("Suono",Modifier.weight(1f));Switch(sound,{sound=it;prefs.edit().putBoolean("sound",it).apply()})};Row{Text("Vibrazione",Modifier.weight(1f));Switch(vibration,{vibration=it;prefs.edit().putBoolean("vibration",it).apply()})}}}
    }
}

@Composable
fun SimpleEntryDialog(title:String,defaultDate:String,onDismiss:()->Unit,onSave:(SimpleEntry)->Unit){
    val context=LocalContext.current
    var name by remember{mutableStateOf("")};var date by remember{mutableStateOf(defaultDate)};var time by remember{mutableStateOf("17:00")};var notes by remember{mutableStateOf("")};var reminder by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(name,{name=it},label={Text("Titolo")},modifier=Modifier.fillMaxWidth())
        DatePickerButton(date){date=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=false,onClick={date=LocalDate.now().toString()},label={Text("Oggi")});FilterChip(selected=false,onClick={date=LocalDate.now().plusDays(1).toString()},label={Text("Domani")})}
        TimePickerButton(time){time=it}
        OutlinedTextField(notes,{notes=it},label={Text("Note / altro da aggiungere")},modifier=Modifier.fillMaxWidth(),minLines=2)
        Row{Text("Promemoria",Modifier.weight(1f));Switch(reminder,{reminder=it})}
    }},confirmButton={Button(enabled=name.isNotBlank(),onClick={onSave(SimpleEntry(System.currentTimeMillis(),name,date,time,notes,false,"",reminder))}){Text("Salva")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun SupplementDialog(onDismiss:()->Unit,onSave:(SupplementEntry)->Unit){
    var name by remember{mutableStateOf("")};var dose by remember{mutableStateOf("")};var days by remember{mutableStateOf("Tutti i giorni")};var time by remember{mutableStateOf("07:20")};var notes by remember{mutableStateOf("")};var reminder by remember{mutableStateOf(true)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Nuovo integratore")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(name,{name=it},label={Text("Nome")});OutlinedTextField(dose,{dose=it},label={Text("Dose")});OutlinedTextField(days,{days=it},label={Text("Giorni")});OutlinedTextField(time,{time=it},label={Text("Ora")});OutlinedTextField(notes,{notes=it},label={Text("Note")});Row{Text("Promemoria",Modifier.weight(1f));Switch(reminder,{reminder=it})}}},confirmButton={Button(enabled=name.isNotBlank(),onClick={onSave(SupplementEntry(System.currentTimeMillis(),name,dose,days,time,notes,"",reminder))}){Text("Salva")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun ShoppingDialog(onDismiss:()->Unit,onSave:(ShoppingItem)->Unit){
    var name by remember{mutableStateOf("")};var qty by remember{mutableStateOf("1")};var unit by remember{mutableStateOf("pz")};var cat by remember{mutableStateOf("")};var note by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Aggiungi prodotto")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(name,{name=it},label={Text("Prodotto")});OutlinedTextField(qty,{qty=it},label={Text("Quantità")});OutlinedTextField(unit,{unit=it},label={Text("Unità: g, kg, ml, L, pz")});OutlinedTextField(cat,{cat=it},label={Text("Categoria opzionale")});OutlinedTextField(note,{note=it},label={Text("Nota opzionale")})}},confirmButton={Button(enabled=name.isNotBlank(),onClick={onSave(ShoppingItem(System.currentTimeMillis(),name,qty.replace(",",".").toDoubleOrNull()?:1.0,unit,cat,note))}){Text("Salva")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun GoalDialog(onDismiss:()->Unit,onSave:(GoalEntry)->Unit){
    var title by remember{mutableStateOf("")};var category by remember{mutableStateOf("Idratazione")};var target by remember{mutableStateOf("7")};var unit by remember{mutableStateOf("giorni")};var auto by remember{mutableStateOf(true)};var metric by remember{mutableStateOf("hydration_days")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Nuovo obiettivo")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(title,{title=it},label={Text("Titolo")});OutlinedTextField(category,{category=it},label={Text("Categoria")});OutlinedTextField(target,{target=it},label={Text("Valore obiettivo")});OutlinedTextField(unit,{unit=it},label={Text("Unità")});Row{Text("Avanzamento automatico",Modifier.weight(1f));Switch(auto,{auto=it})};if(auto)OutlinedTextField(metric,{metric=it},label={Text("Metrica: hydration_days, workouts, shopping, reports, weight_loss")})}},confirmButton={Button(enabled=title.isNotBlank(),onClick={onSave(GoalEntry(System.currentTimeMillis(),title,category,target.replace(",",".").toDoubleOrNull()?:1.0,0.0,unit,auto,metric))}){Text("Salva")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun CalendarDialog(date:LocalDate,existing:CalendarEvent?=null,onDismiss:()->Unit,onSave:(CalendarEvent)->Unit){
    var title by remember(existing?.id){mutableStateOf(existing?.title?:"")}
    var d by remember(existing?.id){mutableStateOf(existing?.date?:date.toString())}
    var time by remember(existing?.id){mutableStateOf(existing?.time?:"09:00")}
    var cat by remember(existing?.id){mutableStateOf(existing?.category?:"Vita personale")}
    var notes by remember(existing?.id){mutableStateOf(existing?.notes?:"")}
    var reminder by remember(existing?.id){mutableStateOf(existing?.reminder?:false)}
    var recurrence by remember(existing?.id){mutableStateOf(existing?.recurrence?:"Nessuna")}
    var duration by remember(existing?.id){mutableStateOf((existing?.durationMinutes?:60).toString())}
    var lead by remember(existing?.id){mutableStateOf((existing?.reminderMinutes?:10).toString())}
    var customDays by remember{mutableStateOf(setOf<Int>())}
    var every by remember{mutableStateOf("1")}
    var until by remember{mutableStateOf("")}
    var customRecurrence by remember{mutableStateOf(recurrence.startsWith("Personalizzata"))}

    fun recurrenceLabel():String{
        if(!customRecurrence)return recurrence
        val dayNames=listOf("Lun","Mar","Mer","Gio","Ven","Sab","Dom")
        val ds=customDays.sorted().joinToString(", "){dayNames[it-1]}
        return "Personalizzata: ogni ${every.toIntOrNull()?.coerceAtLeast(1)?:1} settimana/e${if(ds.isNotBlank())" · $ds" else ""}${if(until.isNotBlank())" · fino al $until" else ""}"
    }

    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if(existing==null)"Nuovo evento" else "Modifica evento")},
        text={
            LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){
                item{OutlinedTextField(title,{title=it},label={Text("Titolo")},modifier=Modifier.fillMaxWidth())}
                item{Text("Categoria",fontWeight=FontWeight.Bold)}
                item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){defaultEventCategories.forEach{c->FilterChip(selected=cat==c.name,onClick={cat=c.name},label={Text("${c.icon} ${c.name}")})}}}
                item{DatePickerButton(d){d=it}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected=false,onClick={d=LocalDate.now().toString()},label={Text("Oggi")});FilterChip(selected=false,onClick={d=LocalDate.now().plusDays(1).toString()},label={Text("Domani")})}}
                item{TimePickerButton(time){time=it}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Adesso" to LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm")),"09:00" to "09:00","17:00" to "17:00","20:00" to "20:00").forEach{p->FilterChip(selected=time==p.second,onClick={time=p.second},label={Text(p.first)})}}}
                item{OutlinedTextField(duration,{duration=it.filter(Char::isDigit)},label={Text("Durata in minuti")},modifier=Modifier.fillMaxWidth())}
                item{Text("Ricorrenza",fontWeight=FontWeight.Bold)}
                item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Nessuna","Ogni giorno","Ogni settimana","Ogni mese","Ogni anno").forEach{r->FilterChip(selected=!customRecurrence&&recurrence==r,onClick={recurrence=r;customRecurrence=false},label={Text(r)})};FilterChip(selected=customRecurrence,onClick={customRecurrence=true},label={Text("Personalizzata")})}}
                if(customRecurrence){
                    item{Text("Giorni della settimana",fontWeight=FontWeight.SemiBold)}
                    item{Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("L","M","M","G","V","S","D").forEachIndexed{i,n->FilterChip(selected=(i+1) in customDays,onClick={customDays=if((i+1) in customDays)customDays-(i+1) else customDays+(i+1)},label={Text(n)})}}}
                    item{OutlinedTextField(every,{every=it.filter(Char::isDigit)},label={Text("Ripeti ogni N settimane")},modifier=Modifier.fillMaxWidth())}
                    item{if(until.isBlank())OutlinedButton(onClick={until=d}){Text("Imposta data di fine") } else DatePickerButton(until){until=it}}
                }
                item{Text("Riepilogo: ${recurrenceLabel()}",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)}
                item{OutlinedTextField(notes,{notes=it},label={Text("Note / altro da aggiungere")},modifier=Modifier.fillMaxWidth(),minLines=2)}
                item{Row(verticalAlignment=Alignment.CenterVertically){Text("Promemoria",Modifier.weight(1f));Switch(reminder,{reminder=it})}}
                if(reminder){
                    item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(10,30,60,1440).forEach{m->FilterChip(selected=lead.toIntOrNull()==m,onClick={lead=m.toString()},label={Text(if(m==1440)"1 giorno prima" else if(m==60)"1 ora prima" else "$m min prima")})}}}
                    item{OutlinedTextField(lead,{lead=it.filter(Char::isDigit)},label={Text("Minuti prima (personalizzato)")},modifier=Modifier.fillMaxWidth())}
                }
            }
        },
        confirmButton={Button(enabled=title.isNotBlank(),onClick={onSave(CalendarEvent(existing?.id?:System.currentTimeMillis(),title,d,time,cat,notes,reminder,recurrenceLabel(),duration.toIntOrNull()?.coerceAtLeast(0)?:60,lead.toIntOrNull()?.coerceAtLeast(0)?:10))}){Text("Salva")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}}
    )
}

@Composable
fun ProfessionalDialog(onDismiss:()->Unit,onSave:(ProfessionalLink)->Unit){
    var name by remember{mutableStateOf("")};var role by remember{mutableStateOf("Nutrizionista")};var nutrition by remember{mutableStateOf(true)};var weight by remember{mutableStateOf(true)};var hydration by remember{mutableStateOf(true)};var workouts by remember{mutableStateOf(false)};var reports by remember{mutableStateOf(true)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Collega professionista")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(name,{name=it},label={Text("Nome")});Row{FilterChip(selected=role=="Nutrizionista",onClick={role="Nutrizionista"},label={Text("Nutrizionista")});FilterChip(selected=role=="Personal Trainer",onClick={role="Personal Trainer"},label={Text("Personal Trainer")})};Text("Permessi",fontWeight=FontWeight.Bold);listOf("Alimentazione" to nutrition,"Peso e misure" to weight,"Idratazione" to hydration,"Allenamenti" to workouts,"Resoconti" to reports).forEach{p->Text("${if(p.second)"✓" else "○"} ${p.first}")};Text("Vita personale: esclusa",color=MaterialTheme.colorScheme.onSurfaceVariant)}},confirmButton={Button(enabled=name.isNotBlank(),onClick={val perms=mutableSetOf<String>();if(nutrition)perms+="Alimentazione";if(weight)perms+="Peso";if(hydration)perms+="Idratazione";if(workouts)perms+="Allenamenti";if(reports)perms+="Resoconti";onSave(ProfessionalLink(System.currentTimeMillis(),name,role,(100000..999999).random().toString(),true,perms))}){Text("Genera invito")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun DatePickerButton(value:String,onChange:(String)->Unit){
    val context=LocalContext.current
    val parsed=runCatching{LocalDate.parse(value)}.getOrElse{LocalDate.now()}
    OutlinedButton(onClick={DatePickerDialog(context,{_,y,m,day->onChange(LocalDate.of(y,m+1,day).toString())},parsed.year,parsed.monthValue-1,parsed.dayOfMonth).show()},modifier=Modifier.fillMaxWidth()){
        Text("📅 ${parsed.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.ITALIAN)} ${parsed.dayOfMonth} ${parsed.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)} ${parsed.year}")
    }
}

@Composable
fun TimePickerButton(value:String,onChange:(String)->Unit){
    val context=LocalContext.current
    val parsed=runCatching{LocalTime.parse(value)}.getOrElse{LocalTime.of(9,0)}
    OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->onChange("%02d:%02d".format(h,m))},parsed.hour,parsed.minute,true).show()},modifier=Modifier.fillMaxWidth()){Text("🕒 $value")}
}

@Composable
fun MiniMonth(first:LocalDate,today:LocalDate,events:List<CalendarEvent>,modifier:Modifier=Modifier,onClick:()->Unit){
    val leading=first.dayOfWeek.value-1
    val days=first.lengthOfMonth()
    Card(modifier=modifier.clickable{onClick()},shape=RoundedCornerShape(14.dp),border=if(today.year==first.year&&today.monthValue==first.monthValue)BorderStroke(2.dp,MaterialTheme.colorScheme.primary)else null){
        Column(Modifier.padding(7.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Text(first.month.getDisplayName(TextStyle.SHORT,Locale.ITALIAN).replaceFirstChar{it.uppercase()},fontWeight=FontWeight.ExtraBold,fontSize=12.sp)
            var n=1-leading
            repeat(6){
                Row(Modifier.fillMaxWidth()){repeat(7){
                    if(n in 1..days){
                        val d=first.withDayOfMonth(n);val has=events.any{it.date==d.toString()}
                        Text(if(has)"•" else n.toString(),fontSize=7.sp,fontWeight=if(d==today)FontWeight.ExtraBold else FontWeight.Normal,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=if(d==today)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }else Text(" ",fontSize=7.sp,modifier=Modifier.weight(1f))
                    n++
                }}
            }
        }
    }
}

@Composable
fun ProfileSetupDialog(prefs:SharedPreferences,onDismiss:()->Unit,forceEdit:Boolean=false){
    var name by remember{mutableStateOf(prefs.getString("profile_name","Alessandro")?:"Alessandro")}
    var goal by remember{mutableStateOf(prefs.getString("profile_main_goal","")?:"")}
    var workout by remember{mutableStateOf(prefs.getString("profile_workout_days","")?:"")}
    var meals by remember{mutableStateOf(prefs.getString("profile_food_preferences","")?:"")}
    var supplements by remember{mutableStateOf(prefs.getString("profile_supplements","")?:"")}
    var water by remember{mutableStateOf(prefs.getInt("water_goal",2000).toString())}
    var notes by remember{mutableStateOf(prefs.getString("profile_extra_notes","")?:"")}
    AlertDialog(onDismissRequest={if(forceEdit)onDismiss()},title={Text(if(forceEdit)"Profilo e preferenze" else "Configuriamo l'app")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Rispondi alle domande di base. Puoi cambiarle quando vuoi.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{OutlinedTextField(name,{name=it},label={Text("Come vuoi essere chiamato?")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(goal,{goal=it},label={Text("Obiettivo principale")},placeholder={Text("Es. dimagrire, stare meglio, aumentare massa")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(workout,{workout=it},label={Text("Giorni / tipo di allenamento")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(meals,{meals=it},label={Text("Preferenze o regole alimentari")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(supplements,{supplements=it},label={Text("Integratori che usi")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(water,{water=it.filter(Char::isDigit)},label={Text("Obiettivo acqua giornaliero (ml)")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(notes,{notes=it},label={Text("C'è altro che vuoi aggiungere?")},modifier=Modifier.fillMaxWidth(),minLines=3)}
    }},confirmButton={Button(onClick={prefs.edit().putString("profile_name",name.ifBlank{"Alessandro"}).putString("profile_main_goal",goal).putString("profile_workout_days",workout).putString("profile_food_preferences",meals).putString("profile_supplements",supplements).putInt("water_goal",water.toIntOrNull()?.coerceAtLeast(250)?:2000).putString("profile_extra_notes",notes).putBoolean("profile_setup_done",true).apply();onDismiss()}){Text("Salva")}},dismissButton={if(forceEdit)TextButton(onClick=onDismiss){Text("Annulla")}})
}

/* ---------- Persistence ---------- */

private fun defaultMealsFor(date:LocalDate,plan:List<PlanDay>,prefs:SharedPreferences):List<MealEntry>{
    val stored=loadMeals(prefs,date)
    if(stored.isNotEmpty())return stored
    if(date.dayOfWeek==DayOfWeek.SUNDAY && prefs.getBoolean("sunday_free_$date",true))return emptyList()
    val p=plan.firstOrNull{it.date==date.toString()}
    val base=mutableListOf(
        MealEntry("${date}_breakfast",date.toString(),"🍳 Colazione","07:00",p?.breakfast?:"Colazione da definire"),
        MealEntry("${date}_snack_am",date.toString(),"🥤 Spuntino mattutino","10:30",p?.snack?:"Shake proteico"),
        MealEntry("${date}_lunch",date.toString(),"🍽️ Pranzo","14:00",p?.lunch?:"Pranzo da definire"),
        MealEntry("${date}_snack_pm",date.toString(),"🍎 Spuntino pomeridiano","16:30","Spuntino da definire")
    )
    if(prefs.getBoolean("dinner_enabled_$date",false))base+=MealEntry("${date}_dinner",date.toString(),"🍲 Cena","20:00",prefs.getString("dinner_desc_$date","Cena da definire")?:"Cena da definire",true)
    return base
}
private fun loadMealsFor(date:LocalDate,plan:List<PlanDay>,prefs:SharedPreferences)=defaultMealsFor(date,plan,prefs)
private fun saveMeals(prefs:SharedPreferences,date:LocalDate,list:List<MealEntry>){val a=JSONArray();list.forEach{m->a.put(JSONObject().put("id",m.id).put("date",m.date).put("name",m.name).put("time",m.time).put("description",m.description).put("optional",m.optional).put("done",m.done).put("reminder",m.reminder.enabled))};prefs.edit().putString("meals_$date",a.toString()).apply()}
private fun loadMeals(prefs:SharedPreferences,date:LocalDate):List<MealEntry>{return try{val a=JSONArray(prefs.getString("meals_$date","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(MealEntry(o.getString("id"),o.optString("date",date.toString()),o.getString("name"),o.optString("time"),o.optString("description"),o.optBoolean("optional"),o.optBoolean("done"),ReminderSettings(o.optBoolean("reminder",true))))}}}catch(_:Exception){emptyList()}}

private fun saveSimple(prefs:SharedPreferences,key:String,list:List<SimpleEntry>){val a=JSONArray();list.forEach{e->a.put(JSONObject().put("id",e.id).put("title",e.title).put("date",e.date).put("time",e.time).put("notes",e.notes).put("done",e.done).put("type",e.type).put("reminder",e.reminder))};prefs.edit().putString(key,a.toString()).apply()}
private fun loadSimple(prefs:SharedPreferences,key:String):List<SimpleEntry>{return try{val a=JSONArray(prefs.getString(key,"[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(SimpleEntry(o.getLong("id"),o.getString("title"),o.getString("date"),o.optString("time"),o.optString("notes"),o.optBoolean("done"),o.optString("type"),o.optBoolean("reminder")))}}}catch(_:Exception){emptyList()}}

private fun saveSupplements(prefs:SharedPreferences,list:List<SupplementEntry>){val a=JSONArray();list.forEach{s->a.put(JSONObject().put("id",s.id).put("name",s.name).put("dose",s.dose).put("days",s.days).put("time",s.time).put("notes",s.notes).put("doneDate",s.doneDate).put("reminder",s.reminder))};prefs.edit().putString("supplements",a.toString()).apply()}
private fun loadSupplements(prefs:SharedPreferences):List<SupplementEntry>{return try{val a=JSONArray(prefs.getString("supplements","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(SupplementEntry(o.getLong("id"),o.getString("name"),o.optString("dose"),o.optString("days"),o.optString("time"),o.optString("notes"),o.optString("doneDate"),o.optBoolean("reminder",true)))}}}catch(_:Exception){emptyList()}}

private fun saveShopping(prefs:SharedPreferences,list:List<ShoppingItem>){val a=JSONArray();list.forEach{i->a.put(JSONObject().put("id",i.id).put("name",i.name).put("quantity",i.quantity).put("unit",i.unit).put("category",i.category).put("note",i.note).put("bought",i.bought))};prefs.edit().putString("shopping_v24",a.toString()).apply()}
private fun loadShopping(prefs:SharedPreferences):List<ShoppingItem>{return try{val a=JSONArray(prefs.getString("shopping_v24","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(ShoppingItem(o.getLong("id"),o.getString("name"),o.optDouble("quantity",1.0),o.optString("unit","pz"),o.optString("category"),o.optString("note"),o.optBoolean("bought")))}}}catch(_:Exception){emptyList()}}

private fun generateShoppingFromCurrentWeek(plan:List<PlanDay>,prefs:SharedPreferences):List<ShoppingItem>{
    val monday=LocalDate.now().with(DayOfWeek.MONDAY)
    val map=linkedMapOf<Pair<String,String>,Double>()
    for(n in 0..6){
        val d=monday.plusDays(n.toLong())
        loadMealsFor(d,plan,prefs).forEach{meal->
            meal.description.split(",", "+", ";").map{it.trim()}.filter{it.isNotBlank()}.forEach{raw->
                val rx=Regex("""^(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l|pz|pezzi|confezioni)?\s*(.*)$""",RegexOption.IGNORE_CASE)
                val m=rx.find(raw)
                val qty=m?.groupValues?.get(1)?.replace(",",".")?.toDoubleOrNull()?:1.0
                val unit=(m?.groupValues?.get(2)?.ifBlank{"pz"}?:"pz").lowercase()
                val name=(m?.groupValues?.get(3)?.ifBlank{raw}?:raw).trim().replaceFirstChar{it.uppercase()}
                val k=name.lowercase() to unit
                map[k]=(map[k]?:0.0)+qty
            }
        }
    }
    return map.entries.mapIndexed{i,e->ShoppingItem(System.currentTimeMillis()+i,e.key.first.replaceFirstChar{it.uppercase()},e.value,e.key.second)}
}
private fun mergeShopping(existing:List<ShoppingItem>,generated:List<ShoppingItem>):List<ShoppingItem>{
    val out=existing.toMutableList()
    generated.forEach{g->
        val idx=out.indexOfFirst{it.name.equals(g.name,true)&&it.unit.equals(g.unit,true)&&!it.bought}
        if(idx>=0)out[idx]=out[idx].copy(quantity=out[idx].quantity+g.quantity) else out+=g
    }
    return out
}

private fun saveGoals(prefs:SharedPreferences,list:List<GoalEntry>){val a=JSONArray();list.forEach{g->a.put(JSONObject().put("id",g.id).put("title",g.title).put("category",g.category).put("target",g.target).put("progress",g.progress).put("unit",g.unit).put("automatic",g.automatic).put("linkedMetric",g.linkedMetric).put("deadline",g.deadline).put("notes",g.notes))};prefs.edit().putString("goals_v24",a.toString()).apply()}
private fun loadGoals(prefs:SharedPreferences):List<GoalEntry>{return try{val a=JSONArray(prefs.getString("goals_v24","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(GoalEntry(o.getLong("id"),o.getString("title"),o.optString("category"),o.optDouble("target",1.0),o.optDouble("progress"),o.optString("unit"),o.optBoolean("automatic"),o.optString("linkedMetric"),o.optString("deadline"),o.optString("notes")))}}}catch(_:Exception){emptyList()}}
private fun automaticGoalProgress(g:GoalEntry,prefs:SharedPreferences):Double=when(g.linkedMetric){
    "hydration_days"->prefs.all.keys.count{it.startsWith("water_")&&prefs.getInt(it,0)>=prefs.getInt("water_goal",2000)}.toDouble()
    "workouts"->loadSimple(prefs,"activities").count{it.done}.toDouble()
    "shopping"->loadShopping(prefs).count{it.bought}.toDouble()
    "reports"->prefs.all.keys.count{it.startsWith("report_")&&!it.endsWith("_mood")}.toDouble()
    "weight_loss"->{val start=prefs.getFloat("start_weight",prefs.getFloat("last_weight",0f));val now=prefs.getFloat("last_weight",start);(start-now).coerceAtLeast(0f).toDouble()}
    else->g.progress
}

private fun saveEvents(prefs:SharedPreferences,list:List<CalendarEvent>){val a=JSONArray();list.forEach{e->a.put(JSONObject().put("id",e.id).put("title",e.title).put("date",e.date).put("time",e.time).put("category",e.category).put("notes",e.notes).put("reminder",e.reminder).put("recurrence",e.recurrence).put("durationMinutes",e.durationMinutes).put("reminderMinutes",e.reminderMinutes))};prefs.edit().putString("events_v24",a.toString()).apply()}
private fun loadEvents(prefs:SharedPreferences):List<CalendarEvent>{return try{val a=JSONArray(prefs.getString("events_v24","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(CalendarEvent(o.getLong("id"),o.getString("title"),o.getString("date"),o.optString("time"),o.optString("category"),o.optString("notes"),o.optBoolean("reminder"),o.optString("recurrence","Nessuna"),o.optInt("durationMinutes",60),o.optInt("reminderMinutes",10)))}}}catch(_:Exception){emptyList()}}

private fun saveProfessionals(prefs:SharedPreferences,list:List<ProfessionalLink>){val a=JSONArray();list.forEach{p->a.put(JSONObject().put("id",p.id).put("name",p.name).put("role",p.role).put("inviteCode",p.inviteCode).put("active",p.active).put("permissions",JSONArray(p.permissions.toList())))};prefs.edit().putString("professionals_v24",a.toString()).apply()}
private fun loadProfessionals(prefs:SharedPreferences):List<ProfessionalLink>{return try{val a=JSONArray(prefs.getString("professionals_v24","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);val pa=o.optJSONArray("permissions")?:JSONArray();val set=buildSet{for(j in 0 until pa.length())add(pa.getString(j))};add(ProfessionalLink(o.getLong("id"),o.getString("name"),o.getString("role"),o.optString("inviteCode"),o.optBoolean("active",true),set))}}}catch(_:Exception){emptyList()}}

private fun saveProposals(prefs:SharedPreferences,list:List<Proposal>){val a=JSONArray();list.forEach{p->a.put(JSONObject().put("id",p.id).put("professional",p.professional).put("area",p.area).put("note",p.note).put("date",p.date).put("status",p.status))};prefs.edit().putString("proposals_v24",a.toString()).apply()}
private fun loadProposals(prefs:SharedPreferences):List<Proposal>{return try{val a=JSONArray(prefs.getString("proposals_v24","[]"));buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(Proposal(o.getLong("id"),o.getString("professional"),o.getString("area"),o.getString("note"),o.getString("date"),o.optString("status","Da valutare")))}}}catch(_:Exception){emptyList()}}

private fun fmt(v:Double)=if(v%1.0==0.0)v.toInt().toString() else "%.1f".format(Locale.ITALIAN,v)

/* ---------- Notifications ---------- */

class ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val title=intent.getStringExtra("title")?:"Alessandro Nutrition"
        val body=intent.getStringExtra("body")?:"Hai un promemoria."
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),
            NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
    }
}

fun createNotificationChannel(context:Context){
    if(Build.VERSION.SDK_INT>=26){
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,"Promemoria Alessandro Nutrition",NotificationManager.IMPORTANCE_HIGH))
    }
}
