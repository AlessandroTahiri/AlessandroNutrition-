package com.alessandro.nutrition

import android.Manifest
import android.app.AlarmManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// ---------- MODELLI ----------
data class PlanDay(
    val date: String,
    val dayNumber: Int,
    val activity: String,
    val breakfastTitle: String,
    val breakfast: String,
    val snack: String,
    val lunchTitle: String,
    val lunch: String,
    val mealTag: String,
    val fasting: String,
    val water: String
)

data class NutritionEstimate(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

data class CategoryStyle(val emoji: String, val label: String, val color: Color)

val WakeStyle = CategoryStyle("🌅", "Risveglio", Color(0xFFF59E0B))
val BreakfastStyle = CategoryStyle("🍳", "Colazione", Color(0xFFFB923C))
val SnackStyle = CategoryStyle("🥤", "Spuntino", Color(0xFF38BDF8))
val LunchStyle = CategoryStyle("🍽️", "Pranzo", Color(0xFFEF4444))
val WaterStyle = CategoryStyle("💧", "Idratazione", Color(0xFF06B6D4))
val WalkStyle = CategoryStyle("🚶", "Camminata", Color(0xFF22C55E))
val GymStyle = CategoryStyle("🏋️", "Palestra", Color(0xFF3B82F6))
val HomeStyle = CategoryStyle("🏠", "Allenamento a casa", Color(0xFFA855F7))
val WorkStyle = CategoryStyle("💼", "Lavoro", Color(0xFF64748B))
val EveningStyle = CategoryStyle("🌙", "Sera", Color(0xFF6366F1))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        val plan = loadPlan(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2563EB))) {
                NutritionApp(plan, this)
            }
        }
    }
}

fun loadPlan(context: Context): List<PlanDay> {
    val json = context.assets.open("annual_plan.json").bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                PlanDay(
                    date = o.getString("date"), dayNumber = o.getInt("dayNumber"), activity = o.getString("activity"),
                    breakfastTitle = o.getString("breakfastTitle"), breakfast = o.getString("breakfast"), snack = o.getString("snack"),
                    lunchTitle = o.getString("lunchTitle"), lunch = o.getString("lunch"), mealTag = o.getString("mealTag"),
                    fasting = o.getString("fasting"), water = o.getString("water")
                )
            )
        }
    }
}

// ---------- APP ----------
@Composable
fun NutritionApp(plan: List<PlanDay>, context: Context) {
    val prefs = remember { context.getSharedPreferences("nutrition_v2", Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(prefs.getInt("last_tab", 0).coerceIn(0, 4)) }

    LaunchedEffect(tab) { prefs.edit().putInt("last_tab", tab).apply() }

    NotificationPermissionRequest()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val tabs = listOf("🏠" to "Oggi", "📅" to "Calendario", "📈" to "Progressi", "🏃" to "Allenamenti", "📋" to "Piano")
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(label, maxLines = 1, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(plan, prefs, context)
                1 -> CalendarScreen(plan, prefs, context)
                2 -> ProgressScreen(plan, prefs)
                3 -> TrainingScreen(prefs, context)
                else -> PlanScreen(prefs)
            }
        }
    }
}

@Composable
fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

