package com.example.livora.ui.ac

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.ir.AcBrands

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcBrandSheet(
    currentBrandId: String,
    currentModelIndex: Int,
    onDismiss: () -> Unit,
    onSendTest: (brandId: String, modelIndex: Int) -> Unit,
    onConfirm: (brandId: String, modelIndex: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var brandId by remember { mutableStateOf(currentBrandId) }
    var modelIndex by remember { mutableIntStateOf(currentModelIndex) }
    var testSent by remember { mutableStateOf(false) }

    val brand = AcBrands.find(brandId)
    val model = brand.models[modelIndex.coerceIn(0, brand.models.lastIndex)]
    val visibleBrands = AcBrands.all.filter {
        query.isBlank() ||
            it.name.contains(query, ignoreCase = true) ||
            it.alsoWorksWith.contains(query, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Remote brand",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick your AC brand, then send a test signal. If the AC does not react, try the next model.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search brands") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (visibleBrands.isEmpty()) {
                Text(
                    text = "No brand matches \"$query\"",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            visibleBrands.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = item.id == brandId,
                            role = Role.RadioButton,
                            onClick = {
                                if (item.id != brandId) {
                                    brandId = item.id
                                    modelIndex = if (item.id == currentBrandId) currentModelIndex else 0
                                    testSent = false
                                }
                            }
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.alsoWorksWith,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    if (item.id == brandId) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${brand.name}, model ${modelIndex.coerceIn(0, brand.models.lastIndex) + 1} of ${brand.models.size}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${model.label}: ${model.detail}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onSendTest(brand.id, modelIndex)
                        testSent = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send test")
                }
                if (brand.models.size > 1) {
                    OutlinedButton(
                        onClick = {
                            modelIndex = (modelIndex + 1) % brand.models.size
                            testSent = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next model")
                    }
                }
            }

            Text(
                text = if (testSent) {
                    "The test turns the AC on at 20°C cool. Did it react?"
                } else {
                    "The test turns the AC on at 20°C cool. Point the phone at the AC first."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            Button(
                onClick = {
                    onConfirm(brand.id, modelIndex)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use this remote")
            }
        }
    }
}
