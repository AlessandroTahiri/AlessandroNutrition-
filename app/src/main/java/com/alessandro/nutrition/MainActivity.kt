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

    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("Oggi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(formatDate(day.date), style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${totalNutrition.kcal} / $target kcal", fontWeight = FontWeight.Bold)
                    Text("P ${totalNutrition.protein}g · C ${totalNutrition.carbs}g · G ${totalNutrition.fat}g", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("← Ieri") }
                OutlinedButton(onClick = { index = todayIndex }, enabled = index != todayIndex) { Text("Oggi") }
                OutlinedButton(onClick = { if (index < plan.lastIndex) index++ }, enabled = index < plan.lastIndex) { Text("Domani →") }
            }
        }

        item {
            ActivityCard(WakeStyle, "Risveglio", "Acqua · vitamine · magnesio", onEdit = {}, onReminder = { showReminderFor = "Risveglio" to LocalTime.of(6,30) })
        }
        item {
            MealEditableCard(BreakfastStyle, "Colazione", "07:00", breakfast.value, breakfastNutrition, breakfastDone,
                onChecked = { breakfastDone = it; prefs.edit().putBoolean("${key}_done_breakfast", it).apply() },
                onEdit = { showMealEditor = "breakfast" }, onReminder = { showReminderFor = "Colazione" to LocalTime.of(7,0) })
        }
        item {
            MealEditableCard(SnackStyle, "Spuntino", "10:30", snack.value, snackNutrition, snackDone,
                onChecked = { snackDone = it; prefs.edit().putBoolean("${key}_done_snack", it).apply() },
                onEdit = { showMealEditor = "snack" }, onReminder = { showReminderFor = "Spuntino" to LocalTime.of(10,30) })
        }
        item {
            MealEditableCard(LunchStyle, "Pranzo", "14:00", lunch.value, lunchNutrition, lunchDone,
                onChecked = { lunchDone = it; prefs.edit().putBoolean("${key}_done_lunch", it).apply() },
                onEdit = { showMealEditor = "lunch" }, onReminder = { showReminderFor = "Pranzo" to LocalTime.of(14,0) })
        }
        item {
            ColoredCard(Color(0xFF64748B)) {
                Text("⏱️ Digiuno", fontWeight = FontWeight.Bold)
                Text(day.fasting, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { showReminderFor = "Inizio digiuno" to LocalTime.of(15,0) }) { Text("🔔 Promemoria") }
            }
        }
        item {
            ColoredCard(activityStyle.color) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("${activityStyle.emoji} Attività", fontWeight = FontWeight.Bold); Text("$activityName · $activityTime · $activityDuration min") }
                    AssistChip(onClick = {}, label = { Text(activityStyle.label) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showActivityEditor = true }) { Text("✏️ Modifica") }
                    OutlinedButton(onClick = {
                        val time = runCatching { LocalTime.parse(activityTime) }.getOrDefault(LocalTime.of(18,0))
                        showReminderFor = activityName to time
                    }) { Text("🔔 Promemoria") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = activityDone, onCheckedChange = { activityDone = it; prefs.edit().putBoolean("${key}_done_activity", it).apply() })
                    Text(if (activityDone) "Completata" else "Segna come completata")
                }
            }
        }
        item {
            ColoredCard(WaterStyle.color) {
                Text("💧 Idratazione", fontWeight = FontWeight.Bold)
                val targetMl = prefs.getInt("water_target_ml", 2500)
                Text("${waterMl / 1000.0} / ${targetMl / 1000.0} L")
                LinearProgressIndicator(progress = { (waterMl.toFloat() / targetMl.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { waterMl += 250; prefs.edit().putInt("${key}_water_ml", waterMl).apply() }) { Text("+ 250 ml") }
                    OutlinedButton(onClick = { waterMl = (waterMl - 250).coerceAtLeast(0); prefs.edit().putInt("${key}_water_ml", waterMl).apply() }) { Text("− 250") }
                    OutlinedButton(onClick = { showReminderFor = "Bere acqua" to LocalTime.of(9,0) }) { Text("🔔") }
                }
            }
        }
        item {
            ActivityCard(EveningStyle, "Sera", "Riepilogo giornata · note · preparazione per domani", onEdit = {}, onReminder = { showReminderFor = "Riepilogo serale" to LocalTime.of(21,0) })
        }
    }

    showMealEditor?.let { meal ->
        val current = when (meal) { "breakfast" -> breakfast.value; "snack" -> snack.value; else -> lunch.value }
        MealEditDialog(
            title = when (meal) { "breakfast" -> "Colazione"; "snack" -> "Spuntino"; else -> "Pranzo" },
            initial = current,
            date = date,
            onDismiss = { showMealEditor = null },
            onSave = { text, mode, startDate ->
                saveMealOverride(prefs, meal, date, text, mode, startDate)
                when (meal) { "breakfast" -> breakfast.value = text; "snack" -> snack.value = text; else -> lunch.value = text }
                showMealEditor = null
            },
            onRestore = {
                clearMealOverrideForDate(prefs, meal, date)
                val original = when (meal) { "breakfast" -> day.breakfast; "snack" -> day.snack; else -> day.lunch }
                when (meal) { "breakfast" -> breakfast.value = original; "snack" -> snack.value = original; else -> lunch.value = original }
                showMealEditor = null
            }
        )
    }

    showReminderFor?.let { (label, time) ->
        ReminderDialog(label, date, time, context, onDismiss = { showReminderFor = null })
    }

    if (showActivityEditor) {
        ActivityEditDialog(activityName, activityTime, activityDuration, onDismiss = { showActivityEditor = false }) { name, time, duration ->
            prefs.edit().putString("${key}_activity_name", name).putString("${key}_activity_time", time).putInt("${key}_activity_duration", duration).apply()
            showActivityEditor = false
        }
    }
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
    ColoredCard(style.color) {
        Text("${style.emoji} $title", fontWeight = FontWeight.Bold)
        Text(body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("✏️ Modifica") }
            OutlinedButton(onClick = onReminder) { Text("🔔 Promemoria") }
        }
    }
}

