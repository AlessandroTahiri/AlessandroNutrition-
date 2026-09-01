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

private const val PREFS = "nutrition_v22" // keep key for seamless v2.2 -> v2.3 migration
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
                    SecondaryScreen(menuScreen!!, prefs, themeMode, onTheme = {
                        themeMode = it; prefs.edit().putString("theme", it).apply()
                    }, onBack = { menuScreen = null })
                } else {
                    when (nav) {
                        0 -> HomeScreen(plan, prefs, context, onMenu = { menuScreen = "menu" })
                        1 -> TrainingScreen(prefs, context)
                        2 -> PlanScreen(plan, prefs)
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
fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
fun CircleCheck(done: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clickable { onClick() },
        shape = CircleShape,
        color = if (done) Aqua.copy(alpha = .18f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (done) Aqua else MaterialTheme.colorScheme.outline)
    ) { Box(contentAlignment = Alignment.Center) { Text("✓", color = if (done) Aqua else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) } }
}

@Composable
fun HomeScreen(plan: List<PlanDay>, prefs: SharedPreferences, context: Context, onMenu: () -> Unit) {
    val today = LocalDate.now()
    val name = prefs.getString("profile_name", "Alessandro") ?: "Alessandro"
    val p = plan.firstOrNull { it.date == today.toString() }
    val meals = listOf(
        Triple("🍳 Colazione", "07:00", p?.breakfast ?: "Yogurt greco, avena e frutta"),
        Triple("🥤 Spuntino mattutino", "10:30", p?.snack ?: "Shake proteico"),
        Triple("🍽️ Pranzo", "14:00", p?.lunch ?: "Pollo, verdure e couscous"),
        Triple("🍎 Spuntino pomeridiano", "16:30", "Frutta e mandorle"),
        Triple("🍲 Cena", "20:00", "Pesce e verdure")
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle("Ciao $name 👋", "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)}") {
                OutlinedButton(onClick = onMenu, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("☰ Menu") }
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Come sta andando oggi", fontWeight = FontWeight.Bold); Text("5 attività completate su 8", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("63%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                }
                LinearProgressIndicator(progress = { .63f }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("💧 6/8"); Text("😴 7h 20m"); Text("🏋️ 17:00"); Text("🌟 2") }
            }
        }
        item { Text("🍽️ Alimentazione di oggi", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        items(meals) { meal ->
            var done by remember(meal.first) { mutableStateOf(prefs.getBoolean("today_${meal.first}", false)) }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(meal.first, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text("✏️", modifier = Modifier.clickable { }) }
                        Text("${meal.second} · ${meal.third}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("🔔 Promemoria attivo", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    CircleCheck(done) { done = !done; prefs.edit().putBoolean("today_${meal.first}", done).apply() }
                }
            }
        }
        item { Text("🏋️ Attività di oggi", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🏋️ Palestra ✏️", fontWeight = FontWeight.Bold)
                        Text("17:00 · Total body", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("🔔 10 minuti prima", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    var done by remember { mutableStateOf(false) }
                    CircleCheck(done) { done = !done }
                }
            }
        }
        item { Text("🌟 Vita personale", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("🛒 Fare la spesa ✏️", fontWeight = FontWeight.Bold); Text("18:30 · 8 prodotti", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("🔔 Promemoria attivo", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                    var done by remember { mutableStateOf(false) }; CircleCheck(done) { done = !done }
                }
            }
        }
        item { AppCard { Text("📝 Resoconto della giornata", fontWeight = FontWeight.Bold); Text("Salva il resoconto nel calendario.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Compila resoconto") } } }
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
                editing = null; showNew = false
            }
        )
        return
    }

    if (showRules) {
        RecurringRulesScreen(prefs, context, onBack = { showRules = false })
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🏋️ Allenamenti", "Registra il tipo di allenamento e il tempo realmente impiegato") { IconButton(onClick = { showNew = true }) { Text("＋", fontSize = 28.sp) } } }
        if (workouts.isEmpty()) {
            item {
                AppCard {
                    Text("Nessun allenamento registrato", fontWeight = FontWeight.Bold)
                    Text("Aggiungine uno e indica liberamente il tempo impiegato.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi allenamento") }
                }
            }
        } else {
            items(workouts.sortedByDescending { it.date }) { w ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🏋️ ${w.title}", fontWeight = FontWeight.Bold)
                            Text("${formatDateShort(w.date)} · ${w.time} · ${w.durationText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (w.reminder.enabled) "🔔 ${reminderLabel(w.reminder)}" else "🔕 Promemoria disattivato", color = if (w.reminder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { editing = w }) { Text("✏️") }
                        CircleCheck(w.done) {
                            val updated = w.copy(done = !w.done); workouts = workouts.map { if (it.id == w.id) updated else it }; saveWorkouts(prefs, workouts)
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Text("🔁 Programmazione fissa", fontWeight = FontWeight.ExtraBold)
                Text("Crea tutte le programmazioni ricorrenti che vuoi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { showRules = true }, modifier = Modifier.fillMaxWidth()) { Text("Gestisci programmazioni ricorrenti") }
            }
        }
    }
}

@Composable
fun WorkoutEditor(initial: WorkoutEntry?, onDismiss: () -> Unit, onSave: (WorkoutEntry) -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: "Total body") }
    var title by remember { mutableStateOf(initial?.title ?: "Total body") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var time by remember { mutableStateOf(initial?.time ?: "17:00") }
    var duration by remember { mutableStateOf(initial?.durationText ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderSettings()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle(if (initial == null) "＋ Nuovo allenamento" else "✏️ Modifica allenamento") { TextButton(onClick = onDismiss) { Text("Annulla") } } }
        item {
            AppCard {
                Text("Tipo di allenamento", fontWeight = FontWeight.Bold)
                SimplePicker(type, listOf("Total body", "Forza", "Cardio", "CrossFit", "Camminata", "Mobilità", "Altro")) { type = it; if (title.isBlank() || title == initial?.type) title = it }
                OutlinedTextField(title, { title = it }, label = { Text("Nome allenamento") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it }, label = { Text("Tempo impiegato") }, placeholder = { Text("Es. 45 min, 1 ora, 1h 20 min") }, modifier = Modifier.fillMaxWidth())
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
                    onSave(WorkoutEntry(initial?.id ?: System.currentTimeMillis(), date, type, title.ifBlank { type }, time, duration.ifBlank { "Tempo non indicato" }, notes, initial?.done ?: false, reminder))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = runCatching { LocalDate.parse(date); parseTime(time); true }.getOrDefault(false)
            ) { Text("Salva allenamento") }
        }
    }
}

