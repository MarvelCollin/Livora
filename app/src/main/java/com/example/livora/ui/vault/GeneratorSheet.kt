package com.example.livora.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.vault.GeneratorOptions
import com.example.livora.data.vault.PasswordGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneratorSheet(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var length by rememberSaveable { mutableIntStateOf(20) }
    var lowercase by rememberSaveable { mutableStateOf(true) }
    var uppercase by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var lookAlikes by rememberSaveable { mutableStateOf(false) }
    var round by rememberSaveable { mutableIntStateOf(0) }

    val options = GeneratorOptions(length, lowercase, uppercase, digits, symbols, lookAlikes)
    val generated = remember(length, lowercase, uppercase, digits, symbols, lookAlikes, round) {
        PasswordGenerator.generate(options)
    }

    fun classesOn() = listOf(lowercase, uppercase, digits, symbols).count { it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Generate a password",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = generated,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            )
            StrengthMeter(generated)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Length $length",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = length.toFloat(),
                onValueChange = { length = it.toInt() },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            OptionRow("Lowercase letters", lowercase) { if (!it && classesOn() == 1) return@OptionRow; lowercase = it }
            OptionRow("Uppercase letters", uppercase) { if (!it && classesOn() == 1) return@OptionRow; uppercase = it }
            OptionRow("Numbers", digits) { if (!it && classesOn() == 1) return@OptionRow; digits = it }
            OptionRow("Symbols", symbols) { if (!it && classesOn() == 1) return@OptionRow; symbols = it }
            OptionRow("Avoid look-alike characters", lookAlikes) { lookAlikes = it }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { round++ },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Text("Another one")
                }
                Button(
                    onClick = { onUse(generated) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Text("Use this")
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