@Composable
fun MealEditableCard(style: CategoryStyle, title: String, time: String, body: String, nutrition: NutritionEstimate, checked: Boolean, onChecked: (Boolean) -> Unit, onEdit: () -> Unit, onReminder: () -> Unit) {
    ColoredCard(style.color) {
        Text("${style.emoji} $title · $time", fontWeight = FontWeight.Bold)
        Text(body)
        Text("≈ ${nutrition.kcal} kcal · P ${nutrition.protein}g · C ${nutrition.carbs}g · G ${nutrition.fat}g", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("✏️ Modifica") }
            OutlinedButton(onClick = onReminder) { Text("🔔 Promemoria") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = checked, onCheckedChange = onChecked); Text(if (checked) "Fatto" else "Segna come fatto") }
    }
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
    var reminderEvent by remember { mutableStateOf<Pair<String, LocalTime>?>(null) }
    val ym = YearMonth.parse(cursor)
    val selected = LocalDate.parse(selectedDate)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Giorno", "Settimana", "Mese", "Anno").forEachIndexed { i, label ->
                    SegmentedButton(selected = view == label, onClick = { view = label }, shape = SegmentedButtonDefaults.itemShape(i, 4)) { Text(label, fontSize = 11.sp) }
                }
            }
        }
        when (view) {
            "Giorno" -> item { DayCalendarView(selected, plan, prefs, onAdd = { addEvent = true }) }
            "Settimana" -> item { WeekCalendarView(selected, prefs) { selectedDate = it.toString(); view = "Giorno" } }
            "Anno" -> item { YearCalendarView(selected.year) { month -> cursor = YearMonth.of(selected.year, month).toString(); view = "Mese" } }
            else -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { cursor = ym.minusMonths(1).toString() }) { Text("‹") }
                        Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { cursor = ym.plusMonths(1).toString() }) { Text("›") }
                    }
                    MonthGrid(ym, selected, prefs) { selectedDate = it.toString() }
                }
            }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDate(selected.toString()), fontWeight = FontWeight.Bold)
                val events = getEventsForDate(prefs, selected)
                if (events.isEmpty()) Text("Nessun impegno personalizzato")
                events.forEach { event -> Text(event) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addEvent = true }) { Text("+ Aggiungi") }
                    if (events.isNotEmpty()) OutlinedButton(onClick = { reminderEvent = events.first() to LocalTime.of(9,0) }) { Text("🔔 Promemoria") }
                }
            } }
        }
    }

    if (addEvent) EventDialog(selected, prefs, onDismiss = { addEvent = false })
    reminderEvent?.let { (label, time) -> ReminderDialog(label, selected, time, context) { reminderEvent = null } }
}

