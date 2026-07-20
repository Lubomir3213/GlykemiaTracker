// VERZIA 18.2 - QR SKENER A CLOUD SYNC
package com.lubos.glykemiatracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FirebaseFirestore
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.widget.Toast
import java.io.File
import com.google.firebase.firestore.Query
import com.lubos.glykemiatracker.ui.theme.GlykemiaTrackerTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.json.JSONArray
import org.json.JSONObject
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle

class MainActivity : ComponentActivity() {
    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val prefs = getSharedPreferences("glykemia_prefs", MODE_PRIVATE)
        val pid = prefs.getString("fb_pid", "") ?: ""
        val apiKey = prefs.getString("fb_api_key", "") ?: ""
        val appId = prefs.getString("fb_app_id", "") ?: ""

        if (pid.isNotEmpty() && apiKey.isNotEmpty() && appId.isNotEmpty()) {
            try {
                val options = FirebaseOptions.Builder()
                    .setProjectId(pid).setApiKey(apiKey).setApplicationId(appId).build()
                if (FirebaseApp.getApps(this).isEmpty()) { FirebaseApp.initializeApp(this, options) }
            } catch (e: Exception) { e.printStackTrace() }
        }

        setContent {
            GlykemiaTrackerTheme(darkTheme = true) {
                val navController = rememberNavController()
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
                val doDatum = rememberSaveable { mutableLongStateOf(calendar.time.time) }
                val calOd = Calendar.getInstance()
                calOd.add(Calendar.MONTH, -3)
                calOd.set(Calendar.HOUR_OF_DAY, 0); calOd.set(Calendar.MINUTE, 0); calOd.set(Calendar.SECOND, 0)
                val odDatum = rememberSaveable { mutableLongStateOf(calOd.time.time) }
                val kcategoriaFilter = rememberSaveable { mutableStateOf("Všetko") }
                val vybranyMobil = rememberSaveable { mutableStateOf(prefs.getString("mobil", "Mobil 1") ?: "Mobil 1") }
                val isIzolovany = rememberSaveable { mutableStateOf(prefs.getBoolean("izolovany", true)) }
                val simulovanyCas = rememberSaveable { mutableLongStateOf(0L) }

                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { innerPadding ->
                        NavHost(navController = navController, startDestination = "hlavna", modifier = Modifier.padding(innerPadding)) {
                            composable("hlavna") { HlavnaObrazovka(navController, vybranyMobil, isIzolovany, simulovanyCas) }
                            composable("tabulka1") { Tabulka1Obrazovka(navController, odDatum, doDatum, kcategoriaFilter, vybranyMobil, isIzolovany) }
                            composable("tabulka2/{scrollToId}") { backStackEntry ->
                                val scrollId = backStackEntry.arguments?.getString("scrollToId") ?: ""
                                Tabulka2Obrazovka(navController, odDatum, doDatum, kcategoriaFilter, vybranyMobil, isIzolovany, scrollId)
                            }
                            composable("graf") { GrafObrazovka(navController, odDatum, doDatum, kcategoriaFilter, vybranyMobil, isIzolovany) }
                            composable("navod") { NavodObrazovka(navController) }
                        }
                    }
                }
            }
        }
    }
}

fun getGlykemiaColor(hodnotaStr: String): Color {
    val h = hodnotaStr.replace(",", ".").toFloatOrNull() ?: return Color.White
    return when {
        h < 3.3f -> Color(0xFF81D4FA) // Hypoglykémia - Modrá
        h <= 5.6f -> Color.White      // Normálna - Biela
        h <= 6.9f -> Color(0xFF4CAF50) // Mierne zvýšená - Zelená
        h <= 7.8f -> Color(0xFFFFEB3B) // Zvýšená - Žltá
        h <= 11.0f -> Color(0xFFFF9800) // Vysoká - Oranžová
        else -> Color(0xFFFF5252)      // Kritická - Červená
    }
}

fun getGlykemiaColorInt(hodnotaStr: String): Int {
    val h = hodnotaStr.replace(",", ".").toFloatOrNull() ?: return android.graphics.Color.BLACK
    return when {
        h < 3.3f -> android.graphics.Color.BLUE
        h <= 5.6f -> android.graphics.Color.BLACK
        h <= 6.9f -> android.graphics.Color.rgb(0, 150, 0)
        h <= 7.8f -> android.graphics.Color.rgb(200, 200, 0)
        h <= 11.0f -> android.graphics.Color.rgb(255, 140, 0)
        else -> android.graphics.Color.RED
    }
}

data class Zaznam(
    val datum: String,
    val ranajkyPred: List<Pair<String, String>> = emptyList(),
    val ranajkyPo: List<Pair<String, String>> = emptyList(),
    val obedPred: List<Pair<String, String>> = emptyList(),
    val obedPo: List<Pair<String, String>> = emptyList(),
    val veceraPred: List<Pair<String, String>> = emptyList(),
    val veceraPo: List<Pair<String, String>> = emptyList(),
    val noc: List<Pair<String, String>> = emptyList()
)

