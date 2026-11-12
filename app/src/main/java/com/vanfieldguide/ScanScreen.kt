package com.vanfieldguide

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ScanScreen(scan: ScanViewModel, binder: BinderViewModel, available: Boolean,
    takePhoto: () -> Unit, choosePhoto: () -> Unit, searchManual: () -> Unit, setup: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Photo → saved manual", style = MaterialTheme.typography.titleLarge)
            Text("Start with the equipment or a close-up of its brand and model label. Searches your saved REGO pack on this phone.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = available, onClick = takePhoto) { Text("Take photo") }
                OutlinedButton(enabled = available, onClick = choosePhoto) { Text("Choose photo") }
            }
            Text("Capture is temporary. Clear scan removes the preview and results.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text(scan.status, style = MaterialTheme.typography.bodySmall)
            if (scan.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        scan.preview?.let { bitmap -> item {
            Image(bitmap.asImageBitmap(), contentDescription = "Equipment photo being matched", modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Fit)
        } }
        scan.match?.let { match -> item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(match.title, style = MaterialTheme.typography.titleLarge)
                    Text(match.detail)
                    if (scan.labelEdited) Text("Based on your edited label text.", style = MaterialTheme.typography.labelSmall)
                    if (match.hasCandidate) {
                        Text(StarterManual.TITLE, style = MaterialTheme.typography.titleMedium)
                        Text("${StarterManual.MODEL}\nOEM manual · revision A0", style = MaterialTheme.typography.bodySmall)
                        if (binder.imported) {
                            Button(enabled = available, onClick = { binder.openPage(0) }) { Text("Open saved manual") }
                            OutlinedButton(enabled = available, onClick = { binder.openPage(13) }) { Text("Open OEM wiring diagram") }
                            TextButton(enabled = available, onClick = searchManual) { Text("Search this manual") }
                        } else OutlinedButton(enabled = available, onClick = setup) { Text("Import matching manual in Setup") }
                    }
                }
            }
        } }
        item {
            OutlinedTextField(value = scan.label, onValueChange = scan::editLabel, enabled = available,
                modifier = Modifier.fillMaxWidth(), label = { Text("Read or correct the model label") }, minLines = 2, maxLines = 5)
            Text("Check letters and digits against the photo. Similar-looking equipment may use a different manual.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = available && scan.label.isNotBlank(), onClick = scan::checkLabel) { Text("Check label") }
                TextButton(enabled = available, onClick = scan::clear) { Text("Clear scan") }
            }
        }
        if (scan.match?.hasCandidate == true && binder.imported && scan.pages.isNotEmpty()) {
            item {
                Text("Visually related manual pages", style = MaterialTheme.typography.titleMedium)
                Text("Possible references from image-to-text similarity. These pages do not confirm the equipment identity.", style = MaterialTheme.typography.bodySmall)
            }
            items(scan.pages, key = { it.page }) { hit ->
                OutlinedCard(onClick = { if (available) binder.openPage(hit.page) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Open PDF page ${hit.page + 1}", style = MaterialTheme.typography.titleSmall)
                        Text(hit.excerpt.replace(Regex("\\s+"), " "), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