@Composable
fun RecurringRulesScreen(prefs: SharedPreferences, context: Context, onBack: () -> Unit) {
    var rules by remember { mutableStateOf(loadRules(prefs)) }
    var editing by remember { mutableStateOf<RecurringRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        RuleEditor(editing, onDismiss = { creating = false; editing = null }) { r ->
            rules = if (editing == null) rules + r else rules.map { if (it.id == r.id) r else it }
            saveRules(prefs, rules)
            scheduleNextOccurrence(context, r)
            creating = false; editing = null
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🔁 Programmazioni ricorrenti", "Aggiungi, modifica o sospendi le tue programmazioni") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        items(rules) { r ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${categoryEmoji(r.category)} ${r.title}", fontWeight = FontWeight.Bold)
                        Text("${weekdayLabel(r.weekdays)} · ${r.time}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (r.reminder.enabled) "🔔 ${reminderLabel(r.reminder)}" else "🔕 Promemoria disattivato", color = if (r.reminder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    IconButton(onClick = { editing = r }) { Text("✏️") }
                }
            }
        }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Nuova programmazione ricorrente") } }
    }
}

@Composable
fun RuleEditor(initial: RecurringRule?, onDismiss: () -> Unit, onSave: (RecurringRule) -> Unit) {
    var category by remember { mutableStateOf(initial?.category ?: "Allenamento") }
    var title by remember { mutableStateOf(initial?.title ?: "Palestra") }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: setOf(DayOfWeek.TUESDAY.value)) }
    var time by remember { mutableStateOf(initial?.time ?: "17:00") }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now().toString()) }
    var end by remember { mutableStateOf(initial?.endDate ?: LocalDate.now().plusMonths(6).toString()) }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderSettings()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle(if (initial == null) "＋ Nuova programmazione" else "✏️ Modifica programmazione") { TextButton(onClick = onDismiss) { Text("Annulla") } } }
        item {
            AppCard {
                Text("Tipo", fontWeight = FontWeight.Bold)
                SimplePicker(category, listOf("Allenamento", "Camminata", "Giornata libera", "Appuntamento", "Attività personale", "Idratazione", "Sonno", "Altro")) { category = it }
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                Text("Giorni della settimana", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("L","M","M","G","V","S","D").forEachIndexed { i, label ->
                        val day = i + 1
                        FilterChip(selected = day in weekdays, onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day }, label = { Text(label) })
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
        item { Button(onClick = { onSave(RecurringRule(initial?.id ?: System.currentTimeMillis(), title, category, weekdays, time, start, end, reminder)) }, enabled = weekdays.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Salva programmazione") } }
    }
}

@Composable
fun ReminderEditor(settings: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.enabled, onCheckedChange = { onChange(settings.copy(enabled = it)) })
            Spacer(Modifier.width(8.dp))
            Column { Text("🔔 Promemoria e notifica", fontWeight = FontWeight.Bold); Text("Attivabile o disattivabile per questo elemento", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
        if (settings.enabled) {
            Text("Avvisami prima (opzionale)", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberPickerField("Giorni", settings.daysBefore, 0..30) { onChange(settings.copy(daysBefore = it)) }
                NumberPickerField("Ore", settings.hoursBefore, 0..23) { onChange(settings.copy(hoursBefore = it)) }
                NumberPickerField("Minuti", settings.minutesBefore, 0..59) { onChange(settings.copy(minutesBefore = it)) }
            }
            Text("Esempio: ${settings.daysBefore} giorni · ${settings.hoursBefore} ore · ${settings.minutesBefore} minuti prima", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
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
                range.forEach { n -> DropdownMenuItem(text = { Text(n.toString()) }, onClick = { onChange(n); expanded = false }) }
            }
        }
    }
}

@Composable
fun SimplePicker(value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value, modifier = Modifier.weight(1f)); Text("▼") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); expanded = false }) }
        }
    }
}

