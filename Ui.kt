package com.example.remot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun RemotApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasIr = remember { IrEngine.hasIrEmitter(ctx) }
    var profiles by remember { mutableStateOf(IrEngine.loadProfiles(ctx)) }
    var selectedId by remember { mutableStateOf(profiles.firstOrNull()?.id ?: "") }
    var statusMessage by remember {
        mutableStateOf(
            if (hasIr) "جاهز للتحكم بالشاشات والرسيفرات"
            else "الهاتف لا يحتوي على مرسل IR مدمج"
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showAddButtonDialog by remember { mutableStateOf(false) }

    val currentProfile = profiles.find { it.id == selectedId } ?: profiles.firstOrNull()

    fun findAction(vararg keys: String): RemoteAction? {
        val list = currentProfile?.actions.orEmpty()
        return list.firstOrNull { act ->
            keys.any { k -> act.id.equals(k, ignoreCase = true) || act.label.contains(k, ignoreCase = true) }
        }
    }

    fun triggerAction(act: RemoteAction?) {
        if (act == null) {
            statusMessage = "الزر غير مضاف في هذا الجهاز"
            return
        }
        val res = IrEngine.transmit(ctx, act)
        statusMessage = res.fold(
            onSuccess = { "✅ $it" },
            onFailure = { "❌ ${it.message}" }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Add Device
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "remot",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasIr) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { showAddButtonDialog = true }) {
                    Text("+ زر")
                }
                Button(onClick = { showAddDeviceDialog = true }) {
                    Text("+ جهاز")
                }
            }
        }

        // Device Selector Strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { prof ->
                val active = prof.id == currentProfile?.id
                if (active) {
                    Button(
                        onClick = {
                            selectedId = prof.id
                            statusMessage = "تم اختيار: ${prof.name}"
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(prof.name)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            selectedId = prof.id
                            statusMessage = "تم اختيار: ${prof.name}"
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(prof.name)
                    }
                }
            }
        }

        // Top Control Row: Power, Mute, Auto-Scan
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { triggerAction(findAction("pwr", "POWER")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("⏻", fontSize = 24.sp, color = Color.White)
            }

            OutlinedButton(
                enabled = !scanning,
                onClick = {
                    val acts = currentProfile?.actions.orEmpty()
                    scanning = true
                    scope.launch {
                        val res = IrEngine.transmitAutoScan(ctx, acts) { msg ->
                            statusMessage = msg
                        }
                        statusMessage = res.fold(
                            onSuccess = { "✅ $it" },
                            onFailure = { "❌ ${it.message}" }
                        )
                        scanning = false
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (scanning) "جاري المسح..." else "مسح سريع للأكواد")
            }

            OutlinedButton(
                onClick = { triggerAction(findAction("mute", "MUTE")) },
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("🔇", fontSize = 20.sp)
            }
        }

        // Source, Menu, Exit row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { triggerAction(findAction("src", "SOURCE", "INPUT")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SOURCE")
            }
            FilledTonalButton(
                onClick = { triggerAction(findAction("menu", "MENU")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MENU")
            }
            FilledTonalButton(
                onClick = { triggerAction(findAction("exit", "EXIT", "BACK")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("EXIT")
            }
        }

        // D-Pad (Navigation + OK)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { triggerAction(findAction("up", "▲")) },
                    modifier = Modifier.width(88.dp).height(46.dp)
                ) { Text("▲", fontSize = 18.sp) }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { triggerAction(findAction("left", "◀")) },
                        modifier = Modifier.width(76.dp).height(52.dp)
                    ) { Text("◀", fontSize = 18.sp) }

                    Button(
                        onClick = { triggerAction(findAction("ok", "OK")) },
                        shape = CircleShape,
                        modifier = Modifier.size(66.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { triggerAction(findAction("right", "▶")) },
                        modifier = Modifier.width(76.dp).height(52.dp)
                    ) { Text("▶", fontSize = 18.sp) }
                }

                FilledTonalButton(
                    onClick = { triggerAction(findAction("down", "▼")) },
                    modifier = Modifier.width(88.dp).height(46.dp)
                ) { Text("▼", fontSize = 18.sp) }
            }
        }

        // Volume & Channel Rockers (SURE Style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { triggerAction(findAction("vol_up", "VOL +")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("VOL +", fontWeight = FontWeight.Bold) }

                    Text("VOLUME", style = MaterialTheme.typography.labelSmall)

                    Button(
                        onClick = { triggerAction(findAction("vol_dn", "VOL -")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("VOL -", fontWeight = FontWeight.Bold) }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { triggerAction(findAction("ch_up", "CH +")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("CH ▲", fontWeight = FontWeight.Bold) }

                    Text("CHANNEL", style = MaterialTheme.typography.labelSmall)

                    Button(
                        onClick = { triggerAction(findAction("ch_dn", "CH -")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("CH ▼", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Extra / Custom buttons for the selected profile
        val extraActions = currentProfile?.actions?.filter { act ->
            act.id !in setOf("pwr", "pwr_alt", "src", "menu", "exit", "mute", "vol_up", "vol_dn", "ch_up", "ch_dn", "ok", "up", "down", "left", "right")
        }.orEmpty()

        if (extraActions.isNotEmpty()) {
            Text("أزرار إضافية / مخصصة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            extraActions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { act ->
                        OutlinedButton(
                            onClick = { triggerAction(act) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(act.label)
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Text(
            text = "Developed by Hayel alkouri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }

    if (showAddDeviceDialog) {
        var name by remember { mutableStateOf("") }
        var brand by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(DeviceCategory.TV) }

        AlertDialog(
            onDismissRequest = { showAddDeviceDialog = false },
            title = { Text("إضافة جهاز جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم الجهاز (مثال: شاشة قاعة 1)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("الماركة أو الموديل (اختياري)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeviceCategory.entries.forEach { cat ->
                            if (category == cat) {
                                Button(onClick = { category = cat }) { Text(cat.label) }
                            } else {
                                OutlinedButton(onClick = { category = cat }) { Text(cat.label) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val template = IrEngine.defaultProfiles().firstOrNull { it.category == category }
                            ?: IrEngine.defaultProfiles().first()
                        val newProf = RemoteProfile(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            brand = brand.trim().ifEmpty { category.label },
                            category = category,
                            actions = template.actions.map { it.copy(id = "${it.id}_${UUID.randomUUID().toString().take(4)}") }
                        )
                        val updated = profiles + newProf
                        profiles = updated
                        selectedId = newProf.id
                        IrEngine.saveProfiles(ctx, updated)
                        showAddDeviceDialog = false
                        statusMessage = "تم إضافة جهاز: ${newProf.name}"
                    }
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDeviceDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showAddButtonDialog && currentProfile != null) {
        var btnLabel by remember { mutableStateOf("") }
        var codeInput by remember { mutableStateOf("") }
        var freqText by remember { mutableStateOf("38000") }

        AlertDialog(
            onDismissRequest = { showAddButtonDialog = false },
            title = { Text("إضافة زر إلى ${currentProfile.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = btnLabel,
                        onValueChange = { btnLabel = it },
                        label = { Text("اسم الزر (مثال: HDMI 1)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("كود Hex أو توقيتات النبضات") }
                    )
                    OutlinedTextField(
                        value = freqText,
                        onValueChange = { freqText = it },
                        label = { Text("التردد بالهرتز (افتراضي 38000)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val freq = freqText.trim().toIntOrNull() ?: 38000
                    val parsed = runCatching { IrEngine.parseCodeInput(codeInput, freq) }
                    parsed.fold(
                        onSuccess = { (finalFreq, pattern) ->
                            if (btnLabel.isNotBlank()) {
                                val newAct = RemoteAction(
                                    id = UUID.randomUUID().toString(),
                                    label = btnLabel.trim(),
                                    frequencyHz = finalFreq,
                                    patternMicros = pattern
                                )
                                val updated = profiles.map { p ->
                                    if (p.id == currentProfile.id) p.copy(actions = p.actions + newAct) else p
                                }
                                profiles = updated
                                IrEngine.saveProfiles(ctx, updated)
                                showAddButtonDialog = false
                                statusMessage = "✅ تم إضافة الزر (${newAct.label})"
                            }
                        },
                        onFailure = { err ->
                            statusMessage = "❌ ${err.message}"
                        }
                    )
                }) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddButtonDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