// ---------- OGGI ----------
@Composable
fun TodayScreen(plan: List<PlanDay>, prefs: SharedPreferences, context: Context) {
    val today = LocalDate.now()
    val todayIndex = plan.indexOfFirst { it.date == today.toString() }.let { if (it >= 0) it else 0 }
    var index by rememberSaveable { mutableIntStateOf(todayIndex.coerceIn(0, plan.lastIndex)) }
    val day = plan[index]
    val date = LocalDate.parse(day.date)
    val key = day.date

    val breakfast = remember(day.date) { mutableStateOf(resolveMealText(prefs, day.date, "breakfast", day.breakfast)) }
    val snack = remember(day.date) { mutableStateOf(resolveMealText(prefs, day.date, "snack", day.snack)) }
    val lunch = remember(day.date) { mutableStateOf(resolveMealText(prefs, day.date, "lunch", day.lunch)) }

    var breakfastDone by remember(key) { mutableStateOf(prefs.getBoolean("${key}_done_breakfast", false)) }
    var snackDone by remember(key) { mutableStateOf(prefs.getBoolean("${key}_done_snack", false)) }
    var lunchDone by remember(key) { mutableStateOf(prefs.getBoolean("${key}_done_lunch", false)) }
    var activityDone by remember(key) { mutableStateOf(prefs.getBoolean("${key}_done_activity", false)) }
    var waterMl by remember(key) { mutableIntStateOf(prefs.getInt("${key}_water_ml", 0)) }
    var showMealEditor by remember { mutableStateOf<String?>(null) }
    var showReminderFor by remember { mutableStateOf<Pair<String, LocalTime>?>(null) }
    var showActivityEditor by remember { mutableStateOf(false) }
    var editTimeFor by remember { mutableStateOf<Pair<String,String>?>(null) }

    val breakfastTime = prefs.getString("${key}_time_breakfast", "07:00") ?: "07:00"
    val snackTime = prefs.getString("${key}_time_snack", "10:30") ?: "10:30"
    val lunchTime = prefs.getString("${key}_time_lunch", "14:00") ?: "14:00"
    val wakeTime = prefs.getString("${key}_time_wakeup", "06:30") ?: "06:30"
    val eveningTime = prefs.getString("${key}_time_evening", "21:00") ?: "21:00"

    val breakfastNutrition = estimateNutrition(breakfast.value)
    val snackNutrition = estimateNutrition(snack.value)
    val lunchNutrition = estimateNutrition(lunch.value)
    val totalNutrition = NutritionEstimate(
        breakfastNutrition.kcal + snackNutrition.kcal + lunchNutrition.kcal,
        breakfastNutrition.protein + snackNutrition.protein + lunchNutrition.protein,
        breakfastNutrition.carbs + snackNutrition.carbs + lunchNutrition.carbs,
        breakfastNutrition.fat + snackNutrition.fat + lunchNutrition.fat
    )
    val target = prefs.getInt("calorie_target", 1900)
    val activityName = prefs.getString("${key}_activity_name", day.activity) ?: day.activity
    val activityStyle = activityStyleFor(activityName)
    val activityTime = prefs.getString("${key}_activity_time", "18:00") ?: "18:00"
    val activityDuration = prefs.getInt("${key}_activity_duration", if (activityName.contains("Palestra", true)) 60 else 45)

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column { Text("Oggi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(formatDate(day.date)) }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${totalNutrition.kcal} / $target kcal", fontWeight = FontWeight.Bold)
                    Text("P ${totalNutrition.protein}g · C ${totalNutrition.carbs}g · G ${totalNutrition.fat}g", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("← Ieri") }
            OutlinedButton(onClick = { index = todayIndex }, enabled = index != todayIndex) { Text("Oggi") }
            OutlinedButton(onClick = { if (index < plan.lastIndex) index++ }, enabled = index < plan.lastIndex) { Text("Domani →") }
        } }

        item { TimedActivityCard(WakeStyle, "Risveglio", wakeTime, "Acqua · vitamine · magnesio", onTime = { editTimeFor = "wakeup" to wakeTime }, onEdit = {}, onReminder = { showReminderFor = "Risveglio" to parseTime(wakeTime, LocalTime.of(6,30)) }) }
        item { MealEditableCard(BreakfastStyle, "Colazione", breakfastTime, breakfast.value, breakfastNutrition, breakfastDone,
            onChecked = { breakfastDone = it; prefs.edit().putBoolean("${key}_done_breakfast", it).apply() }, onEdit = { showMealEditor = "breakfast" }, onReminder = { showReminderFor = "Colazione" to parseTime(breakfastTime, LocalTime.of(7,0)) }, onTime = { editTimeFor = "breakfast" to breakfastTime }) }
        item { MealEditableCard(SnackStyle, "Spuntino", snackTime, snack.value, snackNutrition, snackDone,
            onChecked = { snackDone = it; prefs.edit().putBoolean("${key}_done_snack", it).apply() }, onEdit = { showMealEditor = "snack" }, onReminder = { showReminderFor = "Spuntino" to parseTime(snackTime, LocalTime.of(10,30)) }, onTime = { editTimeFor = "snack" to snackTime }) }
        item { MealEditableCard(LunchStyle, "Pranzo", lunchTime, lunch.value, lunchNutrition, lunchDone,
            onChecked = { lunchDone = it; prefs.edit().putBoolean("${key}_done_lunch", it).apply() }, onEdit = { showMealEditor = "lunch" }, onReminder = { showReminderFor = "Pranzo" to parseTime(lunchTime, LocalTime.of(14,0)) }, onTime = { editTimeFor = "lunch" to lunchTime }) }
        item { ColoredCard(Color(0xFF64748B)) { Text("⏱️ Digiuno", fontWeight = FontWeight.Bold); Text(day.fasting); OutlinedButton(onClick = { showReminderFor = "Inizio digiuno" to parseTime(lunchTime, LocalTime.of(14,0)).plusHours(1) }) { Text("🔔 Promemoria") } } }
        item {
            ColoredCard(activityStyle.color) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("${activityStyle.emoji} Attività", fontWeight = FontWeight.Bold); Text("$activityName · $activityTime · $activityDuration min") }
                    AssistChip(onClick = {}, label = { Text(activityStyle.label) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showActivityEditor = true }) { Text("✏️ Modifica") }
                    OutlinedButton(onClick = { showReminderFor = activityName to parseTime(activityTime, LocalTime.of(18,0)) }) { Text("🔔 Promemoria") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = activityDone, onCheckedChange = { activityDone = it; prefs.edit().putBoolean("${key}_done_activity", it).apply() }); Text(if (activityDone) "Completata" else "Segna come completata") }
            }
        }
        item { ColoredCard(WaterStyle.color) {
            Text("💧 Idratazione", fontWeight = FontWeight.Bold); val targetMl = prefs.getInt("water_target_ml", 2500); Text("${"%.2f".format(waterMl/1000.0)} / ${"%.2f".format(targetMl/1000.0)} L")
            LinearProgressIndicator(progress = { (waterMl.toFloat()/targetMl.coerceAtLeast(1)).coerceIn(0f,1f) }, modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={waterMl+=250;prefs.edit().putInt("${key}_water_ml",waterMl).apply()}){Text("+ 250 ml")}; OutlinedButton(onClick={waterMl=(waterMl-250).coerceAtLeast(0);prefs.edit().putInt("${key}_water_ml",waterMl).apply()}){Text("− 250")}; OutlinedButton(onClick={showReminderFor="Bere acqua" to LocalTime.of(9,0)}){Text("🔔")} }
        } }
        item { TimedActivityCard(EveningStyle, "Sera", eveningTime, "Riepilogo giornata · note · preparazione per domani", onTime={editTimeFor="evening" to eveningTime}, onEdit={}, onReminder={showReminderFor="Riepilogo serale" to parseTime(eveningTime,LocalTime.of(21,0))}) }
    }

    showMealEditor?.let { meal ->
        val current = when(meal){"breakfast"->breakfast.value;"snack"->snack.value;else->lunch.value}
        MealEditDialog(title=when(meal){"breakfast"->"Colazione";"snack"->"Spuntino";else->"Pranzo"}, initial=current, date=date, onDismiss={showMealEditor=null},
            onSave={text,mode,startDate->saveMealOverride(prefs,meal,date,text,mode,startDate);when(meal){"breakfast"->breakfast.value=text;"snack"->snack.value=text;else->lunch.value=text};showMealEditor=null},
            onRestore={clearMealOverrideForDate(prefs,meal,date);val original=when(meal){"breakfast"->day.breakfast;"snack"->day.snack;else->day.lunch};when(meal){"breakfast"->breakfast.value=original;"snack"->snack.value=original;else->lunch.value=original};showMealEditor=null})
    }
    showReminderFor?.let { (label,time)-> ReminderDialog(label,date,time,context,onDismiss={showReminderFor=null}) }
    if(showActivityEditor) ActivityEditDialog(activityName,activityTime,activityDuration,onDismiss={showActivityEditor=false}){name,time,duration->prefs.edit().putString("${key}_activity_name",name).putString("${key}_activity_time",time).putInt("${key}_activity_duration",duration).apply();showActivityEditor=false}
    editTimeFor?.let { (type,initial) -> TimeEditDialog(type, initial, date, onDismiss={editTimeFor=null}) { value,mode,startDate -> saveTimeOverride(prefs,type,date,value,mode,startDate); editTimeFor=null } }
}

@Composable
fun ColoredCard(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().border(0.dp, Color.Transparent, RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(5.dp).fillMaxHeight().heightIn(min = 90.dp).background(color))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
        }
    }
}

@Composable
fun ActivityCard(style: CategoryStyle, title: String, body: String, onEdit: () -> Unit, onReminder: () -> Unit) {
    ColoredCard(style.color) { Text("${style.emoji} $title", fontWeight = FontWeight.Bold); Text(body); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onEdit){Text("✏️ Modifica")};OutlinedButton(onClick=onReminder){Text("🔔 Promemoria")}} }
}

@Composable
fun TimedActivityCard(style: CategoryStyle, title: String, time: String, body: String, onTime: () -> Unit, onEdit: () -> Unit, onReminder: () -> Unit) {
    ColoredCard(style.color) {
        Row(verticalAlignment=Alignment.CenterVertically){ Text("${style.emoji} $title · ",fontWeight=FontWeight.Bold); TextButton(onClick=onTime,contentPadding=PaddingValues(0.dp)){Text(time,fontWeight=FontWeight.Bold)} }
        Text(body)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onEdit){Text("✏️ Modifica")};OutlinedButton(onClick=onReminder){Text("🔔 Promemoria")}}
    }
}

