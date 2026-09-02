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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlin.math.absoluteValue

private val Aqua = Color(0xFF32D6C5)
private val DarkBg = Color(0xFF07111E)
private val DarkCard = Color(0xFF101D2B)
private val DarkCard2 = Color(0xFF142435)
private val DarkText = Color(0xFFF6FAFF)
private val DarkMuted = Color(0xFFB8C7D9)
private val LightBg = Color(0xFFF4F7FA)
private val LightCard = Color.White
private val LightText = Color(0xFF101820)
private val LightMuted = Color(0xFF52606D)

private const val PREFS = "nutrition_v22"
private const val CHANNEL = "nutrition_reminders"

data class PlanDay(
    val date: String,
    val breakfast: String,
    val snack: String,
    val lunch: String
)

data class ReminderSettings(
    val enabled: Boolean = true,
    val daysBefore: Int = 0,
    val hoursBefore: Int = 0,
    val minutesBefore: Int = 10
)

data class MealEntry(
    val id: String,
    val date: String,
    val name: String,
    val time: String,
    val description: String,
    val optional: Boolean = false,
    val done: Boolean = false,
    val reminder: ReminderSettings = ReminderSettings()
)

data class WorkoutEntry(
    val id: Long,
    val date: String,
    val type: String,
    val title: String,
    val time: String,
    val durationText: String,
    val notes: String,
    val done: Boolean,
    val reminder: ReminderSettings
)

data class RecurringRule(
    val id: Long,
    val title: String,
    val category: String,
    val weekdays: Set<Int>,
    val time: String,
    val startDate: String,
    val endDate: String,
    val reminder: ReminderSettings
)

data class CalendarEvent(
    val id: Long,
    val date: String,
    val category: String,
    val title: String,
    val time: String,
    val durationText: String,
    val notes: String,
    val recurrence: String = "Nessuna",
    val reminder: ReminderSettings
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        val plan = loadPlan(this)
        setContent { NutritionApp(plan) }
    }
}