@Composable
fun MonthGrid(month: YearMonth, selected: LocalDate, prefs: SharedPreferences, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    Column {
        Row(Modifier.fillMaxWidth()) { listOf("L","M","M","G","V","S","D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) } }
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNum = row * 7 + col - offset + 1
                    if (dayNum !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                        val d = month.atDay(dayNum)
                        val events = getEventsForDate(prefs, d)
                        val bg = when {
                            events.any { it.contains("Palestra", true) } -> GymStyle.color.copy(alpha=.16f)
                            events.any { it.contains("Camminata", true) } -> WalkStyle.color.copy(alpha=.16f)
                            events.any { it.contains("Lavoro", true) } -> WorkStyle.color.copy(alpha=.16f)
                            else -> Color.Transparent
                        }
                        Column(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).background(bg, RoundedCornerShape(10.dp)).border(if (d == selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)).clickable { onSelect(d) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayNum.toString(), fontSize = 12.sp)
                            Text(when { events.any { it.contains("Palestra", true) } -> "🏋️"; events.any { it.contains("Camminata", true) } -> "🚶"; events.any { it.contains("Lavoro", true) } -> "💼"; else -> "" }, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun DayCalendarView(date: LocalDate, plan: List<PlanDay>, prefs: SharedPreferences, onAdd: () -> Unit) {
    val day = plan.firstOrNull { it.date == date.toString() }
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatDate(date.toString()), fontWeight = FontWeight.Bold)
        day?.let { Text("🍳 ${resolveMealText(prefs, date.toString(), "breakfast", it.breakfast)}"); Text("🥤 ${resolveMealText(prefs, date.toString(), "snack", it.snack)}"); Text("🍽️ ${resolveMealText(prefs, date.toString(), "lunch", it.lunch)}") }
        getEventsForDate(prefs, date).forEach { Text(it) }
        Button(onClick = onAdd) { Text("+ Aggiungi impegno") }
    } }
}

@Composable fun WeekCalendarView(date: LocalDate, prefs: SharedPreferences, onSelect: (LocalDate) -> Unit) {
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(7) { i -> val d = monday.plusDays(i.toLong()); Card(Modifier.fillMaxWidth().clickable { onSelect(d) }) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(d.format(DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN))); Text(getEventsForDate(prefs, d).joinToString("  ").ifBlank { "—" }) } } }
    }
}

@Composable fun YearCalendarView(year: Int, onMonth: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..12).chunked(3).forEach { chunk -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chunk.forEach { m -> Card(Modifier.weight(1f).clickable { onMonth(m) }) { Text(YearMonth.of(year,m).format(DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)).uppercase(), Modifier.padding(18.dp).fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) } } } }
    }
}

@Composable
fun EventDialog(date: LocalDate, prefs: SharedPreferences, onDismiss: () -> Unit) {
    val types = listOf("🏋️ Palestra", "🚶 Camminata", "💼 Lavoro", "📌 Impegno", "🏠 Allenamento a casa", "⭐ Altro")
    var type by remember { mutableStateOf(types[0]) }
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("18:00") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuovo impegno") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(type == t, { type = t }); Text(t) } }
        OutlinedTextField(title, { title = it }, label = { Text("Titolo / nota") })
        OutlinedTextField(time, { time = it }, label = { Text("Ora") })
    } }, confirmButton = { Button(onClick = { addEvent(prefs, date, "$type ${title.trim()} · $time".trim()); onDismiss() }) { Text("Salva") } }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Annulla") } })
}