@Composable
fun MealEditableCard(style: CategoryStyle, title: String, time: String, body: String, nutrition: NutritionEstimate, checked: Boolean, onChecked: (Boolean) -> Unit, onEdit: () -> Unit, onReminder: () -> Unit, onTime: () -> Unit) {
    ColoredCard(style.color) {
        Row(verticalAlignment=Alignment.CenterVertically){ Text("${style.emoji} $title · ",fontWeight=FontWeight.Bold); TextButton(onClick=onTime,contentPadding=PaddingValues(0.dp)){Text(time,fontWeight=FontWeight.Bold)} }
        Text(body); Text("≈ ${nutrition.kcal} kcal · P ${nutrition.protein}g · C ${nutrition.carbs}g · G ${nutrition.fat}g", style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onEdit){Text("✏️ Modifica")};OutlinedButton(onClick=onReminder){Text("🔔 Promemoria")}}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=checked,onCheckedChange=onChecked);Text(if(checked)"Fatto" else "Segna come fatto")}
    }
}

@Composable
fun TimeEditDialog(type:String, initial:String, date:LocalDate, onDismiss:()->Unit, onSave:(String,String,LocalDate?)->Unit){
    var time by remember{mutableStateOf(initial)}; var mode by remember{mutableStateOf("today")}; var start by remember{mutableStateOf(date.toString())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("🕒 Modifica orario")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(time,{time=it},label={Text("Orario HH:mm")},singleLine=true)
        Text("Applica a:",fontWeight=FontWeight.Bold)
        listOf("today" to "📅 Solo oggi","future" to "🔁 Da oggi in poi","date" to "🗓️ Dal giorno...").forEach{(v,l)->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=mode==v,onClick={mode=v});Text(l)}}
        if(mode=="date")OutlinedTextField(start,{start=it},label={Text("Data YYYY-MM-DD")})
    }},confirmButton={Button(onClick={if(runCatching{LocalTime.parse(time)}.isSuccess)onSave(time,mode,if(mode=="date")runCatching{LocalDate.parse(start)}.getOrNull()else null)}){Text("Salva")}},dismissButton={OutlinedButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun MealEditDialog(title: String, initial: String, date: LocalDate, onDismiss: () -> Unit, onSave: (String, String, LocalDate?) -> Unit, onRestore: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var mode by remember { mutableStateOf("today") }
    var startText by remember { mutableStateOf(date.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Modifica $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scrivi liberamente gli alimenti. Per il calcolo usa, quando possibile, nome + grammi (es. pollo 180 g).")
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Text("Applica modifica a:", fontWeight = FontWeight.Bold)
                listOf("today" to "📅 Solo oggi", "future" to "🔁 Da oggi in poi", "date" to "🗓️ Dal giorno...").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = mode == value, onClick = { mode = value }); Text(label) }
                }
                if (mode == "date") OutlinedTextField(value = startText, onValueChange = { startText = it }, label = { Text("Data YYYY-MM-DD") })
                TextButton(onClick = onRestore) { Text("↩️ Ripristina piano originale") }
            }
        },
        confirmButton = { Button(onClick = { onSave(text, mode, if (mode == "date") runCatching { LocalDate.parse(startText) }.getOrNull() else null) }) { Text("Salva") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
fun ActivityEditDialog(initialName: String, initialTime: String, initialDuration: Int, onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    val options = listOf("🚶 Camminata", "🏋️ Palestra", "🏠 Allenamento a casa", "😴 Riposo", "✏️ Personalizzata")
    var selected by remember { mutableStateOf(options.firstOrNull { initialName.contains(it.substringAfter(' '), true) } ?: "✏️ Personalizzata") }
    var custom by remember { mutableStateOf(if (selected == "✏️ Personalizzata") initialName else "") }
    var time by remember { mutableStateOf(initialTime) }
    var duration by remember { mutableStateOf(initialDuration.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifica attività") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { op -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == op, { selected = op }); Text(op) } }
            if (selected == "✏️ Personalizzata") OutlinedTextField(custom, { custom = it }, label = { Text("Nome attività") })
            OutlinedTextField(time, { time = it }, label = { Text("Ora (HH:mm)") })
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Durata minuti") })
        }
    }, confirmButton = {
        Button(onClick = { val name = if (selected == "✏️ Personalizzata") custom.ifBlank { "Attività personalizzata" } else selected.substringAfter(' '); onSave(name, time.ifBlank { "18:00" }, duration.toIntOrNull()?.coerceIn(1,600) ?: 45) }) { Text("Salva") }
    }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Annulla") } })
}

// ---------- CALENDARIO ----------
@Composable
fun CalendarScreen(plan: List<PlanDay>, prefs: SharedPreferences, context: Context) {
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var view by rememberSaveable { mutableStateOf("Mese") }
    var cursor by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var addEvent by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<String?>(null) }
    var reminderEvent by remember { mutableStateOf<Pair<String,LocalTime>?>(null) }
    val ym=YearMonth.parse(cursor); val selected=LocalDate.parse(selectedDate)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Calendario",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("Giorno","Settimana","Mese","Anno").forEachIndexed{i,label->SegmentedButton(selected=view==label,onClick={view=label},shape=SegmentedButtonDefaults.itemShape(i,4)){Text(label,fontSize=11.sp)}}}}
        when(view){
            "Settimana"->item{WeekCalendarView(selected,prefs){selectedDate=it.toString();view="Giorno"}}
            "Anno"->item{YearCalendarView(selected.year){m->cursor=YearMonth.of(selected.year,m).toString();view="Mese"}}
            "Mese"->item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={cursor=ym.minusMonths(1).toString()}){Text("‹")};Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ITALIAN)).replaceFirstChar{it.uppercase()},fontWeight=FontWeight.Bold);OutlinedButton(onClick={cursor=ym.plusMonths(1).toString()}){Text("›")}};MonthGrid(ym,selected,prefs){selectedDate=it.toString()}}}
            else->{ }
        }
        item{OrderedDayCard(selected,plan,prefs,onAdd={addEvent=true},onEdit={editEvent=it},onReminder={event->reminderEvent=event to parseEventTime(event)})}
    }
    if(addEvent) EventDialog(selected,prefs,onDismiss={addEvent=false})
    editEvent?.let{event-> EventEditDialog(selected,event,prefs,onDismiss={editEvent=null},onReminder={label,time->editEvent=null;reminderEvent=label to time}) }
    reminderEvent?.let{(label,time)->ReminderDialog(label,selected,time,context){reminderEvent=null}}
}

@Composable
fun OrderedDayCard(date:LocalDate,plan:List<PlanDay>,prefs:SharedPreferences,onAdd:()->Unit,onEdit:(String)->Unit,onReminder:(String)->Unit){
    val day=plan.firstOrNull{it.date==date.toString()}
    Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(formatDate(date.toString()),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text("🍽️ ALIMENTAZIONE",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        day?.let{
            CalendarMealLine(BreakfastStyle, resolveTimeText(prefs,date,"breakfast","07:00"),"Colazione",resolveMealText(prefs,date.toString(),"breakfast",it.breakfast))
            CalendarMealLine(SnackStyle, resolveTimeText(prefs,date,"snack","10:30"),"Spuntino",resolveMealText(prefs,date.toString(),"snack",it.snack))
            CalendarMealLine(LunchStyle, resolveTimeText(prefs,date,"lunch","14:00"),"Pranzo",resolveMealText(prefs,date.toString(),"lunch",it.lunch))
        } ?: Text("Nessun piano alimentare per questa data",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("📅 IMPEGNI",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        val events=getEventsForDate(prefs,date)
        if(events.isEmpty())Text("Nessun impegno",style=MaterialTheme.typography.bodySmall)
        events.forEach{event->
            val style=activityStyleFor(event)
            Surface(modifier=Modifier.fillMaxWidth().clickable{onEdit(event)},shape=RoundedCornerShape(12.dp),color=style.color.copy(alpha=.10f)){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(event,modifier=Modifier.weight(1f));Text("✏️",fontSize=18.sp)}
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onAdd){Text("+ Aggiungi")}; if(events.isNotEmpty())OutlinedButton(onClick={onReminder(events.first())}){Text("🔔 Promemoria")}}
    }}
}

@Composable fun CalendarMealLine(style:CategoryStyle,time:String,title:String,body:String){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){Box(Modifier.width(4.dp).height(52.dp).background(style.color,RoundedCornerShape(4.dp)));Spacer(Modifier.width(8.dp));Column{Text("${style.emoji} $time · $title",fontWeight=FontWeight.SemiBold);Text(body,style=MaterialTheme.typography.bodySmall)}}}