fun saveLocalMeranie(context: android.content.Context, meranie: Map<String, Any>) {
    try {
        val file = File(context.filesDir, "local_data.json")
        val jsonArray = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        val jsonObj = JSONObject()
        meranie.forEach { (k, v) -> if (v is java.util.Date) jsonObj.put(k, v.time) else jsonObj.put(k, v) }
        if (!jsonObj.has("id")) jsonObj.put("id", System.currentTimeMillis().toString())
        jsonArray.put(jsonObj)
        file.writeText(jsonArray.toString())
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadLocalMerania(context: android.content.Context, mobil: String): List<Map<String, Any>> {
    return try {
        val file = File(context.filesDir, "local_data.json")
        if (!file.exists()) return emptyList()
        val jsonArray = JSONArray(file.readText())
        val list = mutableListOf<Map<String, Any>>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val map = mutableMapOf<String, Any>()
            obj.keys().forEach { k -> if (k == "datum_cas") map[k] = com.google.firebase.Timestamp(java.util.Date(obj.getLong(k))) else map[k] = obj.get(k) }
            if (!map.containsKey("id")) map["id"] = "local_${i}_${obj.optLong("datum_cas", System.currentTimeMillis())}"
            if (map["mobil"] == mobil) list.add(map)
        }
        list.sortedByDescending { (it["datum_cas"] as com.google.firebase.Timestamp).seconds }
    } catch (e: Exception) { emptyList() }
}

@Composable
fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context as? Activity; val original = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose { activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

@Composable
fun VerticalFilterButtons(odDatum: MutableLongState, doDatum: MutableLongState, kFilter: MutableState<String>) {
    val context = LocalContext.current; val formatD = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    fun showDP(current: Long, onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(context, { _, y, m, d -> val nCal = Calendar.getInstance().apply { set(y, m, d) }; onDateSelected(nCal.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    Button(onClick = { showDP(odDatum.longValue) { odDatum.longValue = it } }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(4.dp)) { Text("Od: ${formatD.format(java.util.Date(odDatum.longValue))}", fontSize = 10.sp) }
    Button(onClick = { showDP(doDatum.longValue) { doDatum.longValue = it } }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(4.dp)) { Text("Do: ${formatD.format(java.util.Date(doDatum.longValue))}", fontSize = 10.sp) }
    Box(modifier = Modifier.fillMaxWidth()) {
        var expanded by remember { mutableStateOf(false) }
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(4.dp)) { Text("${kFilter.value} ▼", fontSize = 10.sp, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { listOf("Všetko", "Pred a Noc", "Po").forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { kFilter.value = f; expanded = false }) } }
    }
}

@Composable
fun HorizontalFilterRow(odDatum: MutableLongState, doDatum: MutableLongState, kFilter: MutableState<String>) {
    val context = LocalContext.current; val formatD = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    fun showDP(current: Long, onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(context, { _, y, m, d -> val nCal = Calendar.getInstance().apply { set(y, m, d) }; onDateSelected(nCal.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { showDP(odDatum.longValue) { odDatum.longValue = it } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Od: ${formatD.format(java.util.Date(odDatum.longValue))}", fontSize = 10.sp) }
        Button(onClick = { showDP(doDatum.longValue) { doDatum.longValue = it } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Do: ${formatD.format(java.util.Date(doDatum.longValue))}", fontSize = 10.sp) }
        Box(modifier = Modifier.weight(1f)) {
            var expanded by remember { mutableStateOf(false) }
            Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(4.dp)) { Text("${kFilter.value} ▼", fontSize = 10.sp, maxLines = 1) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { listOf("Všetko", "Pred a Noc", "Po").forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { kFilter.value = f; expanded = false }) } }
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
fun HlavnaObrazovka(navController: NavController, vybranyMobil: MutableState<String>, isIzolovany: MutableState<Boolean>, simulovanyCas: MutableLongState) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    val context = LocalContext.current
    // Databáza sa vytvorí LEN vtedy, ak sme inicializovali Firebase cez QR kód
    val db = remember { 
        try { 
            if (com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null 
        } catch(e:Exception) { null } 
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val prefs = context.getSharedPreferences("glykemia_prefs", android.content.Context.MODE_PRIVATE)
    val zoznamPrstov = listOf("L5d", "L5h", "L4d", "L4h", "L3d", "L3h", "L2d", "L2h", "P5d", "P5h", "P4d", "P4h", "P3d", "P3h", "P2d", "P2h")
    val prstKey = if (isIzolovany.value) "prst_index_${vybranyMobil.value}" else "prst_index_shared"
    var prstIndex by rememberSaveable(prstKey) { mutableIntStateOf(prefs.getInt(prstKey, 0)) }
    var menuPrstRozbalene by remember { mutableStateOf(false) }
    var meno1 by remember { mutableStateOf(prefs.getString("meno_1", "") ?: "") }
    var meno2 by remember { mutableStateOf(prefs.getString("meno_2", "") ?: "") }
    var meno3 by remember { mutableStateOf(prefs.getString("meno_3", "") ?: "") }
    val label1 = if (isIzolovany.value) (meno1.ifEmpty { "Osoba 1" }) else "Mobil 1"
    val label2 = if (isIzolovany.value) (meno2.ifEmpty { "Osoba 2" }) else "Mobil 2"
    val label3 = if (isIzolovany.value) (meno3.ifEmpty { "Osoba 3" }) else "Mobil 3"
    val aktualnyLabel = when(vybranyMobil.value) { "Mobil 2" -> label2; "Mobil 3" -> label3; else -> label1 }

    val updateCloudPrst = { newIdx: Int ->
        prstIndex = newIdx
        prefs.edit().putInt(prstKey, newIdx).apply()
        if (!isIzolovany.value && db != null) {
            db.collection("nastavenia").document("aktualny_prst_shared").set(mapOf("index" to newIdx))
        }
    }

    val calendar = Calendar.getInstance().apply { if (simulovanyCas.longValue > 0) timeInMillis = simulovanyCas.longValue }
    val hod = calendar.get(Calendar.HOUR_OF_DAY)
    val pUsek = when (hod) { in 4..9 -> "Raňajky"; in 10..15 -> "Obed"; in 16..21 -> "Večera"; else -> "Noc" }
    val pPredPo = when (hod) { in 5..7, in 10..11, in 16..17 -> "Pred"; in 8..9, in 12..15, in 18..21 -> "Po"; else -> "" }

    var zadanaHodnota by remember { mutableStateOf("") }; var zobrazitPonukuUlozenia by remember { mutableStateOf(false) }
    var menuRozbalene by remember { mutableStateOf(false) }; var stavPredPo by remember { mutableStateOf(pPredPo) }
    var vybranyCasovyUsek by remember { mutableStateOf(pUsek) }; var textPoznamky by remember { mutableStateOf("") }
    var zobrazitPoznamku by remember { mutableStateOf(false) }; val focusRequester = remember { FocusRequester() }
    var pocetKlikov by remember { mutableIntStateOf(0) }; var testMenuZobrazene by remember { mutableStateOf(false) }
    var pocetKlikovStav by remember { mutableIntStateOf(0) }; var cloudMenuZobrazene by remember { mutableStateOf(false) }
    var zoznamMerani by remember { mutableStateOf(listOf<Map<String, Any>>()) }; var stavCloudu by remember { mutableStateOf("PRIPÁJANIE...") }
    
    var isEditingNames by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    
    var tPid by remember { mutableStateOf(prefs.getString("fb_pid", "") ?: "") }
    var tKey by remember { mutableStateOf(prefs.getString("fb_api_key", "") ?: "") }
    var tAid by remember { mutableStateOf(prefs.getString("fb_app_id", "") ?: "") }
    
    var showScanner by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) showScanner = true else Toast.makeText(context, "Kamera je potrebná na skenovanie", Toast.LENGTH_SHORT).show()
    }

    // Odstráni kurzor z políčok, keď sa zapne kamera
    LaunchedEffect(showScanner) {
        if (showScanner) focusManager.clearFocus()
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Kľúče načítané") },
            text = { Text("Pre aktiváciu nového nastavenia cloudu je potrebné aplikáciu reštartovať. Chcete tak urobiť teraz?") },
            confirmButton = {
                Button(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    val mainIntent = Intent.makeRestartActivityTask(intent?.component)
                    context.startActivity(mainIntent)
                    Runtime.getRuntime().exit(0)
                }) { Text("REŠTARTOVAŤ") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("NESKÔR") }
            }
        )
    }

    DisposableEffect(vybranyMobil.value, isIzolovany.value, db) {
        if (isIzolovany.value || db == null) { 
            stavCloudu = if (db == null) "FIREBASE NEINICIALIZOVANÝ" else "LOKÁLNE"
            zoznamMerani = loadLocalMerania(context, vybranyMobil.value).take(20) 
            onDispose {} 
        }
        else {
            val listener = db.collection("merania").orderBy("datum_cas", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, err ->
                    if (err != null) { 
                        stavCloudu = "CHYBA: ${err.code}" 
                        if (err.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Toast.makeText(context, "Prístup zamietnutý! Skontrolujte Rules v Firebase konzole.", Toast.LENGTH_LONG).show()
                        }
                        return@addSnapshotListener 
                    }
                    if (snap != null) { 
                        stavCloudu = if (snap.metadata.isFromCache) "SERVER (Offline/Cache)" else "SERVER (Online)"; 
                        zoznamMerani = snap.documents.mapNotNull { d -> 
                            d.data?.toMutableMap()?.apply { put("id", d.id) } 
                        }.take(20) 
                    }
                }
            onDispose { listener.remove() }
        }
    }
    val formatDatumu = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- VRSTVA 1: HLAVNÝ OBSAH ---
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(Color.Gray.copy(alpha = 0.3f), CircleShape).pointerInput(Unit) { detectTapGestures { navController.navigate("navod") } }, contentAlignment = Alignment.Center) { Text("i", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Box {
                            Box(modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.2f), RoundedCornerShape(10.dp)).pointerInput(Unit) { detectTapGestures(onTap = { updateCloudPrst((prstIndex + 1) % zoznamPrstov.size) }, onLongPress = { menuPrstRozbalene = true }) }.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text(zoznamPrstov[prstIndex], fontSize = 14.sp, color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold, style = TextStyle(shadow = Shadow(color = Color(0xFF00B0FF), blurRadius = 8f))) }
                            DropdownMenu(expanded = menuPrstRozbalene, onDismissRequest = { menuPrstRozbalene = false }) {
                                zoznamPrstov.forEachIndexed { idx, p -> DropdownMenuItem(text = { Text(p, color = if(idx == prstIndex) Color.Cyan else Color.White) }, onClick = { updateCloudPrst(idx); menuPrstRozbalene = false }) }
                            }
                        }
                    }
                    Text("PRST(Next)", fontSize = 8.sp, color = Color.Gray, modifier = Modifier.padding(start = 35.dp))
                }
                Box {
                    Box(modifier = Modifier.background(Color(0xFF6750A4), RoundedCornerShape(20.dp)).pointerInput(Unit) { detectTapGestures(onLongPress = { menuRozbalene = true }) }.padding(horizontal = 24.dp, vertical = 12.dp)
                    ) { Text("$aktualnyLabel  ▼", fontSize = 16.sp, color = Color.White) }
                    DropdownMenu(expanded = menuRozbalene, onDismissRequest = { menuRozbalene = false; isEditingNames = false }) {
                        if (isEditingNames) {
                            Column(modifier = Modifier.padding(8.dp).width(200.dp)) {
                                OutlinedTextField(value = meno1, onValueChange = { meno1 = it; prefs.edit().putString("meno_1", it).apply() }, label = { Text("Osoba 1") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = meno2, onValueChange = { meno2 = it; prefs.edit().putString("meno_2", it).apply() }, label = { Text("Osoba 2") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = meno3, onValueChange = { meno3 = it; prefs.edit().putString("meno_3", it).apply() }, label = { Text("Osoba 3") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { isEditingNames = false; menuRozbalene = false }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("ULOŽIŤ") }
                            }
                        } else {
                            DropdownMenuItem(text = { Text(label1) }, onClick = { vybranyMobil.value = "Mobil 1"; prefs.edit().putString("mobil", "Mobil 1").apply(); menuRozbalene = false })
                            DropdownMenuItem(text = { Text(label2) }, onClick = { vybranyMobil.value = "Mobil 2"; prefs.edit().putString("mobil", "Mobil 2").apply(); menuRozbalene = false })
                            DropdownMenuItem(text = { Text(label3) }, onClick = { vybranyMobil.value = "Mobil 3"; prefs.edit().putString("mobil", "Mobil 3").apply(); menuRozbalene = false })
                            if (isIzolovany.value) { HorizontalDivider(); DropdownMenuItem(text = { Text("ZMENIŤ MENÁ", color = Color.Cyan) }, onClick = { isEditingNames = true }) }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(modifier = Modifier.background(if (isIzolovany.value) Color.Red.copy(alpha = 0.2f) else Color.Green.copy(alpha = 0.2f), RoundedCornerShape(10.dp)).pointerInput(Unit) { detectTapGestures(onTap = { pocetKlikovStav++; if (pocetKlikovStav >= 5) { cloudMenuZobrazene = true; pocetKlikovStav = 0 } }) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (isIzolovany.value) "SÚKROMNÉ" else "ZDIEĽANÉ", fontSize = 10.sp, color = if (isIzolovany.value) Color(0xFFFF5252) else Color(0xFF4CAF50), fontWeight = FontWeight.Bold) }
                    Text(if (isIzolovany.value) "Len v tomto mobile" else stavCloudu, fontSize = 8.sp, color = Color.Gray)
                } 
            }
            if (simulovanyCas.longValue > 0) { Text("SIMULOVANÝ ČAS: ${formatDatumu.format(java.util.Date(simulovanyCas.longValue))}", color = Color.Yellow, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { navController.navigate("tabulka1") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Tabuľka 1", fontSize = 14.sp, maxLines = 1) }; Button(onClick = { navController.navigate("tabulka2/none") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Tabuľka 2", fontSize = 14.sp, maxLines = 1) }; Button(onClick = { navController.navigate("graf") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Graf", fontSize = 14.sp, maxLines = 1) }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(zoznamMerani) { mer ->
                        val ts = mer["datum_cas"] as? com.google.firebase.Timestamp; val d = ts?.toDate(); val datumT = if (d != null) formatDatumu.format(d) else ""
                        val hodnota = mer["hodnota"].toString()
                        val mobRaw = mer["mobil"].toString()
                        val mobSkratka = if (isIzolovany.value) {
                            when(mobRaw) {
                                "Mobil 2" -> if(meno2.isEmpty()) "O2" else meno2.take(2)
                                "Mobil 3" -> if(meno3.isEmpty()) "O3" else meno3.take(2)
                                else -> if(meno1.isEmpty()) "O1" else meno1.take(2)
                            }
                        } else { mobRaw.replace("Mobil ", "Mob ") }
                        
                        val farbaRiadku = getGlykemiaColor(hodnota)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$datumT | $mobSkratka | ${mer["prst"] ?: ""} | ", 
                                fontSize = 15.sp, 
                                color = farbaRiadku,
                                fontFamily = FontFamily.Serif
                            )
                            DecimalAlignedText(
                                hodnota.replace(".", ","), 
                                modifier = Modifier.width(45.dp), 
                                color = farbaRiadku, 
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = " mmol/l", 
                                fontSize = 13.sp, 
                                color = farbaRiadku,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                val ulozMeranie = {
                    val kcat = when (vybranyCasovyUsek) { "Raňajky" -> if (stavPredPo == "Pred") "predRanajkami" else "poRanajkach"; "Obed" -> if (stavPredPo == "Pred") "predObedom" else "poObede"; "Večera" -> if (stavPredPo == "Pred") "predVecerou" else "poVeceri"; else -> "Noc" }
                    val id = System.currentTimeMillis().toString()
                    val meranie = hashMapOf("id" to id, "datum_cas" to java.util.Date(if (simulovanyCas.longValue > 0) simulovanyCas.longValue else System.currentTimeMillis()), "mobil" to vybranyMobil.value, "hodnota" to zadanaHodnota.replace(",", "."), "poznamka" to textPoznamky, "kcategoria" to kcat, "prst" to zoznamPrstov[prstIndex])
                    
                    if (isIzolovany.value) { 
                        saveLocalMeranie(context, meranie)
                        zoznamMerani = loadLocalMerania(context, vybranyMobil.value).take(20) 
                        Toast.makeText(context, "Uložené lokálne", Toast.LENGTH_SHORT).show()
                    } else { 
                        if (db == null) {
                            Toast.makeText(context, "Firebase nie je nastavený!", Toast.LENGTH_LONG).show()
                        } else {
                            db.collection("merania").document(id).set(meranie)
                                .addOnSuccessListener { Toast.makeText(context, "Uložené do cloudu", Toast.LENGTH_SHORT).show() }
                                .addOnFailureListener { e -> Toast.makeText(context, "CHYBA CLOUDU: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                    updateCloudPrst((prstIndex + 1) % zoznamPrstov.size); zobrazitPonukuUlozenia = false; zobrazitPoznamku = false; zadanaHodnota = ""; textPoznamky = ""
                }
                
                if (testMenuZobrazene) {
                    Card(modifier = Modifier.fillMaxWidth().height(350.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("SERVISNÝ PANEL", color = Color.Red, fontWeight = FontWeight.Bold); Text("STROJ ČASU", color = Color.Yellow)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { val c = Calendar.getInstance().apply { if (simulovanyCas.longValue > 0) timeInMillis = simulovanyCas.longValue }; c.add(Calendar.DAY_OF_YEAR, -1); simulovanyCas.longValue = c.timeInMillis }, Modifier.weight(1f)) { Text("-1 Deň") }; Button(onClick = { simulovanyCas.longValue = 0L }, Modifier.weight(1f)) { Text("REÁLNY") }; Button(onClick = { val c = Calendar.getInstance().apply { if (simulovanyCas.longValue > 0) timeInMillis = simulovanyCas.longValue }; c.add(Calendar.DAY_OF_YEAR, 1); simulovanyCas.longValue = c.timeInMillis }, Modifier.weight(1f)) { Text("+1 Deň") }
                            }
                            Button(onClick = { 
                                if (isIzolovany.value) { val file = File(context.filesDir, "local_data.json"); if (file.exists()) file.delete(); zoznamMerani = emptyList(); Toast.makeText(context, "Lokálna databáza vymazaná", Toast.LENGTH_SHORT).show() }
                                else { db?.collection("merania")?.get()?.addOnSuccessListener { it.forEach { doc -> doc.reference.delete() } }; Toast.makeText(context, "Cloud vymazaný", Toast.LENGTH_SHORT).show() }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text(if (isIzolovany.value) "VYMAZAŤ LOKÁLNE DÁTA" else "VYMAZAŤ CELÝ CLOUD") }
                            
                            Button(onClick = { 
                                prefs.edit().clear().apply()
                                val file = File(context.filesDir, "local_data.json")
                                if (file.exists()) file.delete()
                                (context as? Activity)?.finishAffinity()
                                Runtime.getRuntime().exit(0)
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("TOVÁRENSKÝ RESET (KĽÚČE + DÁTA)") }

                            Button(onClick = { testMenuZobrazene = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("ZAVRIEŤ") }
                        }
                    }
                } else if (zobrazitPonukuUlozenia) {
                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().verticalScroll(rememberScrollState())) {
                        if (zobrazitPoznamku) {
                            OutlinedTextField(value = textPoznamky, onValueChange = { textPoznamky = it }, label = { Text("Poznámka") }, modifier = Modifier.fillMaxWidth().wrapContentHeight().focusRequester(focusRequester)); LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            Button(onClick = { ulozMeranie() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(50.dp)) { Text("ULOŽIŤ") }
                        } else {
                            listOf("Raňajky", "Obed", "Večera", "Noc").forEach { jedlo ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    if (vybranyCasovyUsek == jedlo && jedlo != "Noc") { Button(onClick = { stavPredPo = "Pred" }, modifier = Modifier.weight(0.6f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (stavPredPo == "Pred") Color(0xFF4CAF50) else Color.Gray), contentPadding = PaddingValues(0.dp)) { Text("Pred", fontSize = 12.sp) } } else Spacer(modifier = Modifier.weight(0.6f))
                                    Spacer(Modifier.width(4.dp)); Button(onClick = { vybranyCasovyUsek = jedlo }, modifier = Modifier.weight(1f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (vybranyCasovyUsek == jedlo) Color(0xFF4CAF50) else Color.Gray), contentPadding = PaddingValues(0.dp)) { Text(jedlo, fontSize = 13.sp) }; Spacer(Modifier.width(4.dp))
                                    if (vybranyCasovyUsek == jedlo && jedlo != "Noc") { Button(onClick = { stavPredPo = "Po" }, modifier = Modifier.weight(0.6f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (stavPredPo == "Po") Color(0xFF4CAF50) else Color.Gray), contentPadding = PaddingValues(0.dp)) { Text("Po", fontSize = 12.sp) } } else Spacer(modifier = Modifier.weight(0.6f))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Button(onClick = { zobrazitPoznamku = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Poznámka") }; Spacer(Modifier.width(8.dp)); Button(onClick = { ulozMeranie() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("ULOŽIŤ") } }
                        }
                    }
                } else {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0xFFFFEB3B), RoundedCornerShape(12.dp)).pointerInput(Unit) { detectTapGestures(onTap = { pocetKlikov++; if (pocetKlikov >= 5) { testMenuZobrazene = true; pocetKlikov = 0 } }, onLongPress = { testMenuZobrazene = false }) }.padding(16.dp), contentAlignment = Alignment.Center) { Text(text = zadanaHodnota.ifEmpty { "0" }, fontSize = 42.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                            val klavesy = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("LOGO", "0", "LOGO"))
                            Column(modifier = Modifier.weight(3.2f).fillMaxHeight()) {
                                klavesy.forEach { r -> 
                                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) { 
                                        r.forEach { z -> 
                                            when {
                                                z == "LOGO" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(2.dp)
                                                            .fillMaxHeight(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Image(
                                                            painter = painterResource(id = R.drawable.asklepios3),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(40.dp),
                                                            alpha = 0.9f
                                                        )
                                                    }
                                                }
                                                z.isEmpty() -> Spacer(Modifier.weight(1f))
                                                else -> Button(onClick = { val p = zadanaHodnota.split(","); if (p.size < 2 || p[1].isEmpty()) zadanaHodnota += z }, modifier = Modifier.weight(1f).padding(2.dp).fillMaxHeight()) { Text(z, fontSize = 26.sp) }
                                            }
                                        } 
                                    } 
                                }
                            }
                            Column(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                                Button(onClick = { if (zadanaHodnota.isNotEmpty()) zadanaHodnota = zadanaHodnota.dropLast(1) }, modifier = Modifier.fillMaxWidth().weight(1f).padding(2.dp).fillMaxHeight(), contentPadding = PaddingValues(0.dp)) { Text("⌫", fontSize = 24.sp) }
                                Button(onClick = { if (!zadanaHodnota.contains(",")) { if (zadanaHodnota.isEmpty()) zadanaHodnota = "0," else zadanaHodnota += "," } }, modifier = Modifier.fillMaxWidth().weight(1f).padding(2.dp)) { Text(",", fontSize = 26.sp) }
                                Button(onClick = { val f = zadanaHodnota.replace(",", ".").toFloatOrNull(); if (f == null || f < 1.0 || f > 99.9) Toast.makeText(context, "Chyba", Toast.LENGTH_SHORT).show() else zobrazitPonukuUlozenia = true }, modifier = Modifier.fillMaxWidth().weight(1f).padding(2.dp).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), contentPadding = PaddingValues(0.dp)) { Text("OK", fontSize = 24.sp) }
                            }
                        }
                    }
                }
            }
        }

        // --- VRSTVA 2: PREKRYTIE NASTAVENÍ CLOUDU (Totálna izolácia) ---
        if (cloudMenuZobrazene) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF121212)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // --- SCROLLOVATEĽNÝ OBSAH (Vrch a stred) ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("NASTAVENIA CLOUDU", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        
                        if (showScanner) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx)
                                        val executor = Executors.newSingleThreadExecutor()
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = androidx.camera.core.Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                            val scanner = BarcodeScanning.getClient()
                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                            
                                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null) {
                                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                                    scanner.process(image)
                                                        .addOnSuccessListener { barcodes ->
                                                            if (showRestartDialog) return@addOnSuccessListener // Poistka: ak už dialóg svieti, nič nerob
                                                            for (barcode in barcodes) {
                                                                val rawValue = barcode.rawValue ?: continue
                                                                try {
                                                                    val json = JSONObject(rawValue)
                                                                    val nPid = json.optString("pid", "")
                                                                    val nKey = json.optString("key", "")
                                                                    val nAid = json.optString("aid", "")
                                                                    
                                                                    if (nPid.isNotEmpty() && nKey.isNotEmpty() && nAid.isNotEmpty()) {
                                                                        tPid = nPid; tKey = nKey; tAid = nAid
                                                                        // Uložíme kľúče AJ automaticky vypneme izoláciu (režim Súkromné)
                                                                        prefs.edit()
                                                                            .putString("fb_pid", nPid)
                                                                            .putString("fb_api_key", nKey)
                                                                            .putString("fb_app_id", nAid)
                                                                            .putBoolean("izolovany", false) 
                                                                            .apply()
                                                                        isIzolovany.value = false
                                                                        showScanner = false
                                                                        showRestartDialog = true
                                                                    }
                                                                } catch (e: Exception) { }
                                                            }
                                                        }
                                                        .addOnCompleteListener { imageProxy.close() }
                                                }
                                            }
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(context as androidx.lifecycle.LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                                        }, ContextCompat.getMainExecutor(ctx))
                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { showScanner = false },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).size(28.dp)
                                ) {
                                    Text("X", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        // Táto pružná medzera odtlačí políčka dole k tlačidlám
                        Spacer(modifier = Modifier.weight(1f).heightIn(min = 20.dp))

                        Button(
                            onClick = { isIzolovany.value = !isIzolovany.value; prefs.edit().putBoolean("izolovany", isIzolovany.value).apply() },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { 
                            Text(if (isIzolovany.value) "AKTIVOVAŤ ZDIEĽANIE" else "DEAKTIVOVAŤ ZDIEĽANIE", fontSize = 13.sp) 
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val tfModifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) showScanner = false }
                        OutlinedTextField(value = tPid, onValueChange = { tPid = it; prefs.edit().putString("fb_pid", it).apply() }, label = { Text("Project ID", fontSize = 11.sp) }, modifier = tfModifier, textStyle = TextStyle(fontSize = 14.sp), singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = tKey, onValueChange = { tKey = it; prefs.edit().putString("fb_api_key", it).apply() }, label = { Text("API Key", fontSize = 11.sp) }, modifier = tfModifier, textStyle = TextStyle(fontSize = 14.sp), singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = tAid, onValueChange = { tAid = it; prefs.edit().putString("fb_app_id", it).apply() }, label = { Text("App ID", fontSize = 11.sp) }, modifier = tfModifier, textStyle = TextStyle(fontSize = 14.sp), singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // --- PEVNÁ SPODNÁ ČASŤ (Tlačidlá na spodku) ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                focusManager.clearFocus()
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { showScanner = true } 
                                else { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                            },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) { Text("SKENOVAŤ QR", fontSize = 13.sp) }
                        
                        Button(
                            onClick = { cloudMenuZobrazene = false },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) { Text("ZAVRIEŤ", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

fun mapK(k: String?): String { return when(k) { "Raňajky Pred" -> "predRanajkami"; "Raňajky Po" -> "poRanajkach"; "Obed Pred" -> "predObedom"; "Obed Po" -> "poObede"; "Večera Pred" -> "predVecerou"; "Večera Po" -> "poVeceri"; else -> k ?: "" } }

@Composable
fun DecimalAlignedText(hodnota: String, modifier: Modifier = Modifier, color: Color = Color.White, fontSize: androidx.compose.ui.unit.TextUnit = 10.sp, fontFamily: FontFamily = FontFamily.Default) {
    val casti = hodnota.split(",")
    val pred = casti[0]
    val po = if (casti.size > 1) "," + casti[1] else ""
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        Text(text = pred, color = color, fontSize = fontSize, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontFamily = fontFamily)
        Text(text = po, color = color, fontSize = fontSize, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, fontFamily = fontFamily)
    }
}

fun filterKategorii(vsetky: List<Map<String, Any>>, filter: String): List<Map<String, Any>> {
    return when (filter) {
        "Pred a Noc" -> vsetky.filter { val k = (it["kcategoria"] ?: it["kategoria"]) as? String; k?.startsWith("pred") == true || mapK(k).startsWith("pred") || k == "Noc" }
        "Po" -> vsetky.filter { val k = (it["kcategoria"] ?: it["kategoria"]) as? String; k?.startsWith("po") == true || mapK(k).startsWith("po") }
        else -> vsetky
    }
}

@Composable
fun Tabulka1Obrazovka(navController: NavController, odDatum: MutableLongState, doDatum: MutableLongState, kFilter: MutableState<String>, vybranyMobil: MutableState<String>, isIzolovany: MutableState<Boolean>) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val context = LocalContext.current; val db = remember { try { FirebaseFirestore.getInstance() } catch(e:Exception) { null } }
    val formatD = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    var zoznam by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    DisposableEffect(vybranyMobil.value, isIzolovany.value, odDatum.longValue, doDatum.longValue, kFilter.value) {
        if (isIzolovany.value || db == null) { zoznam = filterKategorii(loadLocalMerania(context, vybranyMobil.value), kFilter.value); onDispose {} }
        else {
            val listener = db.collection("merania")
                .whereGreaterThanOrEqualTo("datum_cas", java.util.Date(odDatum.longValue))
                .whereLessThanOrEqualTo("datum_cas", java.util.Date(doDatum.longValue))
                .orderBy("datum_cas", Query.Direction.DESCENDING).limit(500)
                .addSnapshotListener { snap, err -> 
                    if (err != null) {
                        android.util.Log.e("Firestore", "Chyba Tabulka1: ${err.message}")
                        return@addSnapshotListener
                    }
                    if (snap != null) zoznam = filterKategorii(snap.documents.mapNotNull { d -> d.data?.toMutableMap()?.apply { put("id", d.id) } }, kFilter.value) 
                }
            onDispose { listener.remove() }
        }
    }
    val meraniaPodlaDni = zoznam.groupBy { 
        val ts = it["datum_cas"]
        val date = when (ts) {
            is com.google.firebase.Timestamp -> ts.toDate()
            is java.util.Date -> ts
            is Long -> java.util.Date(ts)
            else -> java.util.Date()
        }
        val cal = Calendar.getInstance().apply { time = date }
        if (cal.get(Calendar.HOUR_OF_DAY) < 5) cal.add(Calendar.DAY_OF_YEAR, -1)
        formatD.format(cal.time)
    }
    val zoradeneDni = meraniaPodlaDni.keys.sortedByDescending { try { formatD.parse(it) } catch(e:Exception) { java.util.Date(0) } }

    Row(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(8.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxWidth().background(Color.DarkGray).padding(4.dp)) { 
                Text("Dátum (${zoznam.size})", Modifier.weight(1.2f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp)
                Text("Raňajky", Modifier.weight(2f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp); Text("Obed", Modifier.weight(2f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp); Text("Večera", Modifier.weight(2f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp); Text("Noc", Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp) 
            }
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF333333)).padding(2.dp)) {
                Spacer(Modifier.weight(1.2f)); repeat(3) { Row(Modifier.weight(2f)) { Text("Pred", Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 8.sp); Text("Po", Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 8.sp) } }; Spacer(Modifier.weight(1f))
            }
            LazyColumn(Modifier.fillMaxSize()) { 
                items(zoradeneDni) { den -> 
                    val m = meraniaPodlaDni[den] ?: emptyList()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { 
                        Text(den, Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center, color = Color.White)
                        listOf("predRanajkami", "poRanajkach", "predObedom", "poObede", "predVecerou", "poVeceri", "Noc").forEach { k -> 
                            val v = m.filter { val kc = (it["kcategoria"] ?: it["kategoria"]) as? String; kc == k || mapK(kc) == k }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { 
                                v.forEach { mer ->
                                    val hodn = mer["hodnota"].toString().replace(".", ",")
                                    Box(modifier = Modifier.clickable { navController.navigate("tabulka2/${mer["id"]}") }.padding(2.dp)) {
                                        DecimalAlignedText(hodn, modifier = Modifier.fillMaxWidth(), color = getGlykemiaColor(hodn))
                                    }
                                } 
                            } 
                        } 
                    }
                    HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
                } 
            }
        }
        Column(modifier = Modifier.width(160.dp).fillMaxHeight().padding(start = 8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { navController.navigate("hlavna") }, Modifier.fillMaxWidth()) { Text("Hlavná") }; Button(onClick = { navController.navigate("tabulka2/none") }, Modifier.fillMaxWidth()) { Text("Tabuľka 2") }; Button(onClick = { navController.navigate("graf") }, Modifier.fillMaxWidth()) { Text("Graf") }
            VerticalFilterButtons(odDatum, doDatum, kFilter)
            Button(onClick = { 
                val exportData = zoradeneDni.map { d -> 
                    val m = meraniaPodlaDni[d] ?: emptyList()
                    Zaznam(d, 
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="predRanajkami"||mapK(k)=="predRanajkami"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")},
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="poRanajkach"||mapK(k)=="poRanajkach"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")},
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="predObedom"||mapK(k)=="predObedom"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")},
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="poObede"||mapK(k)=="poObede"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")},
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="predVecerou"||mapK(k)=="predVecerou"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")},
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="poVeceri"||mapK(k)=="poVeceri"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")},
                        m.filter{val k=(it["kcategoria"]?:it["kategoria"]) as? String; k=="Noc"||mapK(k)=="Noc"}.map{it["hodnota"].toString() to (it["id"]?.toString()?:"")}) 
                }
                PdfExporter.exportovatAOdoslatPDF(context, exportData, "Súhrn glykémie") 
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth()) { Text("PDF a Tlač") }
        }
    }
}

@Composable
fun Tabulka2Obrazovka(navController: NavController, odDatum: MutableLongState, doDatum: MutableLongState, kFilter: MutableState<String>, vybranyMobil: MutableState<String>, isIzolovany: MutableState<Boolean>, scrollToId: String = "") {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    val context = LocalContext.current; val db = remember { try { FirebaseFirestore.getInstance() } catch(e:Exception) { null } }
    val formatD = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val prefs = context.getSharedPreferences("glykemia_prefs", android.content.Context.MODE_PRIVATE)
    var zoznam by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    val listState = rememberLazyListState()
    val meno1 = prefs.getString("meno_1", "") ?: ""; val meno2 = prefs.getString("meno_2", "") ?: ""; val meno3 = prefs.getString("meno_3", "") ?: ""

    var meranieNaEditaciu by remember { mutableStateOf<Map<String, Any>?>(null) }
    var textEditovanejPoznamky by remember { mutableStateOf("") }

    if (meranieNaEditaciu != null) {
        AlertDialog(
            onDismissRequest = { meranieNaEditaciu = null },
            title = { Text("Upraviť poznámku") },
            text = {
                OutlinedTextField(
                    value = textEditovanejPoznamky,
                    onValueChange = { textEditovanejPoznamky = it },
                    label = { Text("Poznámka") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val id = meranieNaEditaciu!!["id"].toString()
                    val novaPoznamka = textEditovanejPoznamky
                    if (isIzolovany.value) {
                        try {
                            val file = File(context.filesDir, "local_data.json")
                            val jsonArray = JSONArray(file.readText())
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                if (obj.optString("id") == id) { obj.put("poznamka", novaPoznamka) }
                            }
                            file.writeText(jsonArray.toString())
                            zoznam = filterKategorii(loadLocalMerania(context, vybranyMobil.value), kFilter.value)
                        } catch (e: Exception) { e.printStackTrace() }
                    } else {
                        db?.collection("merania")?.document(id)?.update("poznamka", novaPoznamka)
                    }
                    meranieNaEditaciu = null
                }) { Text("ULOŽIŤ") }
            },
            dismissButton = { TextButton(onClick = { meranieNaEditaciu = null }) { Text("ZRUŠIŤ") } }
        )
    }

    DisposableEffect(vybranyMobil.value, isIzolovany.value, odDatum.longValue, doDatum.longValue, kFilter.value) {
        if (isIzolovany.value || db == null) { zoznam = filterKategorii(loadLocalMerania(context, vybranyMobil.value), kFilter.value); onDispose {} }
        else {
            val listener = db.collection("merania")
                .whereGreaterThanOrEqualTo("datum_cas", java.util.Date(odDatum.longValue))
                .whereLessThanOrEqualTo("datum_cas", java.util.Date(doDatum.longValue))
                .orderBy("datum_cas", Query.Direction.DESCENDING).limit(500)
                .addSnapshotListener { snap, err -> 
                    if (err != null) {
                        android.util.Log.e("Firestore", "Chyba Tabulka1: ${err.message}")
                        return@addSnapshotListener
                    }
                    if (snap != null) zoznam = filterKategorii(snap.documents.mapNotNull { d -> d.data?.toMutableMap()?.apply { put("id", d.id) } }, kFilter.value) 
                }
            onDispose { listener.remove() }
        }
    }
    LaunchedEffect(zoznam, scrollToId) { 
        if (scrollToId != "none" && scrollToId.isNotEmpty()) {
            val idx = zoznam.indexOfFirst { it["id"] == scrollToId }
            if (idx >= 0) {
                val targetScrollIdx = if (idx > 3) idx - 3 else idx
                listState.animateScrollToItem(targetScrollIdx)
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { navController.navigate("hlavna") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Hlavná", fontSize = 13.sp) }; Button(onClick = { navController.navigate("tabulka1") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Tabuľka 1", fontSize = 13.sp) }; Button(onClick = { navController.navigate("graf") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Graf", fontSize = 13.sp) }
            Button(onClick = { 
                val exportData = zoznam.map { val ts = it["datum_cas"] as? com.google.firebase.Timestamp; val dStr = if (ts != null) formatD.format(ts.toDate()) else ""; Zaznam(dStr, listOf(it["hodnota"].toString() to ""), listOf("" to it["kcategoria"].toString()), listOf("" to it["mobil"].toString()), listOf("" to it["poznamka"].toString())) }
                PdfExporter.exportovatDetailPDF(context, exportData) 
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("PDF a Tlač", fontSize = 13.sp) }
        }
        HorizontalFilterRow(odDatum, doDatum, kFilter)
        Row(Modifier.fillMaxWidth().background(Color.DarkGray).padding(8.dp)) {
            Text("Dátum a čas", Modifier.weight(1.5f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(if(isIzolovany.value) "Osoba" else "Mob/Oso", Modifier.weight(0.5f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text("Prst", Modifier.weight(0.4f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text("Kateg", Modifier.weight(0.7f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text("Hodn", Modifier.weight(0.5f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text("Poznámka", Modifier.weight(1.5f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(zoznam) { _, m ->
                val ts = m["datum_cas"] as? com.google.firebase.Timestamp; val dt = ts?.toDate(); val dText = if (dt != null) formatD.format(dt) else ""
                val mobRaw = m["mobil"].toString()
                val mobSkratka = if (isIzolovany.value) {
                    when(mobRaw) {
                        "Mobil 2" -> if(meno2.isEmpty()) "O2" else meno2.take(2)
                        "Mobil 3" -> if(meno3.isEmpty()) "O3" else meno3.take(2)
                        else -> if(meno1.isEmpty()) "O1" else meno1.take(2)
                    }
                } else { mobRaw.replace("Mobil ", "Mob ") }
                val kcat = (m["kcategoria"] ?: m["kategoria"]) as? String ?: ""
                val kSkratka = kcat.replace("predRanajkami", "prRaň").replace("poRanajkach", "poRaň").replace("predObedom", "prObe").replace("poObede", "poObe").replace("predVecerou", "prVeč").replace("poVeceri", "poVeč").replace("Raňajky Pred", "prRaň").replace("Raňajky Po", "poRaň").replace("Obed Pred", "prObe").replace("Obed Po", "poObe").replace("Večera Pred", "prVeč").replace("Večera Po", "poVeč")
                val isHighlighted = m["id"] == scrollToId
                val hodnota = m["hodnota"].toString().replace(".", ",")
                Row(Modifier.fillMaxWidth().background(if (isHighlighted) Color(0x66FFEB3B) else Color.Transparent).padding(vertical = 4.dp)) {
                    Text(dText, Modifier.weight(1.5f), fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                    Text(mobSkratka, Modifier.weight(0.5f), fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                    Text(m["prst"]?.toString() ?: "", Modifier.weight(0.4f), fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                    Text(kSkratka, Modifier.weight(0.7f), fontSize = 10.sp, color = Color.White, maxLines = 1, textAlign = TextAlign.Center)
                    DecimalAlignedText(hodnota, modifier = Modifier.weight(0.5f), color = getGlykemiaColor(hodnota))
                    Text(m["poznamka"].toString(), Modifier.weight(1.5f).padding(horizontal = 2.dp).clickable { 
                        meranieNaEditaciu = m
                        textEditovanejPoznamky = m["poznamka"].toString()
                    }, fontSize = 10.sp, color = Color.White)
                }
                HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun GrafObrazovka(navController: NavController, odDatum: MutableLongState, doDatum: MutableLongState, kFilter: MutableState<String>, vybranyMobil: MutableState<String>, isIzolovany: MutableState<Boolean>) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val context = LocalContext.current; val db = remember { try { FirebaseFirestore.getInstance() } catch(e:Exception) { null } }
    var zoznam by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    val formatXFull = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val formatTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    DisposableEffect(vybranyMobil.value, isIzolovany.value, odDatum.longValue, doDatum.longValue, kFilter.value) {
        if (isIzolovany.value || db == null) { 
            zoznam = filterKategorii(loadLocalMerania(context, vybranyMobil.value), kFilter.value)
                .sortedBy { (it["datum_cas"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
            onDispose {} 
        }
        else {
            val listener = db.collection("merania")
                .whereGreaterThanOrEqualTo("datum_cas", java.util.Date(odDatum.longValue))
                .whereLessThanOrEqualTo("datum_cas", java.util.Date(doDatum.longValue))
                .orderBy("datum_cas", Query.Direction.DESCENDING).limit(500)
                .addSnapshotListener { snap, _ -> 
                    if (snap != null) {
                        zoznam = filterKategorii(snap.documents.mapNotNull { d -> d.data?.toMutableMap()?.apply { put("id", d.id) } }, kFilter.value)
                            .sortedBy { (it["datum_cas"] as? com.google.firebase.Timestamp)?.seconds ?: 0L } 
                    }
                }
            onDispose { listener.remove() }
        }
    }
    Row(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(8.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text("Trend glykémie (${zoznam.size})", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.White).padding(24.dp)) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().pointerInput(zoznam) { 
                    detectTapGestures { offset -> 
                        if (zoznam.size > 1) { 
                            val cW = size.width
                            val clickX = offset.x
                            val clickY = offset.y
                            val step = cW / (zoznam.size - 1)
                            val cH = size.height
                            val h = zoznam.map { it["hodnota"].toString().toFloatOrNull() ?: 5f }
                            val maxV = (h.maxOrNull() ?: 10f) + 1f
                            val minV = ((h.minOrNull() ?: 4f) - 1f).coerceAtLeast(0f)
                            val range = maxV - minV
                            val closestIndex = zoznam.indices.minByOrNull { i ->
                                val pointX = i * step
                                val pointY = cH - ((h[i] - minV) / range * cH)
                                val dx = pointX - clickX
                                val dy = pointY - clickY
                                dx * dx + dy * dy
                            } ?: -1
                            if (closestIndex >= 0) {
                                val selectedId = zoznam[closestIndex]["id"]?.toString() ?: ""
                                navController.navigate("tabulka2/$selectedId") 
                            }
                        } 
                    } 
                }) {
                    val paint = Paint().apply { color = android.graphics.Color.BLACK; textSize = 24f }
                    drawLine(Color.Black, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2f)
                    drawLine(Color.Black, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = 2f)
                    if (zoznam.size > 1) {
                        val h = zoznam.map { it["hodnota"].toString().toFloatOrNull() ?: 5f }; val maxV = (h.maxOrNull() ?: 10f) + 1f; val minV = ((h.minOrNull() ?: 4f) - 1f).coerceAtLeast(0f)
                        val cW = size.width; val cH = size.height; val range = maxV - minV
                        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", maxV), -60f, 20f, paint); drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", minV), -60f, cH, paint)
                        val mid1 = minV + range * 0.33f; val mid2 = minV + range * 0.66f
                        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", mid1), -60f, cH - (0.33f * cH), paint); drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", mid2), -60f, cH - (0.66f * cH), paint)
                        val path = androidx.compose.ui.graphics.Path(); var lastDateStr = ""
                        zoznam.forEachIndexed { i, m ->
                            val hodn = m["hodnota"].toString()
                            val x = i * (cW / (zoznam.size - 1)); val y = cH - (((hodn.toFloatOrNull() ?: 5f) - minV) / range * cH)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y); drawCircle(getGlykemiaColor(hodn), 10f, androidx.compose.ui.geometry.Offset(x, y))
                            if (i % (if (zoznam.size > 10) zoznam.size / 5 else 1) == 0 || i == zoznam.size - 1) {
                                val ts = m["datum_cas"] as? com.google.firebase.Timestamp; val date = ts?.toDate()
                                if (date != null) { val dayStr = SimpleDateFormat("dd.MM", Locale.getDefault()).format(date); val label = if (dayStr != lastDateStr) { lastDateStr = dayStr; formatXFull.format(date) } else formatTime.format(date); drawContext.canvas.nativeCanvas.drawText(label, x - 40f, cH + 30f, paint.apply { textSize = 18f }) }
                            }
                        }
                        drawPath(path, Color.Gray.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                }
            }
        }
        Column(modifier = Modifier.width(160.dp).fillMaxHeight().padding(start = 8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { navController.navigate("hlavna") }, Modifier.fillMaxWidth()) { Text("Hlavná") }; Button(onClick = { navController.navigate("tabulka1") }, Modifier.fillMaxWidth()) { Text("Tabuľka 1") }; Button(onClick = { navController.navigate("tabulka2/none") }, Modifier.fillMaxWidth()) { Text("Tabuľka 2") }
            VerticalFilterButtons(odDatum, doDatum, kFilter); Button(onClick = { PdfExporter.generujPosliPdfGraf(context, zoznam) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("PDF a Tlač") }
        }
    }
}

@Composable
fun NavodObrazovka(navController: NavController) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().background(Color(0xFF121212)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { navController.popBackStack() }) { Text("SPÄŤ") }
            Spacer(Modifier.width(16.dp))
            Text("NÁVOD NA POUŽITIE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SekciaNavodu("O APLIKÁCII", "GlykemiaTracker is moderný a jednoduchý nástroj na sledovanie Vašej hladiny cukru v krvi. Slúži na ukladanie meraní, sledovanie trendov a generovanie reportov, ktoré môžete zdieľať so Svojím lekárom, alebo osobou, ktorá Vás ošetruje.")
            SekciaNavodu("1. ZÁKLADNÉ OVLÁDANIE", "Hodnotu glykémie zadávate na číselníku. Po potvrdení tlačidlom OK sa Vám automaticky v závislosti od času zobrazí časový úsek a stav Pred/Po jedle. Môžete automatickú ponuku prijať, alebo tlačidlami zmeniť. Ak chcete pridať poznámku (obed, sladkosti, hladovka, atď), kliknite na 'POZNÁMKA'. Túto ponuku uzatvoríte tlačidlom 'ULOŽIŤ'.")
            SekciaNavodu("2. PERSONALIZÁCIA (MENÁ)", "V režime SÚKROMNÉ môžete zmeniť mená osôb. Dlho podržte fialové tlačidlo s menom, vyberte 'ZMENIŤ MENÁ', prepíšte ich a uložte. V tabuľkách uvidíte prvé dve písmená mena (napr. Pe pre Peter) alebo O1-O3 (Osoba 1 až Osoba 3, ak nebudete do okienok, ktoré sa Vám zobrazia, zadávať žiadne údaje).")
            SekciaNavodu("3. INTELIGENTNÝ PRST", "Modré tlačidlo vľavo hore navrhuje prst pre ďalší odber (napr. l5d = ľavá ruka malíčkom dole, piaty prst dolná strana, alebo p4h = pravá ruka štvrtý prst prsteník horná strana). Prst sa automaticky posúva po každom uložení. V prípade, že nemôžete urobiť odber z navrhovaného prsta, môžete kliknutím na PRST posunúť na nasledujúci, alebo dlhým pridržaním tlačidla zobraziť všetky možnosti (robiť odbery z palca sa podľa niektorých názorov neodporúča) a vybrať si tú, ktorá Vám vyhovuje.")
            SekciaNavodu("4. ZDIEĽANIE (FIREBASE CLOUD)", "Pre rodinné zdieľanie si vytvorte Firebase projekt. Vložte Project ID, API Key a App ID do aplikácie (5-klik na stavové okno). Nezabudnite v konzole nastaviť 'Rules': allow read, write: if request.time < timestamp.date(2030, 12, 31). Po aktivácii REŠTARTUJTE aplikáciu.")
            SekciaNavodu("5. REPORTY A GRAFY", "Reporty si zobrazíte v tabuľkách a grafoch. Hodnoty v tabuľkách sú zobrazené farebne. Modrá farba je pre hodnoty cukru v krvi menšie ako je normálna hodnota, pre normálne hodnoty je biela farba na tmavom podklade alebo čierna na svetlom podklade, pre vyššie hodnoty sú postupne farby zelená, žltá, oranžová a červená. Kliknutím na hodnotu v Tabuľke 1 alebo na zobrazený bod v grafe sa Vám zobrazí Tabuľka 2 s vyznačenou hodnotou, kde si môžete pozrieť presný čas, prípadne poznámku.")
            Text("Vzhľadom na nejednotnosť dostupných údajov sú tieto farby iba orientačné.", color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            SekciaNavodu("Pokračovanie k reportom", "Kliknutím na 'PDF A TLAČ' vygenerujete súbor, ktorý môžete ihneď odoslať ako prílohu emailu (ak ste pripojení na internet a máte vlastnú emailovú adresu), prílohu SMS, vytlačiť na tlačiarni (mobil musí byť pripojený k tlačiarni) atď. podľa ponuky, ktorá sa Vám zobrazí.")
            SekciaNavodu("6. SERVISNÝ PANEL", "Päťnásobným kliknutím na žlté pole s číslom na hlavnej obrazovke otvoríte servisný panel pre vymazanie databázy alebo testovací 'Stroj času'.")
            Text("Prajeme Vám veľa zdravia a pevne veríme, že GlykemiaTracker Vám pomôže mať Vašu cukrovku pod lepšou kontrolou!", color = Color.Yellow, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SekciaNavodu(titul: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titul, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun HlavnaObrazovkaPreview() {
    GlykemiaTrackerTheme(darkTheme = true) {
        Surface(color = Color(0xFF121212)) {
            HlavnaObrazovka(
                rememberNavController(),
                remember { mutableStateOf("Mobil 1") },
                remember { mutableStateOf(false) },
                remember { mutableLongStateOf(0L) }
            )
        }
    }
}