// ---------- PROGRESSI ----------
@Composable
fun ProgressScreen(plan: List<PlanDay>, prefs: SharedPreferences) {
    var weightText by remember { mutableStateOf("") }
    var weights by remember { mutableStateOf(loadWeights(prefs)) }
    var initialWeight by remember { mutableStateOf(prefs.getFloat("initial_weight", 0f).toString().takeIf { prefs.contains("initial_weight") } ?: "") }
    val current = weights.firstOrNull()?.second
    val start = prefs.getFloat("initial_weight", current?.toFloat() ?: 0f).toDouble()
    val change = if (current != null && start > 0) current - start else null
    val stats = calculateDiscipline(plan, prefs)
    val completedGoals = calculateGoalCount(weights, stats, prefs)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Progressi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Peso iniziale", if (start > 0) "%.1f kg".format(start) else "—", Modifier.weight(1f))
                MetricCard("Peso attuale", current?.let { "%.1f kg".format(it) } ?: "—", Modifier.weight(1f), change?.let { "%+.1f kg".format(it) })
            }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚖️ Registra peso", fontWeight = FontWeight.Bold)
                OutlinedTextField(initialWeight, { initialWeight = it.replace(',', '.') }, label = { Text("Peso iniziale") }, singleLine = true)
                OutlinedTextField(weightText, { weightText = it.replace(',', '.') }, label = { Text("Peso di oggi") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { initialWeight.toFloatOrNull()?.takeIf { it in 30f..300f }?.let { prefs.edit().putFloat("initial_weight", it).apply() } }) { Text("Salva iniziale") }
                    Button(onClick = { weightText.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }?.let { saveWeight(prefs, LocalDate.now(), it); weights = loadWeights(prefs); weightText = "" } }) { Text("Salva oggi") }
                }
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("🔥 Disciplina generale", fontWeight = FontWeight.Bold); Text("${stats["general"] ?: 0}%", fontWeight = FontWeight.Bold) }
                DisciplineRow("🥗 Alimentazione", stats["food"] ?: 0)
                DisciplineRow("💧 Idratazione", stats["water"] ?: 0)
                DisciplineRow("🏋️ Palestra / attività", stats["activity"] ?: 0)
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎯 Piccoli traguardi", fontWeight = FontWeight.Bold)
                GoalRow("⚖️", "Primi 0,5 kg", if (change != null) ((-change / .5) * 100).roundToInt().coerceIn(0,100) else 0)
                GoalRow("💧", "3 giorni idratazione", ((stats["waterDays"] ?: 0) * 100 / 3).coerceIn(0,100))
                GoalRow("🏋️", "3 attività completate", ((stats["activityCount"] ?: 0) * 100 / 3).coerceIn(0,100))
                GoalRow("🥗", "3 giorni alimentazione", ((stats["foodDays"] ?: 0) * 100 / 3).coerceIn(0,100))
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🏆 Bacheca trofei", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Trophy("🥉", "Bronzo", 5, completedGoals, Modifier.weight(1f)); Trophy("🥈", "Argento", 15, completedGoals, Modifier.weight(1f)); Trophy("🥇", "Oro", 30, completedGoals, Modifier.weight(1f))
                }
                Text("Obiettivi completati: $completedGoals", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📏 Corpo & foto progressi", fontWeight = FontWeight.Bold)
                Text("Vita · addome · fianchi · torace · braccia · cosce")
                Text("La struttura è pronta per registrare misure e confrontare foto prima/dopo.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { Text("Storico peso", fontWeight = FontWeight.Bold) }
        items(weights.take(30)) { (date, kg) -> Card { Text("${formatDate(date)} · %.1f kg".format(kg), Modifier.padding(12.dp)) } }
    }
}

@Composable fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) { Card(modifier) { Column(Modifier.padding(14.dp)) { Text(label, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); sub?.let { Text(it) } } } }
@Composable fun DisciplineRow(label: String, value: Int) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("$value%", fontWeight = FontWeight.Bold) }; LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth()) } }
@Composable fun GoalRow(icon: String, label: String, value: Int) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("$icon $label"); Text("$value%") }; LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth()) } }
@Composable fun Trophy(icon: String, label: String, needed: Int, have: Int, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (have >= needed) icon else "🔒", fontSize = 28.sp); Text(label, fontWeight = FontWeight.Bold); Text("$needed obiettivi", style = MaterialTheme.typography.bodySmall) } } }