@Composable
fun MonthGrid(month:YearMonth,selected:LocalDate,prefs:SharedPreferences,onSelect:(LocalDate)->Unit){
    val first=month.atDay(1);val offset=first.dayOfWeek.value-1
    Column{Row(Modifier.fillMaxWidth()){listOf("L","M","M","G","V","S","D").forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)}}
        val cells=offset+month.lengthOfMonth();val rows=(cells+6)/7
        repeat(rows){row->Row(Modifier.fillMaxWidth()){repeat(7){col->val dayNum=row*7+col-offset+1;if(dayNum !in 1..month.lengthOfMonth())Spacer(Modifier.weight(1f).aspectRatio(1f))else{val d=month.atDay(dayNum);val events=getEventsForDate(prefs,d);val icons=buildString{if(events.any{it.contains("Palestra",true)})append("🏋️");if(events.any{it.contains("Cammin",true)})append("🚶");if(events.any{it.contains("Lavor",true)})append("💼")};Column(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).border(if(d==selected)2.dp else 0.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(10.dp)).clickable{onSelect(d)}.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(dayNum.toString(),fontSize=12.sp);Text(icons.take(4),fontSize=9.sp)}}}}}
    }
}

@Composable fun WeekCalendarView(date:LocalDate,prefs:SharedPreferences,onSelect:(LocalDate)->Unit){val monday=date.minusDays((date.dayOfWeek.value-1).toLong());Column(verticalArrangement=Arrangement.spacedBy(6.dp)){repeat(7){i->val d=monday.plusDays(i.toLong());Card(Modifier.fillMaxWidth().clickable{onSelect(d)}){Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(d.format(DateTimeFormatter.ofPattern("EEE d",Locale.ITALIAN)));Text(getEventsForDate(prefs,d).joinToString("  ").ifBlank{"—"})}}}}}
@Composable fun YearCalendarView(year:Int,onMonth:(Int)->Unit){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){(1..12).chunked(3).forEach{chunk->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){chunk.forEach{m->Card(Modifier.weight(1f).clickable{onMonth(m)}){Text(YearMonth.of(year,m).format(DateTimeFormatter.ofPattern("MMM",Locale.ITALIAN)).uppercase(),Modifier.padding(18.dp).fillMaxWidth(),textAlign=TextAlign.Center,fontWeight=FontWeight.Bold)}}}}}}