fun loadPlan(context: Context): List<PlanDay> {
    return try {
        val json = context.assets.open("annual_plan.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PlanDay(
                        date = o.optString("date"),
                        breakfast = o.optString("breakfast", "Colazione da definire"),
                        snack = o.optString("snack", "Spuntino da definire"),
                        lunch = o.optString("lunch", "Pranzo da definire")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun NutritionApp(plan: List<PlanDay>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var nav by rememberSaveable { mutableIntStateOf(prefs.getInt("nav", 0).coerceIn(0, 3)) }
    var menuScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var themeMode by rememberSaveable { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }

    val dark = when (themeMode) {
        "light" -> false
        "system" -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> true
    }

    val scheme = if (dark) darkColorScheme(
        primary = Aqua,
        background = DarkBg,
        surface = DarkCard,
        surfaceVariant = DarkCard2,
        onBackground = DarkText,
        onSurface = DarkText,
        onSurfaceVariant = DarkMuted
    ) else lightColorScheme(
        primary = Color(0xFF008C82),
        background = LightBg,
        surface = LightCard,
        surfaceVariant = Color(0xFFEAF0F4),
        onBackground = LightText,
        onSurface = LightText,
        onSurfaceVariant = LightMuted
    )

    LaunchedEffect(nav) { prefs.edit().putInt("nav", nav).apply() }
    NotificationPermissionRequest()

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (menuScreen == null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val tabs = listOf("🏠" to "Home", "🏋️" to "Allenamenti", "🍽️" to "Piano", "📅" to "Calendario")
                        tabs.forEachIndexed { index, p ->
                            NavigationBarItem(
                                selected = nav == index,
                                onClick = { nav = index },
                                icon = { Text(p.first, fontSize = 20.sp) },
                                label = { Text(p.second, fontSize = 10.sp, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (menuScreen != null) {
                    SecondaryScreen(
                        screen = menuScreen!!,
                        prefs = prefs,
                        context = context,
                        theme = themeMode,
                        onTheme = {
                            themeMode = it
                            prefs.edit().putString("theme", it).apply()
                        },
                        onBack = { menuScreen = null }
                    )
                } else {
                    when (nav) {
                        0 -> HomeScreen(plan, prefs, context, onMenu = { menuScreen = "menu" })
                        1 -> TrainingScreen(prefs, context)
                        2 -> PlanScreen(plan, prefs, context)
                        else -> CalendarScreen(prefs, context)
                    }
                }
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

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
        trailing?.invoke()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun CircleCheck(done: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clickable { onClick() },
        shape = CircleShape,
        color = if (done) Aqua.copy(alpha = .18f) else Color.Transparent,
        border = BorderStroke(2.dp, if (done) Aqua else MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("✓", color = if (done) Aqua else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

private fun defaultMealsFor(date: LocalDate, plan: List<PlanDay>, prefs: SharedPreferences): List<MealEntry> {
    val p = plan.firstOrNull { it.date == date.toString() }
    val stored = loadMeals(prefs, date)
    if (stored.isNotEmpty()) return stored

    val base = mutableListOf(
        MealEntry("${date}_breakfast", date.toString(), "🍳 Colazione", "07:00", p?.breakfast ?: "Yogurt greco, avena e frutta"),
        MealEntry("${date}_snack_am", date.toString(), "🥤 Spuntino mattutino", "10:30", p?.snack ?: "Shake proteico"),
        MealEntry("${date}_lunch", date.toString(), "🍽️ Pranzo", "14:00", p?.lunch ?: "Pollo, verdure e couscous"),
        MealEntry("${date}_snack_pm", date.toString(), "🍎 Spuntino pomeridiano", "16:30", "Frutta e mandorle")
    )

    val dinnerEnabled = prefs.getBoolean("dinner_enabled_${date}", false)
    if (dinnerEnabled) {
        base += MealEntry(
            "${date}_dinner",
            date.toString(),
            "🍲 Cena",
            prefs.getString("dinner_time_${date}", "20:00") ?: "20:00",
            prefs.getString("dinner_desc_${date}", "Cena da definire") ?: "Cena da definire",
            optional = true
        )
    }
    return base
}

@Composable
fun HomeScreen(plan: List<PlanDay>, prefs: SharedPreferences, context: Context, onMenu: () -> Unit) {
    val today = LocalDate.now()
    val name = prefs.getString("profile_name", "Alessandro") ?: "Alessandro"
    var meals by remember(today) { mutableStateOf(defaultMealsFor(today, plan, prefs)) }
    var editingMeal by remember { mutableStateOf<MealEntry?>(null) }
    var reminderMeal by remember { mutableStateOf<MealEntry?>(null) }
    var addingDinner by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var editingHomeWorkout by remember { mutableStateOf<WorkoutEntry?>(null) }
    var editingHomePersonal by remember { mutableStateOf<CalendarEvent?>(null) }
    val showNutrition = prefs.getBoolean("home_show_nutrition", true)
    val showWorkouts = prefs.getBoolean("home_show_workouts", true)
    val showPersonal = prefs.getBoolean("home_show_personal", true)
    val showDailyReport = prefs.getBoolean("home_show_report", true)

    editingHomeWorkout?.let { workout ->
        WorkoutEditor(
            initial = workout,
            onDismiss = { editingHomeWorkout = null },
            onSave = { saved ->
                val all = loadWorkouts(prefs).map { if (it.id == saved.id) saved else it }
                saveWorkouts(prefs, all)
                if (saved.reminder.enabled) scheduleIfNeeded(context, saved.title, LocalDate.parse(saved.date), parseTime(saved.time), saved.reminder, saved.id.toInt())
                else cancelReminder(context, saved.id.toInt())
                editingHomeWorkout = null
            },
            onDelete = {
                saveWorkouts(prefs, loadWorkouts(prefs).filterNot { it.id == workout.id })
                cancelReminder(context, workout.id.toInt())
                editingHomeWorkout = null
            }
        )
        return
    }

    editingHomePersonal?.let { ev ->
        CalendarEventEditor(
            date = runCatching { LocalDate.parse(ev.date) }.getOrDefault(today),
            initial = ev,
            onDismiss = { editingHomePersonal = null },
            onSave = { saved ->
                val all = loadEvents(prefs).map { if (it.id == saved.id) saved else it }
                saveEvents(prefs, all)
                if (saved.reminder.enabled) scheduleIfNeeded(context, saved.title, LocalDate.parse(saved.date), parseTime(saved.time), saved.reminder, saved.id.toInt())
                else cancelReminder(context, saved.id.toInt())
                editingHomePersonal = null
            },
            onDelete = {
                saveEvents(prefs, loadEvents(prefs).filterNot { it.id == ev.id })
                cancelReminder(context, ev.id.toInt())
                editingHomePersonal = null
            }
        )
        return
    }

    if (showReport) {
        DailyReportScreen(prefs, today, onBack = { showReport = false })
        return
    }

    reminderMeal?.let { meal ->
        ReminderOnlyScreen(
            title = meal.name,
            initial = meal.reminder,
            onBack = { reminderMeal = null },
            onSave = { newReminder ->
                val updated = meals.map { if (it.id == meal.id) it.copy(reminder = newReminder) else it }
                meals = updated
                saveMeals(prefs, today, updated)
                if (newReminder.enabled) scheduleIfNeeded(context, meal.name, today, parseTime(meal.time), newReminder, meal.id.hashCode())
                else cancelReminder(context, meal.id.hashCode())
                reminderMeal = null
            }
        )
        return
    }

    if (editingMeal != null || addingDinner) {
        MealEditor(
            initial = editingMeal,
            date = today,
            isDinner = addingDinner || editingMeal?.name?.contains("Cena") == true,
            onDismiss = { editingMeal = null; addingDinner = false },
            onSave = { saved ->
                val current = defaultMealsFor(today, plan, prefs).toMutableList()
                val updated = if (current.any { it.id == saved.id }) {
                    current.map { if (it.id == saved.id) saved else it }
                } else current + saved
                saveMeals(prefs, today, updated)
                if (saved.name.contains("Cena")) {
                    prefs.edit()
                        .putBoolean("dinner_enabled_${today}", true)
                        .putString("dinner_time_${today}", saved.time)
                        .putString("dinner_desc_${today}", saved.description)
                        .apply()
                }
                scheduleIfNeeded(context, saved.name, today, parseTime(saved.time), saved.reminder, saved.id.hashCode())
                meals = updated
                editingMeal = null
                addingDinner = false
            },
            onDelete = editingMeal?.let { meal ->
                {
                    val updated = defaultMealsFor(today, plan, prefs).filterNot { it.id == meal.id }
                    saveMeals(prefs, today, updated)
                    if (meal.name.contains("Cena")) {
                        prefs.edit().putBoolean("dinner_enabled_${today}", false).apply()
                    }
                    meals = updated
                    editingMeal = null
                }
            }
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle(
                "Ciao $name 👋",
                "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)}"
            ) {
                OutlinedButton(onClick = onMenu, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("☰ Menu") }
            }
        }

        if (showNutrition) {
            item {
                AppCard {
                    Text("🍽️ Alimentazione di oggi", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Usa ✏️ per modificare e 🔔 per il promemoria. La cena è facoltativa.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(meals, key = { it.id }) { meal ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(meal.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { editingMeal = meal }) { Text("✏️") }
                                IconButton(onClick = { reminderMeal = meal }) { Text(if (meal.reminder.enabled) "🔔" else "🔕") }
                            }
                            Text("${meal.time} · ${meal.description}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                if (meal.reminder.enabled) "Promemoria: ${reminderLabel(meal.reminder)}" else "Promemoria disattivato",
                                color = if (meal.reminder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            if (meal.optional) Text("Facoltativa", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                        CircleCheck(meal.done) {
                            val updated = meals.map { if (it.id == meal.id) it.copy(done = !it.done) else it }
                            meals = updated
                            saveMeals(prefs, today, updated)
                        }
                    }
                }
            }

            if (meals.none { it.name.contains("Cena") }) {
                item {
                    OutlinedButton(onClick = { addingDinner = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ Aggiungi cena facoltativa")
                    }
                }
            }
        }

        if (showWorkouts) {
            item {
                AppCard {
                    Text("🏋️ Attività di oggi", fontWeight = FontWeight.ExtraBold)
                    val workouts = loadWorkouts(prefs).filter { it.date == today.toString() }
                    if (workouts.isEmpty()) {
                        Text("Nessun allenamento programmato oggi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        workouts.forEach { w ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${categoryEmoji(w.type)} ${w.time} · ${w.title}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { editingHomeWorkout = w }) { Text("✏️") }
                            }
                        }
                    }
                }
            }
        }

        if (showPersonal) {
            item {
                AppCard {
                    Text("🌟 Vita personale", fontWeight = FontWeight.ExtraBold)
                    val events = loadEvents(prefs).filter { it.date == today.toString() && it.category.contains("personale", true) }
                    if (events.isEmpty()) Text("Nessun impegno personale oggi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else events.forEach { ev ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${ev.time} · ${ev.title}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { editingHomePersonal = ev }) { Text("✏️") }
                        }
                    }
                }
            }
        }

        if (showDailyReport) {
            item {
                AppCard {
                    Text("📝 Resoconto della giornata", fontWeight = FontWeight.ExtraBold)
                    Text("Compila alimentazione, idratazione, attività, umore e note.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showReport = true }, modifier = Modifier.fillMaxWidth()) { Text("Compila resoconto") }
                }
            }
        }
    }
}

@Composable
fun MealEditor(
    initial: MealEntry?,
    date: LocalDate,
    isDinner: Boolean,
    onDismiss: () -> Unit,
    onSave: (MealEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: if (isDinner) "🍲 Cena" else "Pasto") }
    var time by remember { mutableStateOf(initial?.time ?: if (isDinner) "20:00" else "12:00") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderSettings()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle(if (initial == null) "＋ Nuovo pasto" else "✏️ Modifica pasto", formatDateFull(date)) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
        item {
            AppCard {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Ora HH:MM") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrizione / alimenti") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                if (isDinner) Text("La cena resta facoltativa: compare solo quando la aggiungi.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
        item { ReminderEditor(reminder) { reminder = it } }
        item {
            Button(
                onClick = {
                    onSave(
                        MealEntry(
                            id = initial?.id ?: "${date}_${System.currentTimeMillis()}",
                            date = date.toString(),
                            name = name.ifBlank { if (isDinner) "🍲 Cena" else "Pasto" },
                            time = time,
                            description = description.ifBlank { "Da definire" },
                            optional = isDinner,
                            done = initial?.done ?: false,
                            reminder = reminder
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva") }
        }
        if (onDelete != null) {
            item {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Elimina") }
            }
        }
    }
}

@Composable
fun PlanScreen(plan: List<PlanDay>, prefs: SharedPreferences, context: Context) {
    var week by rememberSaveable { mutableIntStateOf(1) }
    var day by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var editingMeal by remember { mutableStateOf<MealEntry?>(null) }
    var reminderMeal by remember { mutableStateOf<MealEntry?>(null) }
    var addingDinner by remember { mutableStateOf(false) }

    val names = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val startRaw = plan.firstOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    val start = startRaw.minusDays((startRaw.dayOfWeek.value - 1).toLong())
    val date = start.plusDays(((week - 1) * 7 + day).toLong())
    val sundayFree = prefs.getBoolean("sunday_free", true)
    val meals = remember(date, refresh) { defaultMealsFor(date, plan, prefs) }

    reminderMeal?.let { meal ->
        ReminderOnlyScreen(
            title = meal.name,
            initial = meal.reminder,
            onBack = { reminderMeal = null },
            onSave = { newReminder ->
                val updated = meals.map { if (it.id == meal.id) it.copy(reminder = newReminder) else it }
                saveMeals(prefs, date, updated)
                if (newReminder.enabled) scheduleIfNeeded(context, meal.name, date, parseTime(meal.time), newReminder, meal.id.hashCode())
                else cancelReminder(context, meal.id.hashCode())
                refresh++
                reminderMeal = null
            }
        )
        return
    }

    if (editingMeal != null || addingDinner) {
        MealEditor(
            initial = editingMeal,
            date = date,
            isDinner = addingDinner || editingMeal?.name?.contains("Cena") == true,
            onDismiss = { editingMeal = null; addingDinner = false },
            onSave = { saved ->
                val current = defaultMealsFor(date, plan, prefs)
                val updated = if (current.any { it.id == saved.id }) {
                    current.map { if (it.id == saved.id) saved else it }
                } else current + saved
                saveMeals(prefs, date, updated)
                if (saved.name.contains("Cena")) {
                    prefs.edit()
                        .putBoolean("dinner_enabled_${date}", true)
                        .putString("dinner_time_${date}", saved.time)
                        .putString("dinner_desc_${date}", saved.description)
                        .apply()
                }
                scheduleIfNeeded(context, saved.name, date, parseTime(saved.time), saved.reminder, saved.id.hashCode())
                refresh++
                editingMeal = null
                addingDinner = false
            },
            onDelete = editingMeal?.let { meal ->
                {
                    val updated = defaultMealsFor(date, plan, prefs).filterNot { it.id == meal.id }
                    saveMeals(prefs, date, updated)
                    if (meal.name.contains("Cena")) prefs.edit().putBoolean("dinner_enabled_${date}", false).apply()
                    refresh++
                    editingMeal = null
                }
            }
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🍽️ Piano alimentare", "Settimana completa da lunedì a domenica") }

        item {
            AppCard {
                Text("1 · Seleziona la settimana", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { w ->
                        FilterChip(
                            selected = week == w,
                            onClick = { week = w },
                            label = { Text("Set. $w") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            AppCard {
                Text("2 · Seleziona il giorno", fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Lun","Mar","Mer","Gio").forEachIndexed { i, d ->
                            FilterChip(selected = day == i, onClick = { day = i }, label = { Text(d) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Ven","Sab","Dom").forEachIndexed { j, d ->
                            val i = j + 4
                            FilterChip(selected = day == i, onClick = { day = i }, label = { Text(d) }, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text("Settimana $week · ${names[day]}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(formatDateFull(date), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🌴 Domenica libera / sgarro", fontWeight = FontWeight.Bold)
                            Text("Puoi lasciarla libera oppure usare normalmente il piano.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sundayFree,
                            onCheckedChange = { prefs.edit().putBoolean("sunday_free", it).apply(); refresh++ }
                        )
                    }
                }
            }
        }

        if (!(date.dayOfWeek == DayOfWeek.SUNDAY && sundayFree)) {
            items(meals, key = { it.id }) { m ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text("${m.time} · ${m.description}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (m.reminder.enabled) "Promemoria: ${reminderLabel(m.reminder)}" else "Promemoria disattivato", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { editingMeal = m }) { Text("✏️") }
                        IconButton(onClick = { reminderMeal = m }) { Text(if (m.reminder.enabled) "🔔" else "🔕") }
                    }
                }
            }

            if (meals.none { it.name.contains("Cena") }) {
                item {
                    OutlinedButton(onClick = { addingDinner = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ Aggiungi cena facoltativa")
                    }
                }
            }
        } else {
            item {
                AppCard {
                    Text("Domenica libera", fontWeight = FontWeight.Bold)
                    Text("Non viene imposto alcun pasto. Se vuoi puoi disattivare la modalità libera qui sopra.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TrainingScreen(prefs: SharedPreferences, context: Context) {
    var workouts by remember { mutableStateOf(loadWorkouts(prefs)) }
    var editing by remember { mutableStateOf<WorkoutEntry?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    if (editing != null || showNew) {
        WorkoutEditor(
            initial = editing,
            onDismiss = { editing = null; showNew = false },
            onSave = { w ->
                workouts = if (editing == null) workouts + w else workouts.map { if (it.id == w.id) w else it }
                saveWorkouts(prefs, workouts)
                scheduleIfNeeded(context, w.title, LocalDate.parse(w.date), parseTime(w.time), w.reminder, w.id.toInt())
                editing = null
                showNew = false
            },
            onDelete = editing?.let { current ->
                {
                    workouts = workouts.filterNot { it.id == current.id }
                    saveWorkouts(prefs, workouts)
                    editing = null
                }
            }
        )
        return
    }

    if (showRules) {
        RecurringRulesScreen(prefs, context, onBack = { showRules = false })
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle("🏋️ Allenamenti", "Tocca una scheda per modificarla") {
                IconButton(onClick = { showNew = true }) { Text("＋", fontSize = 28.sp) }
            }
        }

        if (workouts.isEmpty()) {
            item {
                AppCard {
                    Text("Nessun allenamento registrato", fontWeight = FontWeight.Bold)
                    Button(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi allenamento") }
                }
            }
        } else {
            items(workouts.sortedByDescending { it.date }) { w ->
                AppCard(modifier = Modifier.clickable { editing = w }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🏋️ ${w.title}", fontWeight = FontWeight.Bold)
                            Text("${formatDateShort(w.date)} · ${w.time} · ${w.durationText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (w.reminder.enabled) "🔔 ${reminderLabel(w.reminder)}" else "🔕 Promemoria disattivato", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        CircleCheck(w.done) {
                            val updated = w.copy(done = !w.done)
                            workouts = workouts.map { if (it.id == w.id) updated else it }
                            saveWorkouts(prefs, workouts)
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                Text("🔁 Programmazione fissa", fontWeight = FontWeight.ExtraBold)
                Button(onClick = { showRules = true }, modifier = Modifier.fillMaxWidth()) { Text("Gestisci programmazioni ricorrenti") }
            }
        }
    }
}

@Composable
fun WorkoutEditor(
    initial: WorkoutEntry?,
    onDismiss: () -> Unit,
    onSave: (WorkoutEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    var type by remember { mutableStateOf(initial?.type ?: "Total body") }
    var title by remember { mutableStateOf(initial?.title ?: "Total body") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var time by remember { mutableStateOf(initial?.time ?: "17:00") }
    var duration by remember { mutableStateOf(initial?.durationText ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderSettings()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle(if (initial == null) "＋ Nuovo allenamento" else "✏️ Modifica allenamento") {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
        item {
            AppCard {
                SimplePicker(type, listOf("Total body", "Forza", "Cardio", "CrossFit", "Camminata", "Mobilità", "Altro")) { type = it }
                OutlinedTextField(title, { title = it }, label = { Text("Nome allenamento") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it }, label = { Text("Tempo impiegato") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Data AAAA-MM-GG") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(time, { time = it }, label = { Text("Ora HH:MM") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        }
        item { ReminderEditor(reminder) { reminder = it } }
        item {
            Button(
                onClick = {
                    onSave(
                        WorkoutEntry(
                            initial?.id ?: System.currentTimeMillis(),
                            date,
                            type,
                            title.ifBlank { type },
                            time,
                            duration.ifBlank { "Tempo non indicato" },
                            notes,
                            initial?.done ?: false,
                            reminder
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva allenamento") }
        }
        if (onDelete != null) item { OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Elimina") } }
    }
}

@Composable
fun RecurringRulesScreen(prefs: SharedPreferences, context: Context, onBack: () -> Unit) {
    var rules by remember { mutableStateOf(loadRules(prefs)) }
    var editing by remember { mutableStateOf<RecurringRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        RuleEditor(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { r ->
                rules = if (editing == null) rules + r else rules.map { if (it.id == r.id) r else it }
                saveRules(prefs, rules)
                scheduleNextOccurrence(context, r)
                creating = false
                editing = null
            },
            onDelete = editing?.let { current ->
                {
                    rules = rules.filterNot { it.id == current.id }
                    saveRules(prefs, rules)
                    editing = null
                }
            }
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle("🔁 Programmazioni ricorrenti", "Tocca una programmazione per modificarla") {
                TextButton(onClick = onBack) { Text("‹ Indietro") }
            }
        }
        items(rules) { r ->
            AppCard(modifier = Modifier.clickable { editing = r }) {
                Text("${categoryEmoji(r.category)} ${r.title}", fontWeight = FontWeight.Bold)
                Text("${weekdayLabel(r.weekdays)} · ${r.time}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (r.reminder.enabled) "🔔 ${reminderLabel(r.reminder)}" else "🔕 Promemoria disattivato", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Nuova programmazione ricorrente") } }
    }
}

@Composable
fun RuleEditor(
    initial: RecurringRule?,
    onDismiss: () -> Unit,
    onSave: (RecurringRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    var category by remember { mutableStateOf(initial?.category ?: "Allenamento") }
    var title by remember { mutableStateOf(initial?.title ?: "Palestra") }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: setOf(DayOfWeek.TUESDAY.value)) }
    var time by remember { mutableStateOf(initial?.time ?: "17:00") }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now().toString()) }
    var end by remember { mutableStateOf(initial?.endDate ?: LocalDate.now().plusMonths(6).toString()) }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderSettings()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle(if (initial == null) "＋ Nuova programmazione" else "✏️ Modifica programmazione") {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
        item {
            AppCard {
                SimplePicker(category, listOf("Allenamento", "Camminata", "Giornata libera", "Appuntamento", "Attività personale", "Idratazione", "Sonno", "Altro")) { category = it }
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                Text("Giorni della settimana", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("L","M","M","G","V","S","D").forEachIndexed { i, label ->
                        val d = i + 1
                        FilterChip(
                            selected = d in weekdays,
                            onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(time, { time = it }, label = { Text("Ora HH:MM") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Dal") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Al") }, modifier = Modifier.weight(1f))
                }
            }
        }
        item { ReminderEditor(reminder) { reminder = it } }
        item {
            Button(
                onClick = { onSave(RecurringRule(initial?.id ?: System.currentTimeMillis(), title, category, weekdays, time, start, end, reminder)) },
                enabled = weekdays.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva programmazione") }
        }
        if (onDelete != null) item { OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Elimina") } }
    }
}

@Composable
fun ReminderEditor(settings: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.enabled, onCheckedChange = { onChange(settings.copy(enabled = it)) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text("🔔 Promemoria e notifica", fontWeight = FontWeight.Bold)
                Text("Attivabile/disattivabile per questo elemento", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        if (settings.enabled) {
            Text("Scegli liberamente quanto prima ricevere l'avviso", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FreeNumberField("Giorni", settings.daysBefore) { onChange(settings.copy(daysBefore = it)) }
                FreeNumberField("Ore", settings.hoursBefore) { onChange(settings.copy(hoursBefore = it)) }
                FreeNumberField("Minuti", settings.minutesBefore) { onChange(settings.copy(minutesBefore = it)) }
            }
        }
    }
}


@Composable
fun RowScope.FreeNumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(Modifier.weight(1f)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() }.take(5)
                text = clean
                clean.toIntOrNull()?.let { onChange(it.coerceAtLeast(0)) }
                if (clean.isEmpty()) onChange(0)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun RowScope.NumberPickerField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.weight(1f)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value.toString()) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                range.forEach { n ->
                    DropdownMenuItem(text = { Text(n.toString()) }, onClick = { onChange(n); expanded = false })
                }
            }
        }
    }
}

@Composable
fun SimplePicker(value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value, modifier = Modifier.weight(1f))
            Text("▼")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); expanded = false }) }
        }
    }
}

@Composable
fun CalendarScreen(prefs: SharedPreferences, context: Context) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var events by remember { mutableStateOf(loadEvents(prefs)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }

    if (editing != null || addDate != null) {
        val targetDate = editing?.date?.let { LocalDate.parse(it) } ?: addDate!!
        CalendarEventEditor(
            date = targetDate,
            initial = editing,
            onDismiss = { editing = null; addDate = null },
            onSave = { ev ->
                events = if (editing == null) events + ev else events.map { if (it.id == ev.id) ev else it }
                saveEvents(prefs, events)
                scheduleIfNeeded(context, ev.title, LocalDate.parse(ev.date), parseTime(ev.time), ev.reminder, ev.id.toInt())
                editing = null
                addDate = null
                selectedDate = LocalDate.parse(ev.date)
            },
            onDelete = editing?.let { current ->
                {
                    events = events.filterNot { it.id == current.id }
                    saveEvents(prefs, events)
                    editing = null
                }
            }
        )
        return
    }

    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    val cells = List(offset) { null } + (1..total).map { month.atDay(it) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("📅 Calendario", "Tocca un evento per modificarlo") }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Text("‹", fontSize = 30.sp) }
                Text("${month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} ${month.year}", fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = { month = month.plusMonths(1) }) { Text("›", fontSize = 30.sp) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L","M","M","G","V","S","D").forEach {
                        Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                cells.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { d ->
                            if (d == null) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                CalendarDayCell(
                                    date = d,
                                    events = events.filter { it.date == d.toString() },
                                    modifier = Modifier.weight(1f),
                                    onAdd = { addDate = d },
                                    onSelect = { selectedDate = d }
                                )
                            }
                        }
                        repeat(7 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                    }
                }
            }
        }

        selectedDate?.let { d ->
            item {
                AppCard {
                    Text(formatDateFull(d), fontWeight = FontWeight.ExtraBold)
                    val list = events.filter { it.date == d.toString() }.sortedBy { it.time }
                    if (list.isEmpty()) {
                        Text("Nessun impegno programmato", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        list.forEach { ev ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { editing = ev },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("${categoryEmoji(ev.category)} ${ev.time} · ${ev.title}", fontWeight = FontWeight.Bold)
                                    if (ev.durationText.isNotBlank()) Text(ev.durationText, fontSize = 12.sp)
                                    if (ev.notes.isNotBlank()) Text(ev.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    if (ev.recurrence != "Nessuna") Text("🔁 ${ev.recurrence}", fontSize = 12.sp)
                                    Text(if (ev.reminder.enabled) "🔔 ${reminderLabel(ev.reminder)}" else "🔕 Promemoria disattivato", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    Text("Tocca per modificare", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Button(onClick = { addDate = d }, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi a questo giorno") }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    date: LocalDate,
    events: List<CalendarEvent>,
    modifier: Modifier,
    onAdd: () -> Unit,
    onSelect: () -> Unit
) {
    Surface(
        modifier = modifier.aspectRatio(.92f).clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier.size(26.dp).clickable { onAdd() },
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("＋", fontSize = 16.sp) }
                }
            }
            events.take(2).forEach { Text("${categoryEmoji(it.category)} ${it.title}", fontSize = 9.sp, maxLines = 1) }
            if (events.size > 2) Text("+${events.size - 2} altri", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
        }
    }
}

@Composable
fun CalendarEventEditor(
    date: LocalDate,
    initial: CalendarEvent?,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (() -> Unit)?
) {
    var eventDate by remember { mutableStateOf(initial?.date ?: date.toString()) }
    var category by remember { mutableStateOf(initial?.category ?: "Impegno / Appuntamento") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var time by remember { mutableStateOf(initial?.time ?: "10:00") }
    var duration by remember { mutableStateOf(initial?.durationText ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var recurrence by remember { mutableStateOf(initial?.recurrence ?: "Nessuna") }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderSettings()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle(if (initial == null) "＋ Aggiungi impegno" else "✏️ Modifica impegno", formatDateFull(runCatching { LocalDate.parse(eventDate) }.getOrDefault(date))) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }

        item {
            AppCard {
                SimplePicker(category, listOf("Impegno / Appuntamento", "Allenamento", "Attività personale", "Spesa", "Idratazione", "Sonno", "Giornata libera", "Altro")) { category = it }
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(eventDate, { eventDate = it }, label = { Text("Data AAAA-MM-GG") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Ora HH:MM") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it }, label = { Text("Durata / tempo (opzionale)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Ricorrenza", fontWeight = FontWeight.Bold)
                SimplePicker(recurrence, listOf("Nessuna", "Ogni giorno", "Ogni settimana", "Ogni mese", "Ogni anno")) { recurrence = it }
            }
        }

        item { ReminderEditor(reminder) { reminder = it } }

        item {
            Button(
                onClick = {
                    onSave(
                        CalendarEvent(
                            id = initial?.id ?: System.currentTimeMillis(),
                            date = eventDate,
                            category = category,
                            title = title.ifBlank { category },
                            time = time,
                            durationText = duration,
                            notes = notes,
                            recurrence = recurrence,
                            reminder = reminder
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (initial == null) "Salva nel calendario" else "Salva modifiche") }
        }

        if (onDelete != null) {
            item { OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Elimina evento") } }
        }
    }
}

@Composable
fun SecondaryScreen(screen: String, prefs: SharedPreferences, context: Context, theme: String, onTheme: (String) -> Unit, onBack: () -> Unit) {
    var target by rememberSaveable(screen) { mutableStateOf(if (screen == "menu") "menu" else screen) }
    if (target != "menu") {
        when (target) {
            "profile" -> ProfileScreen(prefs, onGoals = { target = "goals" }, onCustomizeHome = { target = "homecustom" }, onBack = { target = "menu" })
            "settings" -> SettingsScreen(theme, onTheme, onBack = { target = "menu" })
            "goals" -> GoalsScreen(prefs, onBack = { target = "menu" })
            "homecustom" -> HomeCustomizeScreen(prefs, onBack = { target = "profile" })
            "personal" -> PersonalLifeScreen(prefs, context, onBack = { target = "menu" })
            "journal" -> DailyReportScreen(prefs, LocalDate.now(), onBack = { target = "menu" })
            "sleep" -> SleepScreen(prefs, onBack = { target = "menu" })
            "weight" -> WeightScreen(prefs, onBack = { target = "menu" })
            "water" -> WaterScreen(prefs, onBack = { target = "menu" })
            "pt" -> ProfessionalScreen(prefs, "pt", "💪 Personal Trainer", onBack = { target = "menu" })
            "nutritionist" -> ProfessionalScreen(prefs, "nutritionist", "🥗 Nutrizionista", onBack = { target = "menu" })
            "shopping" -> ShoppingScreen(prefs, onBack = { target = "menu" })
            "agenda" -> CalendarScreen(prefs, context)
            "widgets" -> WidgetSettingsScreen(prefs, onBack = { target = "menu" })
            else -> SettingsScreen(theme, onTheme, onBack = { target = "menu" })
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("☰ Menu") { TextButton(onClick = onBack) { Text("Chiudi") } } }
        item { MenuSection("🧭 Il mio percorso") }
        item { MenuButton("👤 Profilo e percorso") { target = "profile" } }
        item { MenuButton("🎯 Obiettivi e trofei") { target = "goals" } }
        item { MenuButton("📝 Resoconto giornaliero") { target = "journal" } }
        item { MenuButton("😴 Sonno") { target = "sleep" } }
        item { MenuButton("⚖️ Peso e misure") { target = "weight" } }
        item { MenuButton("💧 Idratazione") { target = "water" } }

        item { MenuSection("🌟 Vita personale") }
        item { MenuButton("🌟 Impegni personali") { target = "personal" } }

        item { MenuSection("👨‍⚕️ Professionisti") }
        item { MenuButton("🥗 Nutrizionista") { target = "nutritionist" } }
        item { MenuButton("💪 Personal Trainer") { target = "pt" } }

        item { MenuSection("🛒 Organizzazione") }
        item { MenuButton("🛒 Lista della spesa") { target = "shopping" } }

        item { MenuSection("⚙️ App") }
        item { MenuButton("🧩 Widget") { target = "widgets" } }
        item { MenuButton("⚙️ Impostazioni") { target = "settings" } }
    }
}

@Composable fun MenuSection(text: String) { Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp)) }
@Composable fun MenuButton(text: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileScreen(prefs: SharedPreferences, onGoals: () -> Unit, onCustomizeHome: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(prefs.getString("profile_name", "Alessandro") ?: "Alessandro") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🧭 Il mio percorso") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { prefs.edit().putString("profile_name", name).apply() }, modifier = Modifier.fillMaxWidth()) { Text("Salva profilo") }
        } }
        item {
            AppCard {
                Text("Questa sezione contiene solo i dati del tuo percorso personale.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                MenuButton("🏠 Personalizza Home") { onCustomizeHome() }
            }
        }
    }
}

@Composable
fun SettingsScreen(theme: String, onTheme: (String) -> Unit, onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("⚙️ Impostazioni") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard {
            Text("Aspetto", fontWeight = FontWeight.Bold)
            listOf("dark" to "🌙 Scuro", "light" to "☀️ Chiaro", "system" to "📱 Automatico").forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == p.first, onClick = { onTheme(p.first) })
                    Text(p.second)
                }
            }
        } }
    }
}

@Composable
fun ReminderOnlyScreen(title: String, initial: ReminderSettings, onBack: () -> Unit, onSave: (ReminderSettings) -> Unit) {
    var reminder by remember { mutableStateOf(initial) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🔔 Promemoria", title) { TextButton(onClick = onBack) { Text("Annulla") } } }
        item { ReminderEditor(reminder) { reminder = it } }
        item { Button(onClick = { onSave(reminder) }, modifier = Modifier.fillMaxWidth()) { Text("Salva promemoria") } }
    }
}

@Composable
fun DailyReportScreen(prefs: SharedPreferences, date: LocalDate, onBack: () -> Unit) {
    val key = "report_${date}"
    val saved = remember { runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject()) }
    var food by remember { mutableStateOf(saved.optBoolean("food")) }
    var water by remember { mutableStateOf(saved.optBoolean("water")) }
    var workout by remember { mutableStateOf(saved.optBoolean("workout")) }
    var walk by remember { mutableStateOf(saved.optBoolean("walk")) }
    var mood by remember { mutableIntStateOf(saved.optInt("mood", 3).coerceIn(1,5)) }
    var note by remember { mutableStateOf(saved.optString("note")) }
    var savedMsg by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("📝 Resoconto della giornata", formatDateFull(date)) { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard {
            SelectableRow("🍽️ Ho seguito l'alimentazione", food) { food = it }
            SelectableRow("💧 Idratazione completata", water) { water = it }
            SelectableRow("🏋️ Allenamento fatto", workout) { workout = it }
            SelectableRow("🚶 Camminata fatta", walk) { walk = it }
            Text("Umore: $mood/5", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..5).forEach { n -> FilterChip(selected = mood == n, onClick = { mood = n }, label = { Text(n.toString()) }) }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Nota libera") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        } }
        item { Button(onClick = {
            prefs.edit().putString(key, JSONObject().put("food",food).put("water",water).put("workout",workout).put("walk",walk).put("mood",mood).put("note",note).toString()).apply()
            savedMsg = true
        }, modifier = Modifier.fillMaxWidth()) { Text("Salva resoconto") } }
        if (savedMsg) item { Text("✓ Resoconto salvato", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SelectableRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GoalsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var goals by remember { mutableStateOf(loadSimpleItems(prefs, "goals_items")) }
    var text by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🎯 Obiettivi e trofei") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard {
            OutlinedTextField(text, { text = it }, label = { Text("Nuovo obiettivo") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (text.isNotBlank()) { goals = goals + SimpleItem(System.currentTimeMillis(), text.trim(), false, ""); saveSimpleItems(prefs,"goals_items",goals); text="" } }, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi obiettivo") }
        } }
        if (goals.isEmpty()) item { Text("Nessun obiettivo ancora.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(goals, key = { it.id }) { g -> AppCard { Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(g.done, { v -> goals = goals.map { if(it.id==g.id) it.copy(done=v) else it }; saveSimpleItems(prefs,"goals_items",goals) })
            Text(g.text, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onClick = { goals = goals.filterNot { it.id==g.id }; saveSimpleItems(prefs,"goals_items",goals) }) { Text("🗑️") }
        } } }
    }
}

@Composable
fun HomeCustomizeScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var nutrition by remember { mutableStateOf(prefs.getBoolean("home_show_nutrition", true)) }
    var workouts by remember { mutableStateOf(prefs.getBoolean("home_show_workouts", true)) }
    var personal by remember { mutableStateOf(prefs.getBoolean("home_show_personal", true)) }
    var report by remember { mutableStateOf(prefs.getBoolean("home_show_report", true)) }
    fun save() { prefs.edit().putBoolean("home_show_nutrition",nutrition).putBoolean("home_show_workouts",workouts).putBoolean("home_show_personal",personal).putBoolean("home_show_report",report).apply() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🏠 Personalizza Home") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard {
            ToggleRow("🍽️ Alimentazione", nutrition) { nutrition=it; save() }
            ToggleRow("🏋️ Allenamenti", workouts) { workouts=it; save() }
            ToggleRow("🌟 Vita personale", personal) { personal=it; save() }
            ToggleRow("📝 Resoconto giornaliero", report) { report=it; save() }
        } }
    }
}

@Composable fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}

@Composable
fun PersonalLifeScreen(prefs: SharedPreferences, context: Context, onBack: () -> Unit) {
    var events by remember { mutableStateOf(loadEvents(prefs).filter { it.category.contains("personale", true) }) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    if (creating || editing != null) {
        CalendarEventEditor(LocalDate.now(), editing, onDismiss = { creating=false; editing=null }, onSave = { ev0 ->
            val ev = ev0.copy(category="Attività personale")
            val all = loadEvents(prefs).filterNot { it.id==ev.id } + ev
            saveEvents(prefs, all); if(ev.reminder.enabled) scheduleIfNeeded(context,ev.title,LocalDate.parse(ev.date),parseTime(ev.time),ev.reminder,ev.id.toInt())
            events = all.filter { it.category.contains("personale", true) }; creating=false; editing=null
        }, onDelete = editing?.let { cur -> { val all=loadEvents(prefs).filterNot{it.id==cur.id}; saveEvents(prefs,all); events=all.filter{it.category.contains("personale",true)}; editing=null } })
        return
    }
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🌟 Vita personale") { Row { TextButton(onClick=onBack){Text("‹ Indietro")}; IconButton(onClick={creating=true}){Text("＋",fontSize=28.sp)} } } }
        item { Button(onClick={creating=true}, modifier=Modifier.fillMaxWidth()){Text("＋ Aggiungi impegno personale")} }
        if(events.isEmpty()) item { Text("Nessun impegno personale.", color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(events.sortedByDescending{it.date}) { ev -> AppCard(modifier=Modifier.clickable{editing=ev}) { Text("${ev.date} · ${ev.time}", color=MaterialTheme.colorScheme.onSurfaceVariant); Text(ev.title,fontWeight=FontWeight.Bold); Text("✏️ Tocca per modificare", color=MaterialTheme.colorScheme.primary,fontSize=12.sp) } }
    }
}

@Composable
fun SleepScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val date=LocalDate.now(); val k="sleep_${date}"; val j=remember{runCatching{JSONObject(prefs.getString(k,"{}")?:"{}")} .getOrDefault(JSONObject())}
    var bed by remember{mutableStateOf(j.optString("bed","23:00"))}; var wake by remember{mutableStateOf(j.optString("wake","07:00"))}; var quality by remember{mutableIntStateOf(j.optInt("quality",3).coerceIn(1,5))}; var msg by remember{mutableStateOf(false)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ScreenTitle("😴 Sonno",formatDateFull(date)){TextButton(onClick=onBack){Text("‹ Indietro")}}}
        item{AppCard{ OutlinedTextField(bed,{bed=it},label={Text("Ora a letto HH:MM")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(wake,{wake=it},label={Text("Ora risveglio HH:MM")},modifier=Modifier.fillMaxWidth()); Text("Qualità: $quality/5",fontWeight=FontWeight.Bold); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(1..5).forEach{n->FilterChip(quality==n,{quality=n},{Text(n.toString())})}} }}
        item{Button(onClick={prefs.edit().putString(k,JSONObject().put("bed",bed).put("wake",wake).put("quality",quality).toString()).apply();msg=true},modifier=Modifier.fillMaxWidth()){Text("Salva sonno")}}
        if(msg)item{Text("✓ Dati sonno salvati",color=MaterialTheme.colorScheme.primary)}
    }
}

@Composable
fun WeightScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var weight by remember{mutableStateOf("")}; var waist by remember{mutableStateOf("")}; var abdomen by remember{mutableStateOf("")}; var chest by remember{mutableStateOf("")}; var note by remember{mutableStateOf("")}; var history by remember{mutableStateOf(loadSimpleItems(prefs,"measure_history"))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ScreenTitle("⚖️ Peso e misure"){TextButton(onClick=onBack){Text("‹ Indietro")}}}
        item{AppCard{ OutlinedTextField(weight,{weight=it},label={Text("Peso kg")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(waist,{waist=it},label={Text("Vita cm")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(abdomen,{abdomen=it},label={Text("Addome cm")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(chest,{chest=it},label={Text("Torace cm")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(note,{note=it},label={Text("Note")},modifier=Modifier.fillMaxWidth()); Button(onClick={ if(weight.isNotBlank()||waist.isNotBlank()||abdomen.isNotBlank()||chest.isNotBlank()){ val t="${LocalDate.now()} · Peso ${weight.ifBlank{"-"}} kg · Vita ${waist.ifBlank{"-"}} · Addome ${abdomen.ifBlank{"-"}} · Torace ${chest.ifBlank{"-"}}"; history=listOf(SimpleItem(System.currentTimeMillis(),t,false,note))+history; saveSimpleItems(prefs,"measure_history",history); weight="";waist="";abdomen="";chest="";note="" }},modifier=Modifier.fillMaxWidth()){Text("Salva misure")}}}
        items(history){h->AppCard{Text(h.text,fontWeight=FontWeight.Bold); if(h.extra.isNotBlank())Text(h.extra,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}

@Composable
fun WaterScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val date=LocalDate.now(); var goal by remember{mutableIntStateOf(prefs.getInt("water_goal",2000))}; var amount by remember{mutableIntStateOf(prefs.getInt("water_${date}",0))}
    fun save(){prefs.edit().putInt("water_goal",goal).putInt("water_${date}",amount).apply()}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ScreenTitle("💧 Idratazione",formatDateFull(date)){TextButton(onClick=onBack){Text("‹ Indietro")}}}
        item{AppCard{Text("$amount / $goal ml",fontSize=28.sp,fontWeight=FontWeight.ExtraBold); LinearProgressIndicator(progress={if(goal>0)(amount.toFloat()/goal).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth()); OutlinedTextField(goal.toString(),{goal=it.toIntOrNull()?.coerceAtLeast(250)?:goal},label={Text("Obiettivo ml")},modifier=Modifier.fillMaxWidth()); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={amount+=250;save()},modifier=Modifier.weight(1f)){Text("+250")};Button(onClick={amount+=500;save()},modifier=Modifier.weight(1f)){Text("+500")};OutlinedButton(onClick={amount=(amount-250).coerceAtLeast(0);save()},modifier=Modifier.weight(1f)){Text("-250")}}; OutlinedButton(onClick={amount=0;save()},modifier=Modifier.fillMaxWidth()){Text("Azzera oggi")}}}
    }
}

@Composable
fun ProfessionalScreen(prefs: SharedPreferences, key: String, title: String, onBack: () -> Unit) {
    var name by remember{mutableStateOf(prefs.getString("${key}_name","")?:"")}; var notes by remember{mutableStateOf(prefs.getString("${key}_notes","")?:"")}; var canPlans by remember{mutableStateOf(prefs.getBoolean("${key}_plans",false))}; var canFeedback by remember{mutableStateOf(prefs.getBoolean("${key}_feedback",false))}; var msg by remember{mutableStateOf(false)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ScreenTitle(title){TextButton(onClick=onBack){Text("‹ Indietro")}}}
        item{AppCard{OutlinedTextField(name,{name=it},label={Text("Nome professionista")},modifier=Modifier.fillMaxWidth());OutlinedTextField(notes,{notes=it},label={Text("Note / piano / indicazioni")},modifier=Modifier.fillMaxWidth(),minLines=3);ToggleRow("Può gestire i piani",canPlans){canPlans=it};ToggleRow("Può lasciare feedback",canFeedback){canFeedback=it}}}
        item{Button(onClick={prefs.edit().putString("${key}_name",name).putString("${key}_notes",notes).putBoolean("${key}_plans",canPlans).putBoolean("${key}_feedback",canFeedback).apply();msg=true},modifier=Modifier.fillMaxWidth()){Text("Salva")}}
        if(msg)item{Text("✓ Dati salvati",color=MaterialTheme.colorScheme.primary)}
    }
}

@Composable
fun ShoppingScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var list by remember{mutableStateOf(loadSimpleItems(prefs,"shopping_items"))}; var item by remember{mutableStateOf("")}; var qty by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ScreenTitle("🛒 Lista della spesa"){TextButton(onClick=onBack){Text("‹ Indietro")}}}
        item{AppCard{OutlinedTextField(item,{item=it},label={Text("Prodotto")},modifier=Modifier.fillMaxWidth());OutlinedTextField(qty,{qty=it},label={Text("Quantità")},modifier=Modifier.fillMaxWidth());Button(onClick={if(item.isNotBlank()){list=list+SimpleItem(System.currentTimeMillis(),item.trim(),false,qty.trim());saveSimpleItems(prefs,"shopping_items",list);item="";qty=""}},modifier=Modifier.fillMaxWidth()){Text("＋ Aggiungi prodotto")}}}
        if(list.isEmpty())item{Text("Lista vuota.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(list,key={it.id}){x->AppCard{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(x.done,{v->list=list.map{if(it.id==x.id)it.copy(done=v)else it};saveSimpleItems(prefs,"shopping_items",list)});Column(Modifier.weight(1f)){Text(x.text,fontWeight=FontWeight.Bold);if(x.extra.isNotBlank())Text(x.extra,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={list=list.filterNot{it.id==x.id};saveSimpleItems(prefs,"shopping_items",list)}){Text("🗑️")}}}}
    }
}

@Composable
fun WidgetSettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var daily by remember{mutableStateOf(prefs.getBoolean("widget_daily",true))}; var water by remember{mutableStateOf(prefs.getBoolean("widget_water",true))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){ item{ScreenTitle("🧩 Widget"){TextButton(onClick=onBack){Text("‹ Indietro")}}}; item{AppCard{ToggleRow("Riepilogo giornata",daily){daily=it;prefs.edit().putBoolean("widget_daily",it).apply()};ToggleRow("Idratazione",water){water=it;prefs.edit().putBoolean("widget_water",it).apply()};Text("Queste preferenze vengono salvate e saranno usate dai widget dell'app.",color=MaterialTheme.colorScheme.onSurfaceVariant)}} }
}

data class SimpleItem(val id: Long, val text: String, val done: Boolean, val extra: String)
fun loadSimpleItems(prefs: SharedPreferences, key: String): List<SimpleItem> = runCatching {
    val a=JSONArray(prefs.getString(key,"[]")); buildList { for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(SimpleItem(o.getLong("id"),o.optString("text"),o.optBoolean("done"),o.optString("extra"))) } }
}.getOrDefault(emptyList())
fun saveSimpleItems(prefs: SharedPreferences,key:String,list:List<SimpleItem>){ val a=JSONArray(); list.forEach{a.put(JSONObject().put("id",it.id).put("text",it.text).put("done",it.done).put("extra",it.extra))}; prefs.edit().putString(key,a.toString()).apply() }

fun loadMeals(prefs: SharedPreferences, date: LocalDate): List<MealEntry> = runCatching {
    val arr = JSONArray(prefs.getString("meals_${date}", "[]"))
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                MealEntry(
                    id = o.getString("id"),
                    date = o.getString("date"),
                    name = o.getString("name"),
                    time = o.getString("time"),
                    description = o.optString("description"),
                    optional = o.optBoolean("optional"),
                    done = o.optBoolean("done"),
                    reminder = reminderFromJson(o.optJSONObject("reminder"))
                )
            )
        }
    }
}.getOrDefault(emptyList())

fun saveMeals(prefs: SharedPreferences, date: LocalDate, list: List<MealEntry>) {
    val arr = JSONArray()
    list.forEach { m ->
        arr.put(
            JSONObject()
                .put("id", m.id)
                .put("date", m.date)
                .put("name", m.name)
                .put("time", m.time)
                .put("description", m.description)
                .put("optional", m.optional)
                .put("done", m.done)
                .put("reminder", reminderToJson(m.reminder))
        )
    }
    prefs.edit().putString("meals_${date}", arr.toString()).apply()
}

fun loadWorkouts(prefs: SharedPreferences): List<WorkoutEntry> = runCatching {
    val arr = JSONArray(prefs.getString("workouts", "[]"))
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                WorkoutEntry(
                    o.getLong("id"),
                    o.getString("date"),
                    o.getString("type"),
                    o.getString("title"),
                    o.getString("time"),
                    o.optString("duration"),
                    o.optString("notes"),
                    o.optBoolean("done"),
                    reminderFromJson(o.optJSONObject("reminder"))
                )
            )
        }
    }
}.getOrDefault(emptyList())

fun saveWorkouts(prefs: SharedPreferences, list: List<WorkoutEntry>) {
    val arr = JSONArray()
    list.forEach { w ->
        arr.put(
            JSONObject()
                .put("id", w.id)
                .put("date", w.date)
                .put("type", w.type)
                .put("title", w.title)
                .put("time", w.time)
                .put("duration", w.durationText)
                .put("notes", w.notes)
                .put("done", w.done)
                .put("reminder", reminderToJson(w.reminder))
        )
    }
    prefs.edit().putString("workouts", arr.toString()).apply()
}

fun loadRules(prefs: SharedPreferences): List<RecurringRule> {
    val loaded = runCatching {
        val arr = JSONArray(prefs.getString("rules", "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ds = mutableSetOf<Int>()
                val a = o.getJSONArray("weekdays")
                for (j in 0 until a.length()) ds += a.getInt(j)
                add(
                    RecurringRule(
                        o.getLong("id"),
                        o.getString("title"),
                        o.getString("category"),
                        ds,
                        o.getString("time"),
                        o.getString("start"),
                        o.getString("end"),
                        reminderFromJson(o.optJSONObject("reminder"))
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    if (loaded.isNotEmpty()) return loaded
    return listOf(
        RecurringRule(1, "Palestra", "Allenamento", setOf(2,4), "17:00", LocalDate.now().toString(), LocalDate.now().plusMonths(10).toString(), ReminderSettings()),
        RecurringRule(2, "Domenica libera", "Giornata libera", setOf(7), "09:00", LocalDate.now().toString(), LocalDate.now().plusYears(1).toString(), ReminderSettings(enabled = false))
    )
}

fun saveRules(prefs: SharedPreferences, list: List<RecurringRule>) {
    val arr = JSONArray()
    list.forEach { r ->
        val days = JSONArray()
        r.weekdays.sorted().forEach { days.put(it) }
        arr.put(
            JSONObject()
                .put("id", r.id)
                .put("title", r.title)
                .put("category", r.category)
                .put("weekdays", days)
                .put("time", r.time)
                .put("start", r.startDate)
                .put("end", r.endDate)
                .put("reminder", reminderToJson(r.reminder))
        )
    }
    prefs.edit().putString("rules", arr.toString()).apply()
}

fun loadEvents(prefs: SharedPreferences): List<CalendarEvent> = runCatching {
    val arr = JSONArray(prefs.getString("events", "[]"))
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                CalendarEvent(
                    id = o.getLong("id"),
                    date = o.getString("date"),
                    category = o.getString("category"),
                    title = o.getString("title"),
                    time = o.getString("time"),
                    durationText = o.optString("duration"),
                    notes = o.optString("notes"),
                    recurrence = o.optString("recurrence", "Nessuna"),
                    reminder = reminderFromJson(o.optJSONObject("reminder"))
                )
            )
        }
    }
}.getOrDefault(emptyList())

fun saveEvents(prefs: SharedPreferences, list: List<CalendarEvent>) {
    val arr = JSONArray()
    list.forEach { e ->
        arr.put(
            JSONObject()
                .put("id", e.id)
                .put("date", e.date)
                .put("category", e.category)
                .put("title", e.title)
                .put("time", e.time)
                .put("duration", e.durationText)
                .put("notes", e.notes)
                .put("recurrence", e.recurrence)
                .put("reminder", reminderToJson(e.reminder))
        )
    }
    prefs.edit().putString("events", arr.toString()).apply()
}

fun reminderToJson(r: ReminderSettings) =
    JSONObject().put("enabled", r.enabled).put("days", r.daysBefore).put("hours", r.hoursBefore).put("minutes", r.minutesBefore)

fun reminderFromJson(o: JSONObject?): ReminderSettings =
    if (o == null) ReminderSettings()
    else ReminderSettings(o.optBoolean("enabled", true), o.optInt("days", 0), o.optInt("hours", 0), o.optInt("minutes", 10))

fun reminderLabel(r: ReminderSettings): String {
    if (!r.enabled) return "Promemoria disattivato"
    val parts = mutableListOf<String>()
    if (r.daysBefore > 0) parts += "${r.daysBefore}g"
    if (r.hoursBefore > 0) parts += "${r.hoursBefore}h"
    if (r.minutesBefore > 0) parts += "${r.minutesBefore}min"
    return if (parts.isEmpty()) "all'orario esatto" else parts.joinToString(" ") + " prima"
}

fun parseTime(s: String): LocalTime =
    runCatching { LocalTime.parse(s, DateTimeFormatter.ofPattern("H:mm")) }.getOrDefault(LocalTime.of(9,0))

fun formatDateShort(s: String): String =
    runCatching { LocalDate.parse(s).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(s)

fun formatDateFull(d: LocalDate): String =
    "${d.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)} ${d.year}"

fun categoryEmoji(c: String): String = when {
    c.contains("Allen", true) -> "🏋️"
    c.contains("Cammin", true) -> "🚶"
    c.contains("libera", true) -> "🌴"
    c.contains("Spesa", true) -> "🛒"
    c.contains("Idrat", true) -> "💧"
    c.contains("Sonno", true) -> "😴"
    c.contains("personale", true) -> "🌟"
    else -> "📅"
}

fun weekdayLabel(days: Set<Int>): String =
    days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { c -> c.uppercase() } }

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Promemoria attività, pasti e impegni"
            }
        )
    }
}

fun cancelReminder(context: Context, requestCode: Int) {
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    if (pending != null) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        pending.cancel()
    }
}

fun scheduleIfNeeded(
    context: Context,
    title: String,
    date: LocalDate,
    time: LocalTime,
    r: ReminderSettings,
    requestCode: Int
) {
    if (!r.enabled) return
    val target = LocalDateTime.of(date,time)
        .minusDays(r.daysBefore.toLong())
        .minusHours(r.hoursBefore.toLong())
        .minusMinutes(r.minutesBefore.toLong())
    val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (millis <= System.currentTimeMillis()) return

    val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", title)
    val pi = PendingIntent.getBroadcast(
        context,
        requestCode.absoluteValue,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try {
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    } catch (_: SecurityException) {
        alarm.set(AlarmManager.RTC_WAKEUP, millis, pi)
    }
}

fun scheduleNextOccurrence(context: Context, r: RecurringRule, after: LocalDateTime = LocalDateTime.now()) {
    if (!r.reminder.enabled || r.weekdays.isEmpty()) return
    val start = runCatching { LocalDate.parse(r.startDate) }.getOrDefault(LocalDate.now())
    val end = runCatching { LocalDate.parse(r.endDate) }.getOrDefault(start.plusYears(1))
    var d = maxOf(start, after.toLocalDate())
    val eventTime = parseTime(r.time)

    while (!d.isAfter(end)) {
        if (d.dayOfWeek.value in r.weekdays) {
            val trigger = LocalDateTime.of(d, eventTime)
                .minusDays(r.reminder.daysBefore.toLong())
                .minusHours(r.reminder.hoursBefore.toLong())
                .minusMinutes(r.reminder.minutesBefore.toLong())
            if (trigger.isAfter(after)) {
                scheduleRecurringAlarm(context, r, d, trigger)
                return
            }
        }
        d = d.plusDays(1)
    }
}

fun scheduleRecurringAlarm(context: Context, r: RecurringRule, occurrence: LocalDate, trigger: LocalDateTime) {
    val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(context, ReminderReceiver::class.java)
        .putExtra("title", r.title)
        .putExtra("recurring_rule_id", r.id)
        .putExtra("occurrence_date", occurrence.toString())

    val pi = PendingIntent.getBroadcast(
        context,
        (r.id.hashCode() * 31 + 7).absoluteValue,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try {
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    } catch (_: SecurityException) {
        alarm.set(AlarmManager.RTC_WAKEUP, millis, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Promemoria"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 $title")
            .setContentText("È il momento di controllare questa attività.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(title.hashCode().absoluteValue, notification)
        }

        val ruleId = intent.getLongExtra("recurring_rule_id", -1L)
        if (ruleId >= 0) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            loadRules(prefs).firstOrNull { it.id == ruleId }?.let { rule ->
                val occurrence = runCatching { LocalDate.parse(intent.getStringExtra("occurrence_date")) }.getOrDefault(LocalDate.now())
                scheduleNextOccurrence(context, rule, LocalDateTime.of(occurrence.plusDays(1), LocalTime.MIDNIGHT))
            }
        }
    }
}
