// app/src/main/java/com/luminar/reader/presentation/components/ErrorReportDialog.kt
package com.luminar.reader.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminar.reader.presentation.theme.LuminarGold

@Composable
fun ErrorReportDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onSendReport: (userNote: String) -> Unit
) {
    var userNote by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    if (sent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Report sent ✓", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(text = "Thanks! We'll look into this and fix it.")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "OK", color = LuminarGold)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Something went wrong", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = "Help us fix this by sending a report:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = userNote,
                    onValueChange = { userNote = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text("What were you doing? (optional)", fontSize = 13.sp) },
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSendReport(userNote)
                sent = true
            }) {
                Text(text = "Send report", color = LuminarGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Dismiss")
            }
        }
    )
}