@Composable
fun EventDialog(date:LocalDate,prefs:SharedPreferences,onDismiss:()->Unit){
    val types=listOf("🏋️ Palestra","🚶 Camminata","💼 Lavoro","📌 Impegno","🏠 Allenamento a casa","⭐ Altro");var type by remember{mutableStateOf(types[0])};var title by remember{mutableStateOf("")};var time by remember{mutableStateOf("18:00")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Nuovo impegno")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){types.forEach{t->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(type==t,{type=t});Text(t)}};OutlinedTextField(title,{title=it},label={Text("Titolo / nota")});OutlinedTextField(time,{time=it},label={Text("Ora HH:mm")})}},confirmButton={Button(onClick={addEvent(prefs,date,"$type ${title.trim()} · ${time.ifBlank{"18:00"}}".trim());onDismiss()}){Text("Salva")}},dismissButton={OutlinedButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
fun EventEditDialog(date:LocalDate,event:String,prefs:SharedPreferences,onDismiss:()->Unit,onReminder:(String,LocalTime)->Unit){
    var text by remember{mutableStateOf(event.substringBeforeLast("·").trim())};var time by remember{mutableStateOf(parseEventTime(event).toString())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("✏️ Modifica impegno")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(text,{text=it},label={Text("Impegno")});OutlinedTextField(time,{time=it},label={Text("Ora HH:mm")});OutlinedButton(onClick={onReminder(text,parseTime(time,LocalTime.of(9,0)))},modifier=Modifier.fillMaxWidth()){Text("🔔 Modifica / aggiungi promemoria")};OutlinedButton(onClick={removeEvent(prefs,date,event);onDismiss()},modifier=Modifier.fillMaxWidth()){Text("🗑️ Elimina")}}},confirmButton={Button(onClick={replaceEvent(prefs,date,event,"${text.trim()} · ${time.ifBlank{"09:00"}}");onDismiss()}){Text("Salva")}},dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
}

// ---------- PROGRESSI ----------
data class TrophyInfo(val icon:String,val name:String,val description:String,val current:Int,val target:Int,val category:String,val unlocked:Boolean)

@Composable
fun ProgressScreen(plan:List<PlanDay>,prefs:SharedPreferences){
    var weightText by remember{mutableStateOf("")};var weights by remember{mutableStateOf(loadWeights(prefs))};var initialWeight by remember{mutableStateOf(prefs.getFloat("initial_weight",0f).toString().takeIf{prefs.contains("initial_weight")}?:"")}
    val current=weights.firstOrNull()?.second;val start=prefs.getFloat("initial_weight",current?.toFloat()?:0f).toDouble();val change=if(current!=null&&start>0)current-start else null;val stats=calculateDiscipline(plan,prefs)
    var showAll by remember{mutableStateOf(false)};var selectedTrophy by remember{mutableStateOf<TrophyInfo?>(null)}
    val lost=((start-(current?:start)).coerceAtLeast(0.0)*10).roundToInt()
    val trophies=listOf(
        TrophyInfo("🏆","Costanza","7 giorni consecutivi con piano completato",stats["foodDays"]?:0,7,"Abitudini",(stats["foodDays"]?:0)>=7),
        TrophyInfo("💧","Idratazione Pro","7 giorni con obiettivo idratazione",stats["waterDays"]?:0,7,"Abitudini",(stats["waterDays"]?:0)>=7),
        TrophyInfo("🏋️","Allenatore","10 allenamenti completati",stats["activityCount"]?:0,10,"Allenamenti",(stats["activityCount"]?:0)>=10),
        TrophyInfo("🔥","Disciplina d'Oro","30 giornate di disciplina",stats["foodDays"]?:0,30,"Abitudini",(stats["foodDays"]?:0)>=30),
        TrophyInfo("⚖️","Perseveranza","5 kg persi",lost,50,"Peso",lost>=50),
        TrophyInfo("🚶","Maratoneta","50 attività/camminate",stats["activityCount"]?:0,50,"Allenamenti",(stats["activityCount"]?:0)>=50),
        TrophyInfo("🧠","Mente Forte","20 giornate alimentari complete",stats["foodDays"]?:0,20,"Alimentazione",(stats["foodDays"]?:0)>=20),
        TrophyInfo("🥗","Nutrizione Top","14 giorni alimentazione perfetta",stats["foodDays"]?:0,14,"Alimentazione",(stats["foodDays"]?:0)>=14),
        TrophyInfo("⭐","Equilibrio","7 giorni alimentazione + idratazione",minOf(stats["foodDays"]?:0,stats["waterDays"]?:0),7,"Abitudini",minOf(stats["foodDays"]?:0,stats["waterDays"]?:0)>=7)
    )
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Progressi",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("“La disciplina di oggi è il successo di domani.”",color=Color(0xFF2E7D32))}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MetricCard("Peso perso",if(change!=null)"${"%.1f".format((-change).coerceAtLeast(0.0))} kg" else "—",Modifier.weight(1f));MetricCard("Idratazione","${prefs.getInt("water_target_ml",2500)/1000.0} L",Modifier.weight(1f));MetricCard("Allenamenti","${stats["activityCount"]?:0}",Modifier.weight(1f))}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Disciplina",fontWeight=FontWeight.Bold);Text("${stats["general"]?:0}%")};DisciplineRow("🥗 Alimentazione",stats["food"]?:0);DisciplineRow("💧 Idratazione",stats["water"]?:0);DisciplineRow("🏋️ Allenamenti",stats["activity"]?:0)}}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("⚖️ Registra peso",fontWeight=FontWeight.Bold);OutlinedTextField(initialWeight,{initialWeight=it.replace(',','.')},label={Text("Peso iniziale")},singleLine=true);OutlinedTextField(weightText,{weightText=it.replace(',','.')},label={Text("Peso di oggi")},singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={initialWeight.toFloatOrNull()?.takeIf{it in 30f..300f}?.let{prefs.edit().putFloat("initial_weight",it).apply()}}){Text("Salva iniziale")};Button(onClick={weightText.toDoubleOrNull()?.takeIf{it in 30.0..300.0}?.let{saveWeight(prefs,LocalDate.now(),it);weights=loadWeights(prefs);weightText=""}}){Text("Salva oggi")}}}}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("🎯 Obiettivi",fontWeight=FontWeight.Bold);GoalRow("⚖️","Perdi 5 kg",(lost*100/50).coerceIn(0,100));GoalRow("💧","7 giorni idratazione",((stats["waterDays"]?:0)*100/7).coerceIn(0,100));GoalRow("🏋️","10 allenamenti",((stats["activityCount"]?:0)*100/10).coerceIn(0,100));GoalRow("🥗","14 giorni alimentazione",((stats["foodDays"]?:0)*100/14).coerceIn(0,100))}}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("🏆 Trofei",fontWeight=FontWeight.Bold);Text("${trophies.count{it.unlocked}} / ${trophies.size} sbloccati",style=MaterialTheme.typography.bodySmall)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){trophies.take(3).forEach{t->TrophyCard(t,Modifier.weight(1f)){selectedTrophy=t}}};Button(onClick={showAll=true},modifier=Modifier.fillMaxWidth()){Text("Vedi tutti i trofei")}}}}
        item{Text("Storico peso",fontWeight=FontWeight.Bold)};items(weights.take(30)){(d,kg)->Card{Text("${formatDate(d)} · ${"%.1f".format(kg)} kg",Modifier.padding(12.dp))}}
    }
    if(showAll) TrophyGalleryDialog(trophies,onDismiss={showAll=false},onSelect={selectedTrophy=it})
    selectedTrophy?.let{TrophyDetailDialog(it){selectedTrophy=null}}
}

@Composable fun MetricCard(label:String,value:String,modifier:Modifier=Modifier,sub:String?=null){Card(modifier){Column(Modifier.padding(12.dp)){Text(label,style=MaterialTheme.typography.bodySmall);Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);sub?.let{Text(it)}}}}
@Composable fun DisciplineRow(label:String,value:Int){Column{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Text("$value%",fontWeight=FontWeight.Bold)};LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth())}}
@Composable fun GoalRow(icon:String,label:String,value:Int){Column{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("$icon $label");Text("$value%")};LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth())}}
@Composable fun TrophyCard(t:TrophyInfo,modifier:Modifier,onClick:()->Unit){Card(modifier.clickable{onClick()}){Column(Modifier.padding(10.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){Text(if(t.unlocked)t.icon else "🔒",fontSize=30.sp);Text(t.name,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,fontSize=12.sp);Text("${t.current.coerceAtMost(t.target)} / ${t.target}",fontSize=11.sp);LinearProgressIndicator(progress={(t.current.toFloat()/t.target.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}}}
@Composable fun TrophyGalleryDialog(trophies:List<TrophyInfo>,onDismiss:()->Unit,onSelect:(TrophyInfo)->Unit){var filter by remember{mutableStateOf("Tutti")};val cats=listOf("Tutti","Alimentazione","Allenamenti","Abitudini","Peso");val shown=trophies.filter{filter=="Tutti"||it.category==filter};AlertDialog(onDismissRequest=onDismiss,title={Text("🏆 I miei trofei")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){cats.take(3).forEachIndexed{i,c->SegmentedButton(selected=filter==c,onClick={filter=c},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(c,fontSize=10.sp)}}}};items(shown.chunked(2)){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{t->TrophyCard(t,Modifier.weight(1f)){onSelect(t)}};if(row.size==1)Spacer(Modifier.weight(1f))}}}},confirmButton={},dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})}
@Composable fun TrophyDetailDialog(t:TrophyInfo,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(if(t.unlocked)"${t.icon} ${t.name}" else "🔒 ${t.name}")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(t.unlocked)t.icon else "🔒",fontSize=72.sp);Text(t.description,textAlign=TextAlign.Center);Text("Il tuo progresso ${t.current.coerceAtMost(t.target)} / ${t.target}",fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={(t.current.toFloat()/t.target.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text(if(t.unlocked)"Sbloccato! Il divano non ha presentato ricorso. 😄" else "Continua così: manca meno di quanto sembri. 😏",style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)}},confirmButton={Button(onClick=onDismiss){Text("Chiudi")}})}