@Composable
fun PlanScreen(plan: List<PlanDay>, prefs: SharedPreferences) {
    var week by rememberSaveable { mutableIntStateOf(1) }
    var day by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val start = plan.firstOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    val date = start.plusDays(((week - 1) * 7 + day).toLong())
    val p = plan.firstOrNull { it.date == date.toString() }
    val meals = listOf(
        Triple("🍳 Colazione", "07:00", p?.breakfast ?: "Yogurt greco, avena e frutta"),
        Triple("🥤 Spuntino mattutino", "10:30", p?.snack ?: "Shake proteico"),
        Triple("🍽️ Pranzo", "14:00", p?.lunch ?: "Pollo, verdure e couscous"),
        Triple("🍎 Spuntino pomeridiano", "16:30", "Frutta e mandorle"),
        Triple("🍲 Cena", "20:00", "Pesce e verdure")
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("🍽️ Piano alimentare", "Scegli prima la settimana e poi il giorno") }
        item {
            AppCard {
                Text("1 · Seleziona la settimana", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { w -> FilterChip(selected = week == w, onClick = { week = w }, label = { Text("Set. $w") }, modifier = Modifier.weight(1f)) }
                }
            }
        }
        item {
            AppCard {
                Text("2 · Seleziona il giorno", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Lun","Mar","Mer","Gio","Ven","Sab","Dom").forEachIndexed { i, d -> FilterChip(selected = day == i, onClick = { day = i }, label = { Text(d) }) }
                }
            }
        }
        item { Text("Settimana $week · ${names[day]}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        items(meals) { m -> AppCard { Row { Column(Modifier.weight(1f)) { Text(m.first, fontWeight = FontWeight.Bold); Text("${m.second} · ${m.third}", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("🔔 Promemoria attivo", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }; Text("✏️", modifier = Modifier.padding(8.dp).clickable { }) } } }
        item { AppCard { Text("Riepilogo giornata", fontWeight = FontWeight.Bold); Text("5 pasti · 2 L acqua · 5 promemoria", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
fun CalendarScreen(prefs: SharedPreferences, context: Context) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var events by remember { mutableStateOf(loadEvents(prefs)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }

    if (addDate != null) {
        CalendarEventEditor(addDate!!, onDismiss = { addDate = null }) { ev ->
            events = events + ev; saveEvents(prefs, events)
            scheduleIfNeeded(context, ev.title, LocalDate.parse(ev.date), parseTime(ev.time), ev.reminder, ev.id.toInt())
            addDate = null
        }
        return
    }

    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    val cells = List(offset) { null } + (1..total).map { month.atDay(it) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle("📅 Calendario", "Tocca ＋ nel riquadro del giorno per aggiungere qualcosa")
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Text("‹", fontSize = 30.sp) }
                Text("${month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} ${month.year}", fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = { month = month.plusMonths(1) }) { Text("›", fontSize = 30.sp) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth()) { listOf("L","M","M","G","V","S","D").forEach { Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) } }
                cells.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { d ->
                            if (d == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else CalendarDayCell(d, events.filter { it.date == d.toString() }, Modifier.weight(1f), onAdd = { addDate = d }, onSelect = { selectedDate = d })
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
                    val list = events.filter { it.date == d.toString() }
                    if (list.isEmpty()) Text("Nessun impegno programmato", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else list.forEach { ev ->
                        Text("${categoryEmoji(ev.category)} ${ev.time} · ${ev.title}", fontWeight = FontWeight.Bold)
                        Text(if (ev.reminder.enabled) "🔔 ${reminderLabel(ev.reminder)}" else "🔕 Promemoria disattivato", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    Button(onClick = { addDate = d }, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi a questo giorno") }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(date: LocalDate, events: List<CalendarEvent>, modifier: Modifier, onAdd: () -> Unit, onSelect: () -> Unit) {
    Surface(
        modifier = modifier.aspectRatio(.92f).clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Surface(modifier = Modifier.size(26.dp).clickable { onAdd() }, shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text("＋", fontSize = 16.sp) } }
            }
            events.take(2).forEach { Text("${categoryEmoji(it.category)} ${it.title}", fontSize = 9.sp, maxLines = 1) }
            if (events.size > 2) Text("+${events.size - 2} altri", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
        }
    }
}

@Composable
fun CalendarEventEditor(date: LocalDate, onDismiss: () -> Unit, onSave: (CalendarEvent) -> Unit) {
    var category by remember { mutableStateOf("Impegno / Appuntamento") }
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("10:00") }
    var duration by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(ReminderSettings()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("＋ Aggiungi impegno", formatDateFull(date)) { TextButton(onClick = onDismiss) { Text("Annulla") } } }
        item {
            AppCard {
                SimplePicker(category, listOf("Impegno / Appuntamento", "Allenamento", "Attività personale", "Spesa", "Idratazione", "Sonno", "Giornata libera", "Altro")) { category = it }
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Ora HH:MM") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it }, label = { Text("Durata / tempo (opzionale)") }, placeholder = { Text("Es. 45 min") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        }
        item { ReminderEditor(reminder) { reminder = it } }
        item { Button(onClick = { onSave(CalendarEvent(System.currentTimeMillis(), date.toString(), category, title.ifBlank { category }, time, duration, notes, reminder)) }, modifier = Modifier.fillMaxWidth()) { Text("Salva nel calendario") } }
    }
}

@Composable
fun SecondaryScreen(screen: String, prefs: SharedPreferences, theme: String, onTheme: (String) -> Unit, onBack: () -> Unit) {
    var target by rememberSaveable(screen) { mutableStateOf(if (screen == "menu") "menu" else screen) }
    if (target != "menu") {
        when (target) {
            "shopping" -> ShoppingScreen(onBack = { target = "menu" })
            "journal" -> JournalScreen(onBack = { target = "menu" })
            "goals" -> GoalsScreen(onBack = { target = "menu" })
            "profile" -> ProfileScreen(prefs, onBack = { target = "menu" })
            "settings" -> SettingsScreen(theme, onTheme, onBack = { target = "menu" })
            else -> SimpleInfoScreen(target, onBack = { target = "menu" })
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("☰ Menu") { TextButton(onClick = onBack) { Text("Chiudi") } } }
        item { MenuSection("👤 Profilo") }
        item { MenuButton("🧭 Il mio percorso") { target = "profile" } }
        item { MenuSection("🔒 Il mio spazio") }
        item { MenuButton("🎯 Obiettivi e trofei") { target = "goals" } }
        item { MenuButton("🌟 Vita personale") { target = "personal" } }
        item { MenuButton("📝 Diario giornaliero") { target = "journal" } }
        item { MenuButton("😴 Sonno") { target = "sleep" } }
        item { MenuButton("⚖️ Peso e misure") { target = "weight" } }
        item { MenuButton("💧 Idratazione") { target = "water" } }
        item { MenuSection("👨‍⚕️ I miei professionisti") }
        item { MenuButton("🥗 Nutrizionista") { target = "nutritionist" } }
        item { MenuButton("💪 Personal Trainer") { target = "pt" } }
        item { MenuSection("⚙️ Organizzazione e app") }
        item { MenuButton("🛒 Lista della spesa") { target = "shopping" } }
        item { MenuButton("📋 Agenda") { target = "agenda" } }
        item { MenuButton("🧩 Widget") { target = "widgets" } }
        item { MenuButton("⚙️ Impostazioni") { target = "settings" } }
    }
}

@Composable fun MenuSection(text: String) { Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp)) }
@Composable fun MenuButton(text: String, onClick: () -> Unit) { Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(16.dp), fontWeight = FontWeight.Bold) } }

@Composable
fun ShoppingScreen(onBack: () -> Unit) {
    val initial = remember { mutableStateListOf("🍎 Mele" to false, "🍐 Pere" to false, "🍝 Pasta proteica" to false, "🧂 Sale" to false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("🛒 Spesa", "${initial.count { it.second }} di ${initial.size} acquistati") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        items(initial.size) { i -> val p = initial[i]; AppCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(p.first, fontWeight = FontWeight.Bold); Text("− 1 + · pezzi", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("📝 Nota", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }; CircleCheck(p.second) { initial[i] = p.first to !p.second } } } }
        item { Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi prodotto") } }
    }
}

@Composable
fun JournalScreen(onBack: () -> Unit) {
    var note by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("📝 Resoconto della giornata") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard { Text("😊 Come è andata?", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Molto bene","Bene","Così così","Male").forEach { AssistChip(onClick = {}, label = { Text(it, fontSize = 11.sp) }) } } } }
        item { AppCard { Text("📓 Note libere", fontWeight = FontWeight.Bold); OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, placeholder = { Text("Scrivi come è andata la giornata...") }) } }
        item { Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("💾 Salva resoconto") } }
    }
}

@Composable
fun GoalsScreen(onBack: () -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { ScreenTitle("🎯 Obiettivi e trofei") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }; item { AppCard { Text("🏋️ Costanza allenamenti", fontWeight = FontWeight.Bold); Text("18 / 25", color = MaterialTheme.colorScheme.onSurfaceVariant); LinearProgressIndicator(progress = { .72f }, modifier = Modifier.fillMaxWidth()); Text("🌴 Le pause programmate non interrompono la serie", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) } }; item { AppCard { Text("🏆 Trofei", fontWeight = FontWeight.Bold); Text("🥉 5 impegni · 🥈 10 allenamenti · 💧 7 giorni acqua") } } } }

@Composable
fun ProfileScreen(prefs: SharedPreferences, onBack: () -> Unit) { var name by remember { mutableStateOf(prefs.getString("profile_name", "Alessandro") ?: "Alessandro") }; LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { ScreenTitle("🧭 Il mio percorso") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }; item { AppCard { OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { prefs.edit().putString("profile_name", name).apply() }, modifier = Modifier.fillMaxWidth()) { Text("Salva profilo") } } }; item { AppCard { Text("🎯 Obiettivi", fontWeight = FontWeight.Bold); Text("Dimagrire · Aumentare massa · Bere di più", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; item { AppCard { Text("🏠 Personalizza Home", fontWeight = FontWeight.Bold); Text("Alimentazione · Allenamenti · Acqua · Sonno · Vita personale", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
fun SettingsScreen(theme: String, onTheme: (String) -> Unit, onBack: () -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { ScreenTitle("⚙️ Impostazioni") { TextButton(onClick = onBack) { Text("‹ Indietro") } } }; item { AppCard { Text("Aspetto", fontWeight = FontWeight.Bold); listOf("dark" to "🌙 Scuro", "light" to "☀️ Chiaro", "system" to "📱 Automatico").forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = theme == p.first, onClick = { onTheme(p.first) }); Text(p.second) } } } } } }

@Composable
fun SimpleInfoScreen(name: String, onBack: () -> Unit) {
    val title = when (name) {
        "personal" -> "🌟 Vita personale"
        "sleep" -> "😴 Sonno"
        "weight" -> "⚖️ Peso e misure"
        "water" -> "💧 Idratazione"
        "nutritionist" -> "🥗 Nutrizionista"
        "pt" -> "💪 Personal Trainer"
        "agenda" -> "📋 Agenda"
        "widgets" -> "🧩 Widget"
        else -> name.replaceFirstChar { it.uppercase() }
    }
    val description = when (name) {
        "personal" -> "Organizza impegni, abitudini e attività personali."
        "sleep" -> "Registra orario di sonno, risveglio, durata e qualità."
        "weight" -> "Registra peso e misure e segui i progressi nel tempo."
        "water" -> "Gestisci il tuo obiettivo acqua e i promemoria personalizzati."
        "nutritionist" -> "Collega il nutrizionista e gestisci piano e permessi."
        "pt" -> "Collega il Personal Trainer e gestisci schede e permessi."
        "agenda" -> "Consulta in ordine cronologico tutti i prossimi impegni."
        "widgets" -> "Configura i widget della giornata e le sezioni da mostrare."
        else -> "Gestisci questa sezione della tua giornata."
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle(title) { TextButton(onClick = onBack) { Text("‹ Indietro") } } }
        item { AppCard { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("🔔 Ogni elemento programmabile può avere una notifica attivabile/disattivabile con preavviso personalizzato in giorni, ore e minuti.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) } }
        item { Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi") } }
    }
}

fun loadWorkouts(prefs: SharedPreferences): List<WorkoutEntry> {
    return runCatching {
        val arr = JSONArray(prefs.getString("workouts", "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(WorkoutEntry(o.getLong("id"), o.getString("date"), o.getString("type"), o.getString("title"), o.getString("time"), o.optString("duration"), o.optString("notes"), o.optBoolean("done"), reminderFromJson(o.optJSONObject("reminder"))))
            }
        }
    }.getOrDefault(emptyList())
}

fun saveWorkouts(prefs: SharedPreferences, list: List<WorkoutEntry>) {
    val arr = JSONArray(); list.forEach { w -> arr.put(JSONObject().put("id", w.id).put("date", w.date).put("type", w.type).put("title", w.title).put("time", w.time).put("duration", w.durationText).put("notes", w.notes).put("done", w.done).put("reminder", reminderToJson(w.reminder))) }; prefs.edit().putString("workouts", arr.toString()).apply()
}

fun loadRules(prefs: SharedPreferences): List<RecurringRule> {
    val loaded = runCatching {
        val arr = JSONArray(prefs.getString("rules", "[]")); buildList {
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val ds = mutableSetOf<Int>(); val a = o.getJSONArray("weekdays"); for (j in 0 until a.length()) ds += a.getInt(j); add(RecurringRule(o.getLong("id"), o.getString("title"), o.getString("category"), ds, o.getString("time"), o.getString("start"), o.getString("end"), reminderFromJson(o.optJSONObject("reminder")))) }
        }
    }.getOrDefault(emptyList())
    if (loaded.isNotEmpty()) return loaded
    return listOf(
        RecurringRule(1, "Palestra", "Allenamento", setOf(2,4), "17:00", LocalDate.now().toString(), LocalDate.now().plusMonths(10).toString(), ReminderSettings()),
        RecurringRule(2, "Domenica libera", "Giornata libera", setOf(7), "09:00", LocalDate.now().toString(), LocalDate.now().plusYears(1).toString(), ReminderSettings(enabled = false))
    )
}

fun saveRules(prefs: SharedPreferences, list: List<RecurringRule>) { val arr = JSONArray(); list.forEach { r -> val days = JSONArray(); r.weekdays.sorted().forEach { days.put(it) }; arr.put(JSONObject().put("id",r.id).put("title",r.title).put("category",r.category).put("weekdays",days).put("time",r.time).put("start",r.startDate).put("end",r.endDate).put("reminder",reminderToJson(r.reminder))) }; prefs.edit().putString("rules", arr.toString()).apply() }

fun loadEvents(prefs: SharedPreferences): List<CalendarEvent> = runCatching { val arr = JSONArray(prefs.getString("events","[]")); buildList { for (i in 0 until arr.length()) { val o=arr.getJSONObject(i); add(CalendarEvent(o.getLong("id"),o.getString("date"),o.getString("category"),o.getString("title"),o.getString("time"),o.optString("duration"),o.optString("notes"),reminderFromJson(o.optJSONObject("reminder")))) } } }.getOrDefault(emptyList())
fun saveEvents(prefs: SharedPreferences, list: List<CalendarEvent>) { val arr=JSONArray(); list.forEach { e -> arr.put(JSONObject().put("id",e.id).put("date",e.date).put("category",e.category).put("title",e.title).put("time",e.time).put("duration",e.durationText).put("notes",e.notes).put("reminder",reminderToJson(e.reminder))) }; prefs.edit().putString("events",arr.toString()).apply() }

fun reminderToJson(r: ReminderSettings) = JSONObject().put("enabled",r.enabled).put("days",r.daysBefore).put("hours",r.hoursBefore).put("minutes",r.minutesBefore)
fun reminderFromJson(o: JSONObject?): ReminderSettings = if (o == null) ReminderSettings() else ReminderSettings(o.optBoolean("enabled",true),o.optInt("days",0),o.optInt("hours",0),o.optInt("minutes",10))
fun reminderLabel(r: ReminderSettings): String { if (!r.enabled) return "Promemoria disattivato"; val parts=mutableListOf<String>(); if(r.daysBefore>0)parts+="${r.daysBefore}g"; if(r.hoursBefore>0)parts+="${r.hoursBefore}h"; if(r.minutesBefore>0)parts+="${r.minutesBefore}min"; return if(parts.isEmpty())"all'orario esatto" else parts.joinToString(" ")+" prima" }
fun parseTime(s: String): LocalTime = runCatching { LocalTime.parse(s, DateTimeFormatter.ofPattern("H:mm")) }.getOrDefault(LocalTime.of(9,0))
fun formatDateShort(s: String): String = runCatching { LocalDate.parse(s).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(s)
fun formatDateFull(d: LocalDate): String = "${d.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)} ${d.year}"
fun categoryEmoji(c: String): String = when { c.contains("Allen",true)->"🏋️"; c.contains("Cammin",true)->"🚶"; c.contains("libera",true)->"🌴"; c.contains("Spesa",true)->"🛒"; c.contains("Idrat",true)->"💧"; c.contains("Sonno",true)->"😴"; c.contains("personale",true)->"🌟"; else->"📅" }
fun weekdayLabel(days: Set<Int>): String = days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { c -> c.uppercase() } }

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_HIGH).apply { description = "Promemoria attività, pasti e impegni" })
    }
}

fun scheduleIfNeeded(context: Context, title: String, date: LocalDate, time: LocalTime, r: ReminderSettings, requestCode: Int) {
    if (!r.enabled) return
    val target = LocalDateTime.of(date,time).minusDays(r.daysBefore.toLong()).minusHours(r.hoursBefore.toLong()).minusMinutes(r.minutesBefore.toLong())
    val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (millis <= System.currentTimeMillis()) return
    val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", title)
    val pi = PendingIntent.getBroadcast(context, requestCode.absoluteValue, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) } catch (_: SecurityException) { alarm.set(AlarmManager.RTC_WAKEUP, millis, pi) }
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
    val pi = PendingIntent.getBroadcast(context, (r.id.hashCode() * 31 + 7).absoluteValue, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
    catch (_: SecurityException) { alarm.set(AlarmManager.RTC_WAKEUP, millis, pi) }
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