// ---------- ALLENAMENTI ----------
@Composable
fun TrainingScreen(prefs: SharedPreferences, context: Context) {
    var reminder by remember { mutableStateOf<Pair<String, LocalTime>?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Allenamenti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            ColoredCard(WakeStyle.color) {
                Text("🌅 Risveglio muscolare", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("5–10 minuti · mobilità e attivazione leggera")
                listOf("Mobilità collo e spalle · 60 sec", "Mobilità anche · 60 sec", "Marcia leggera · 90 sec", "Stretching dolce · 2 min").forEach { Text("• $it") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { markTraining(prefs, "wakeup") }) { Text("▶ Inizia / completa") }; OutlinedButton(onClick = { reminder = "Risveglio muscolare" to LocalTime.of(6,45) }) { Text("🔔") } }
            }
        }
        item {
            ColoredCard(HomeStyle.color) {
                Text("🏠 Allenamento a casa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Solo nei giorni senza palestra · pomeriggio/sera")
                listOf("💪 Total body · 25 min", "🔥 Core · 15 min", "🦵 Gambe · 20 min", "🧘 Mobilità · 15 min").forEach { label -> OutlinedButton(onClick = { markTraining(prefs, "home_${label.hashCode()}") }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
                OutlinedButton(onClick = { reminder = "Allenamento a casa" to LocalTime.of(18,0) }) { Text("🔔 Promemoria") }
            }
        }
        item {
            ColoredCard(GymStyle.color) {
                Text("🏋️ Palestra", fontWeight = FontWeight.Bold)
                Text("Quando il Calendario segna palestra, l'app non propone automaticamente l'allenamento a casa.")
                OutlinedButton(onClick = { reminder = "Palestra" to LocalTime.of(17,0) }) { Text("🔔 Promemoria") }
            }
        }
    }
    reminder?.let { (label,time) -> ReminderDialog(label, LocalDate.now(), time, context) { reminder = null } }
}

// ---------- PIANO ----------
@Composable
fun PlanScreen(prefs: SharedPreferences) {
    var target by remember { mutableStateOf(prefs.getInt("calorie_target", 1900).toString()) }
    var waterTarget by remember { mutableStateOf(prefs.getInt("water_target_ml", 2500).toString()) }
    var calculated by remember { mutableStateOf(prefs.getInt("calorie_calculated", 2300).toString()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Piano", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📋 Piano alimentare", fontWeight = FontWeight.Bold)
            Text("Il piano base rimane valido finché non lo modifichi. Ogni pasto può essere cambiato solo oggi, da oggi in poi o da una data scelta.")
            Text("Le modifiche giornaliere non cancellano lo storico.", style = MaterialTheme.typography.bodySmall)
        } } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔥 Fabbisogno calorico", fontWeight = FontWeight.Bold)
            OutlinedTextField(calculated, { calculated = it.filter(Char::isDigit) }, label = { Text("Calcolato dall'app") }, suffix = { Text("kcal") })
            OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Obiettivo impostato") }, suffix = { Text("kcal") })
            Text("Puoi sostituire il valore con quello indicato dal nutrizionista.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { prefs.edit().putInt("calorie_calculated", calculated.toIntOrNull()?.coerceIn(800,6000) ?: 2300).putInt("calorie_target", target.toIntOrNull()?.coerceIn(800,6000) ?: 1900).apply() }) { Text("Salva") }
        } } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("💧 Obiettivo idratazione", fontWeight = FontWeight.Bold)
            OutlinedTextField(waterTarget, { waterTarget = it.filter(Char::isDigit) }, label = { Text("ml al giorno") })
            Button(onClick = { prefs.edit().putInt("water_target_ml", waterTarget.toIntOrNull()?.coerceIn(500,6000) ?: 2500).apply() }) { Text("Salva") }
        } } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ℹ️ Calorie e macro", fontWeight = FontWeight.Bold)
            Text("L'app stima calorie e macronutrienti leggendo alimento + grammi. Il database integrato copre gli alimenti più comuni del piano; i valori restano stime e possono essere corretti dal nutrizionista.")
        } } }
    }
}

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