// ---------- ALLENAMENTI ----------
@Composable
fun TrainingScreen(prefs:SharedPreferences,context:Context){
    var reminder by remember{mutableStateOf<Pair<String,LocalTime>?>(null)};var editExercise by remember{mutableStateOf<String?>(null)}
    val exercises=listOf("Collo e spalle" to 60,"Mobilità anche" to 60,"Marcia leggera" to 90,"Stretching dolce" to 120)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Allenamenti",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        item{ColoredCard(WakeStyle.color){Text("🌅 Risveglio muscolare",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Massimo 10 minuti · mobilità e attivazione leggera");exercises.forEach{(name,def)->val sec=prefs.getInt("exercise_${name.hashCode()}",def);Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),modifier=Modifier.fillMaxWidth().clickable{editExercise=name}){Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(name);Text("$sec sec ✏️",fontWeight=FontWeight.Bold)}}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={markTraining(prefs,"wakeup")}){Text("▶ Completa")};OutlinedButton(onClick={reminder="Risveglio muscolare" to LocalTime.of(6,45)}){Text("🔔")}}}}
        item{ColoredCard(HomeStyle.color){Text("🏠 Allenamento a casa",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Solo nei giorni senza palestra · pomeriggio/sera");listOf("💪 Total body · 25 min","🔥 Core · 15 min","🦵 Gambe · 20 min","🧘 Mobilità · 15 min").forEach{label->OutlinedButton(onClick={markTraining(prefs,"home_${label.hashCode()}")},modifier=Modifier.fillMaxWidth()){Text(label)}};OutlinedButton(onClick={reminder="Allenamento a casa" to LocalTime.of(18,0)}){Text("🔔 Promemoria")}}}
        item{ColoredCard(GymStyle.color){Text("🏋️ Palestra",fontWeight=FontWeight.Bold);Text("Nei giorni palestra l'allenamento a casa non viene proposto automaticamente.");OutlinedButton(onClick={reminder="Palestra" to LocalTime.of(17,0)}){Text("🔔 Promemoria")}}}
    }
    editExercise?.let{name->val def=exercises.firstOrNull{it.first==name}?.second?:60;ExerciseDurationDialog(name,prefs.getInt("exercise_${name.hashCode()}",def),onDismiss={editExercise=null}){sec->prefs.edit().putInt("exercise_${name.hashCode()}",sec).apply();editExercise=null}}
    reminder?.let{(label,time)->ReminderDialog(label,LocalDate.now(),time,context){reminder=null}}
}
@Composable fun ExerciseDurationDialog(name:String,initial:Int,onDismiss:()->Unit,onSave:(Int)->Unit){var sec by remember{mutableStateOf(initial.toString())};AlertDialog(onDismissRequest=onDismiss,title={Text("⏱️ $name")},text={OutlinedTextField(sec,{sec=it.filter(Char::isDigit)},label={Text("Durata in secondi")})},confirmButton={Button(onClick={onSave(sec.toIntOrNull()?.coerceIn(10,600)?:initial)}){Text("Salva")}},dismissButton={OutlinedButton(onClick=onDismiss){Text("Annulla")}})}

// ---------- PIANO ----------
@Composable
fun PlanScreen(prefs:SharedPreferences){
    var target by remember{mutableStateOf(prefs.getInt("calorie_target",1900).toString())};var waterTarget by remember{mutableStateOf(prefs.getInt("water_target_ml",2500).toString())};var calculated by remember{mutableStateOf(prefs.getInt("calorie_calculated",2300).toString())};var week by rememberSaveable{mutableIntStateOf(1)};var editDay by remember{mutableStateOf<Int?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Piano",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("📋 Piano della nutrizionista",fontWeight=FontWeight.Bold);Text("4 settimane modificabili separatamente. Puoi copiare una settimana sulle successive.");SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){(1..4).forEachIndexed{i,w->SegmentedButton(selected=week==w,onClick={week=w},shape=SegmentedButtonDefaults.itemShape(i,4)){Text("S$w")}}};(1..7).forEach{d->val name=listOf("Lunedì","Martedì","Mercoledì","Giovedì","Venerdì","Sabato","Domenica")[d-1];val text=prefs.getString("plan_w${week}_d$d","")?:"";OutlinedButton(onClick={editDay=d},modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.Start){Text("$name · ✏️",fontWeight=FontWeight.Bold);Text(text.ifBlank{"Inserisci il piano del giorno"},maxLines=2,style=MaterialTheme.typography.bodySmall)}}};OutlinedButton(onClick={copyWeek(prefs,week)},modifier=Modifier.fillMaxWidth()){Text("📄 Copia settimana $week nelle successive")}}}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("🔥 Fabbisogno calorico",fontWeight=FontWeight.Bold);OutlinedTextField(calculated,{calculated=it.filter(Char::isDigit)},label={Text("Calcolato dall'app")},suffix={Text("kcal")});OutlinedTextField(target,{target=it.filter(Char::isDigit)},label={Text("Obiettivo impostato")},suffix={Text("kcal")});Text("Puoi usare il valore indicato dal nutrizionista.",style=MaterialTheme.typography.bodySmall);Button(onClick={prefs.edit().putInt("calorie_calculated",calculated.toIntOrNull()?.coerceIn(800,6000)?:2300).putInt("calorie_target",target.toIntOrNull()?.coerceIn(800,6000)?:1900).apply()}){Text("Salva")}}}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("💧 Obiettivo idratazione",fontWeight=FontWeight.Bold);OutlinedTextField(waterTarget,{waterTarget=it.filter(Char::isDigit)},label={Text("ml al giorno")});Button(onClick={prefs.edit().putInt("water_target_ml",waterTarget.toIntOrNull()?.coerceIn(500,6000)?:2500).apply()}){Text("Salva")}}}}
    }
    editDay?.let{d->PlanDayEditDialog(week,d,prefs.getString("plan_w${week}_d$d","")?:"",onDismiss={editDay=null}){text->prefs.edit().putString("plan_w${week}_d$d",text).apply();editDay=null}}
}
@Composable fun PlanDayEditDialog(week:Int,day:Int,initial:String,onDismiss:()->Unit,onSave:(String)->Unit){var text by remember{mutableStateOf(initial)};AlertDialog(onDismissRequest=onDismiss,title={Text("Settimana $week · Giorno $day")},text={Column{Text("Inserisci liberamente colazione, spuntino, pranzo, quantità e orari.");OutlinedTextField(text,{text=it},modifier=Modifier.fillMaxWidth(),minLines=7,label={Text("Piano del giorno")})}},confirmButton={Button(onClick={onSave(text)}){Text("Salva")}},dismissButton={OutlinedButton(onClick=onDismiss){Text("Annulla")}})}

// ---------- PROMEMORIA ----------
@Composable
fun ReminderDialog(label: String, date: LocalDate, baseTime: LocalTime, context: Context, onDismiss: () -> Unit) {
    var customHours by remember { mutableStateOf("2") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("🔔 Promemoria · $label") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scegli quando essere avvisato. La frase della notifica sarà ironica e varierà automaticamente.")
            ReminderChoice("All'ora prevista") { scheduleReminder(context, label, date, baseTime) ; onDismiss() }
            ReminderChoice("30 minuti prima") { scheduleReminder(context, label, date, baseTime.minusMinutes(30)); onDismiss() }
            ReminderChoice("2 ore prima") { scheduleReminder(context, label, date, baseTime.minusHours(2)); onDismiss() }
            ReminderChoice("Il giorno prima") { scheduleReminder(context, label, date.minusDays(1), baseTime); onDismiss() }
            ReminderChoice("Tutto il giorno (ore 08:00)") { scheduleReminder(context, label, date, LocalTime.of(8,0)); onDismiss() }
            OutlinedTextField(customHours, { customHours = it.filter(Char::isDigit) }, label = { Text("Ore prima personalizzate") })
            OutlinedButton(onClick = { val h = customHours.toLongOrNull()?.coerceIn(1,168) ?: 2; val dt = LocalDateTime.of(date, baseTime).minusHours(h); scheduleReminder(context, label, dt.toLocalDate(), dt.toLocalTime()); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Imposta ore personalizzate") }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } })
}

@Composable fun ReminderChoice(label: String, onClick: () -> Unit) { OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) } }

fun createNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("nutrition_reminders", "Promemoria Alessandro Nutrition", NotificationManager.IMPORTANCE_HIGH).apply { description = "Promemoria ironici per pasti, allenamenti e impegni" })
}

