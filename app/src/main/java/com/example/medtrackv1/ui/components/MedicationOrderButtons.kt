package com.example.medtrackv1.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MedicationOrderButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp
) {
    Row(modifier = modifier) {
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move medication earlier")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move medication later")
        }
    }
}
