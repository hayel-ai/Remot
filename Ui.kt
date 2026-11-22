package com.example.remot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun RemotApp() {
    val ctx = LocalContext.current
    val hasIr = remember { IrEngine.hasIrEmitter(ctx) }
    var profiles by remember { mutableStateOf(IrEngine.loadProfiles(ctx)) }
    var selectedId by remember { mutableStateOf(profiles.firstOrNull()?.id ?: "") }
    var statusMessage by remember {
        mutableStateOf(
            if (hasIr) "مرسل IR جاهز للتحكم بالشاشات والرسيفرات"
            else "تنبيه: هاتفك لا يحتوي على مرسل IR مدمج"
        )
    }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showAddButtonDialog by remember { mutableStateOf(false) }

    val currentProfile = profiles.find { it.id == selectedId } ?: profiles.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "remot",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(onClick = { showAddDeviceDialog = true }) {
                Text("+ إضافة جهاز")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasIr) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 155.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(profiles, key = { it.id }) { prof ->
                val isSelected = prof.id == currentProfile?.id
                if (isSelected) {
                    Button(
                        onClick = { selectedId = prof.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${prof.name} • ${prof.brand}")
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedId = prof.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${prof.name} • ${prof.brand}")
                    }
                }
            }
        }

        if (currentProfile != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أزرار: ${currentProfile.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { showAddButtonDialog = true }) {
                    Text("+ زر جديد")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(currentProfile.actions, key = { it.id }) { action ->
                    Button(
                        onClick = {
                            val res = IrEngine.transmit(ctx, action)
                            statusMessage = res.fold(
                                onSuccess = { "✅ $it" },
                                onFailure = { "❌ ${it.message}" }
                            )
                        },
                        modifier = Modifier.height(64.dp)
                    ) {
                        Text(
                            text = action.label,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Text(
            text = "Developed by Hayel alkouri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
                        label = { Text("اسم الجهاز (مثال: شاشة قاعة 2)") },
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
                        val baseActions = if (category == DeviceCategory.RECEIVER) {
                            listOf(
                                RemoteAction("p_${UUID.randomUUID()}", "باور الرسيفر", 38000, IrEngine.necPattern(0x807F02FD)),
                                RemoteAction("ok_${UUID.randomUUID()}", "موافق (OK)", 38000, IrEngine.necPattern(0x807F4AB5)),
                                RemoteAction("cu_${UUID.randomUUID()}", "قناة +", 38000, IrEngine.necPattern(0x807F12ED)),
                                RemoteAction("cd_${UUID.randomUUID()}", "قناة -", 38000, IrEngine.necPattern(0x807F1AE5))
                            )
                        } else {
                            listOf(
                                RemoteAction("p_${UUID.randomUUID()}", "تشغيل / إطفاء", 38000, IrEngine.necPattern(0x20DF10EF)),
                                RemoteAction("s_${UUID.randomUUID()}", "تغيير المدخل (Source)", 38000, IrEngine.necPattern(0x20DFD02F)),
                                RemoteAction("u_${UUID.randomUUID()}", "صوت +", 38000, IrEngine.necPattern(0x20DF40BF)),
                                RemoteAction("d_${UUID.randomUUID()}", "صوت -", 38000, IrEngine.necPattern(0x20DFC03F))
                            )
                        }
                        val newProf = RemoteProfile(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            brand = brand.trim().ifEmpty { category.label },
                            category = category,
                            actions = baseActions
                        )
                        val updated = profiles + newProf
                        profiles = updated
                        selectedId = newProf.id
                        IrEngine.saveProfiles(ctx, updated)
                        showAddDeviceDialog = false
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