fun scheduleReminder(context: Context, label: String, date: LocalDate, time: LocalTime) {
    var trigger = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (trigger <= System.currentTimeMillis()) trigger = System.currentTimeMillis() + 5000
    val id = (label + date.toString() + time.toString() + System.nanoTime()).hashCode()
    val intent = Intent(context, ReminderReceiver::class.java).putExtra("label", label).putExtra("id", id)
    val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "Promemoria"
        val id = intent.getIntExtra("id", System.currentTimeMillis().toInt())
        val phrase = ironicReminder(label)
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, "nutrition_reminders").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(label).setContentText(phrase).setStyle(NotificationCompat.BigTextStyle().bigText(phrase)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        }
    }
}

fun ironicReminder(label: String): String {
    val pool = when {
        label.contains("Palestra", true) -> listOf("Il divano ha votato contro. Per fortuna non decide lui. 🏋️", "I muscoli hanno mandato un sollecito: pare sia ora di lavorare. 😄", "La palestra non si frequenta col pensiero. Ci tocca andare. 😏")
        label.contains("Cammin", true) -> listOf("Le scarpe stanno iniziando a sentirsi decorative. Facciamole lavorare. 👟", "La passeggiata non si completa da sola, purtroppo. 🚶", "Due passi: il modo elegante di dire che oggi il divano perde. 😄")
        label.contains("Colazione", true) -> listOf("Colazione: il caffè da solo continua a non essere un pasto completo. ☕", "È ora di dare al corpo qualcosa di più concreto delle buone intenzioni. 🍳", "Colazione pronta? I muscoli stanno controllando l'orologio. 😄")
        label.contains("Spuntino", true) -> listOf("Spuntino! Le proteine hanno presentato richiesta di presenza. 🥤", "Piccola pausa, grande missione: non dimenticare lo spuntino. 😄", "È arrivato quel momento in cui una mela non può più fingere di essere invisibile. 🍎")
        label.contains("Pranzo", true) -> listOf("Pranzo! Pesare il pollo non significa interrogarlo. 🍽️", "È ora di pranzo: bilancia pronta, stomaco pure. 😄", "Il piano alimentare ha appena bussato. Apriamo? 🍽️")
        label.contains("acqua", true) || label.contains("Bere", true) -> listOf("La borraccia segnala abbandono emotivo. 💧", "Acqua: il corpo ha fatto richiesta ufficiale. 💦", "Un bicchiere adesso vale più di dieci promesse dopo. 😄")
        label.contains("Lavor", true) -> listOf("Sì, oggi si lavora. Ho controllato due volte. 💼", "Promemoria professionale: il weekend non è ancora arrivato. 😄", "Lavoro in vista. Coraggio: anche questa giornata ha una fine. 😏")
        else -> listOf("Promemoria ufficiale: il tuo futuro te ringrazierà. Forse. 😄", "Era troppo facile affidarsi alla memoria, quindi eccomi qui. 🔔", "Piccolo promemoria, grande tentativo di tenere tutto sotto controllo. 😎")
    }
    return pool[(System.nanoTime().absoluteValue % pool.size).toInt()]
}

// ---------- STORAGE / LOGICA ----------
fun resolveMealText(prefs: SharedPreferences, dateRaw: String, meal: String, original: String): String {
    val date = LocalDate.parse(dateRaw)
    prefs.getString("meal_${date}_${meal}", null)?.let { return it }
    val rules = prefs.getString("meal_rules_$meal", "[]") ?: "[]"
    return runCatching {
        val arr = JSONArray(rules)
        var bestDate: LocalDate? = null; var bestText: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val start = LocalDate.parse(o.getString("start"))
            if (!date.isBefore(start) && (bestDate == null || start.isAfter(bestDate))) { bestDate = start; bestText = o.getString("text") }
        }
        bestText ?: original
    }.getOrDefault(original)
}

fun saveMealOverride(prefs: SharedPreferences, meal: String, date: LocalDate, text: String, mode: String, startDate: LocalDate?) {
    if (mode == "today") prefs.edit().putString("meal_${date}_${meal}", text).apply() else {
        val start = if (mode == "future") date else (startDate ?: date)
        val arr = runCatching { JSONArray(prefs.getString("meal_rules_$meal", "[]")) }.getOrElse { JSONArray() }
        arr.put(JSONObject().put("start", start.toString()).put("text", text))
        prefs.edit().putString("meal_rules_$meal", arr.toString()).apply()
    }
}
fun clearMealOverrideForDate(prefs: SharedPreferences, meal: String, date: LocalDate) { prefs.edit().remove("meal_${date}_${meal}").apply() }

fun addEvent(prefs: SharedPreferences, date: LocalDate, text: String) { val key = "events_$date"; val old = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf(); old.add(text); prefs.edit().putStringSet(key, old).apply() }
fun getEventsForDate(prefs: SharedPreferences, date: LocalDate): List<String> = prefs.getStringSet("events_$date", emptySet())?.sorted() ?: emptyList()
fun removeEvent(prefs:SharedPreferences,date:LocalDate,event:String){val key="events_$date";val set=prefs.getStringSet(key,emptySet())?.toMutableSet()?:mutableSetOf();set.remove(event);prefs.edit().putStringSet(key,set).apply()}
fun replaceEvent(prefs:SharedPreferences,date:LocalDate,old:String,new:String){removeEvent(prefs,date,old);addEvent(prefs,date,new)}
fun parseEventTime(event:String):LocalTime=runCatching{LocalTime.parse(event.substringAfterLast("·").trim())}.getOrDefault(LocalTime.of(9,0))
fun parseTime(raw:String,fallback:LocalTime):LocalTime=runCatching{LocalTime.parse(raw)}.getOrDefault(fallback)

fun resolveTimeText(prefs:SharedPreferences,date:LocalDate,type:String,original:String):String{
    prefs.getString("time_${date}_$type",null)?.let{return it}
    val arr=runCatching{JSONArray(prefs.getString("time_rules_$type","[]"))}.getOrElse{JSONArray()};var best:LocalDate?=null;var value:String?=null
    for(i in 0 until arr.length()){val o=arr.getJSONObject(i);val st=LocalDate.parse(o.getString("start"));if(!date.isBefore(st)&&(best==null||st.isAfter(best))){best=st;value=o.getString("time")}}
    return value?:original
}
fun saveTimeOverride(prefs:SharedPreferences,type:String,date:LocalDate,time:String,mode:String,startDate:LocalDate?){if(mode=="today")prefs.edit().putString("time_${date}_$type",time).apply()else{val start=if(mode=="future")date else(startDate?:date);val arr=runCatching{JSONArray(prefs.getString("time_rules_$type","[]"))}.getOrElse{JSONArray()};arr.put(JSONObject().put("start",start.toString()).put("time",time));prefs.edit().putString("time_rules_$type",arr.toString()).apply()}}
fun copyWeek(prefs:SharedPreferences,week:Int){if(week>=4)return;val e=prefs.edit();for(w in week+1..4)for(d in 1..7)e.putString("plan_w${w}_d$d",prefs.getString("plan_w${week}_d$d","")?:"");e.apply()}

fun saveWeight(prefs: SharedPreferences, date: LocalDate, kg: Double) { val arr = runCatching { JSONArray(prefs.getString("weights_json", "[]")) }.getOrElse { JSONArray() }; arr.put(JSONObject().put("date", date.toString()).put("kg", kg)); prefs.edit().putString("weights_json", arr.toString()).apply() }
fun loadWeights(prefs: SharedPreferences): List<Pair<String, Double>> = runCatching { val arr = JSONArray(prefs.getString("weights_json", "[]")); (0 until arr.length()).map { i -> arr.getJSONObject(i).let { it.getString("date") to it.getDouble("kg") } }.sortedByDescending { it.first } }.getOrDefault(emptyList())

fun calculateDiscipline(plan: List<PlanDay>, prefs: SharedPreferences): Map<String, Int> {
    val today = LocalDate.now(); val recent = plan.filter { val d = LocalDate.parse(it.date); !d.isAfter(today) && ChronoUnit.DAYS.between(d,today) < 14 }
    var foodPossible=0; var foodDone=0; var waterPossible=0; var waterDone=0; var actPossible=0; var actDone=0; var foodDays=0; var waterDays=0; var actCount=0
    recent.forEach { d -> val k=d.date; val meals=listOf("breakfast","snack","lunch"); foodPossible += 3; val fd=meals.count { prefs.getBoolean("${k}_done_$it",false) }; foodDone += fd; if(fd==3) foodDays++; waterPossible++; val wt=prefs.getInt("water_target_ml",2500); if(prefs.getInt("${k}_water_ml",0)>=wt){waterDone++;waterDays++}; if(!d.activity.contains("Riposo",true)){actPossible++; if(prefs.getBoolean("${k}_done_activity",false)){actDone++;actCount++}} }
    fun pct(done:Int, possible:Int)=if(possible==0)100 else (done*100/possible)
    val food=pct(foodDone,foodPossible); val water=pct(waterDone,waterPossible); val activity=pct(actDone,actPossible); val general=(food+water+activity)/3
    return mapOf("food" to food,"water" to water,"activity" to activity,"general" to general,"foodDays" to foodDays,"waterDays" to waterDays,"activityCount" to actCount)
}

fun calculateGoalCount(weights: List<Pair<String,Double>>, stats: Map<String,Int>, prefs: SharedPreferences): Int {
    var count=0; val start=prefs.getFloat("initial_weight",0f).toDouble(); val current=weights.firstOrNull()?.second
    if(start>0 && current!=null){ val lost=(start-current).coerceAtLeast(0.0); count += (lost/.5).toInt() }
    count += (stats["foodDays"] ?: 0)/3; count += (stats["waterDays"] ?: 0)/3; count += (stats["activityCount"] ?: 0)/3
    return count
}
fun markTraining(prefs: SharedPreferences, id: String) { val key="training_${LocalDate.now()}_$id"; prefs.edit().putBoolean(key,true).apply() }

fun activityStyleFor(name: String): CategoryStyle = when { name.contains("Palestra",true)->GymStyle; name.contains("Cammin",true)->WalkStyle; name.contains("casa",true)->HomeStyle; name.contains("Lavor",true)->WorkStyle; else -> CategoryStyle("⭐", name, EveningStyle.color) }

// ---------- DATABASE NUTRIZIONALE SEMPLICE ----------
data class FoodInfo(val aliases: List<String>, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)
val foods = listOf(
    FoodInfo(listOf("anguria"),30.0,0.6,7.6,0.2), FoodInfo(listOf("mela","mele"),52.0,0.3,14.0,0.2), FoodInfo(listOf("banana","banane"),89.0,1.1,23.0,0.3),
    FoodInfo(listOf("pollo","petto di pollo"),165.0,31.0,0.0,3.6), FoodInfo(listOf("tacchino"),135.0,29.0,0.0,1.5), FoodInfo(listOf("tonno"),116.0,26.0,0.0,1.0),
    FoodInfo(listOf("yogurt greco"),73.0,10.0,4.0,1.8), FoodInfo(listOf("avena"),389.0,16.9,66.3,6.9), FoodInfo(listOf("proteine","proteine in polvere"),390.0,75.0,10.0,7.0),
    FoodInfo(listOf("zucchine","zucchina"),17.0,1.2,3.1,0.3), FoodInfo(listOf("couscous"),376.0,12.8,77.4,0.6), FoodInfo(listOf("pasta proteica"),350.0,30.0,45.0,5.0),
    FoodInfo(listOf("uovo","uova"),143.0,12.6,0.7,9.5), FoodInfo(listOf("feta"),264.0,14.2,4.1,21.3), FoodInfo(listOf("mandorle"),579.0,21.2,21.6,49.9)
)

fun estimateNutrition(text: String): NutritionEstimate {
    var kcal=0.0; var p=0.0; var c=0.0; var f=0.0
    val lower=text.lowercase(Locale.ITALIAN)
    foods.forEach { info ->
        val alias=info.aliases.firstOrNull { lower.contains(it) } ?: return@forEach
        val idx=lower.indexOf(alias); val tail=lower.substring(idx+alias.length).take(30)
        val grams=Regex("(\\d+(?:[.,]\\d+)?)\\s*g\\b").find(tail)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: return@forEach
        val factor=grams/100.0; kcal += info.kcal*factor; p += info.protein*factor; c += info.carbs*factor; f += info.fat*factor
    }
    // olio: cucchiaino ~5g, cucchiaio ~10g. Il sale non aggiunge kcal.
    val tablespoons=Regex("olio[^.]{0,30}(\\d+(?:[.,]\\d+)?)?\\s*cucchiai").find(lower)?.groupValues?.getOrNull(1)?.replace(',','.')?.toDoubleOrNull()
    val teaspoons=Regex("olio[^.]{0,30}(\\d+(?:[.,]\\d+)?)?\\s*cucchiaini").find(lower)?.groupValues?.getOrNull(1)?.replace(',','.')?.toDoubleOrNull()
    val oilGrams=(tablespoons ?: if(lower.contains("olio") && lower.contains("1 cucchiaio")) 1.0 else 0.0)*10 + (teaspoons ?: 0.0)*5
    kcal += oilGrams*8.84; f += oilGrams
    return NutritionEstimate(kcal.roundToInt(),p.roundToInt(),c.roundToInt(),f.roundToInt())
}

fun formatDate(raw: String): String { val d=LocalDate.parse(raw); return d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.ITALIAN)).replaceFirstChar { it.uppercase() } }
private val Long.absoluteValue: Long get() = if(this==Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(this)
