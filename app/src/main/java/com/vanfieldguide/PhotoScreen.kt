package com.vanfieldguide

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable fun PhotoScreen(
    photos: PhotoViewModel, query: String, setQuery: (String) -> Unit,
    available: Boolean, hasModel: Boolean, listening: Boolean, speechStatus: String,
    speak: () -> Unit, cancelSpeech: () -> Unit, chooseAccess: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val grid = rememberLazyGridState()
    var shown by rememberSaveable { mutableIntStateOf(24) }
    var removeDialog by remember { mutableStateOf(false) }
    var managePhotos by remember(photos.revision) { mutableStateOf(false) }
    val visible = if (photos.searched) photos.results else photos.photos.take(shown)
    LaunchedEffect(photos.revision) { grid.scrollToItem(if (photos.searched && photos.results.isNotEmpty()) 2 else 0) }
    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Find photos by description", style = MaterialTheme.typography.titleLarge)
                Text("Search pictures stored on this phone. Try ‘van pictures’, ‘camper interior’, or ‘battery cables’.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = available || listening, onClick = speak) { Text(if (listening) "Stop" else "Speak") }
                    if (listening) TextButton(onClick = cancelSpeech) { Text("Cancel") }
                }
                Text(speechStatus, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = query, onValueChange = setQuery, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Describe a photo") }, minLines = 1, maxLines = 3)
                Button(modifier = Modifier.fillMaxWidth(), enabled = available && hasModel && (photos.coverage.indexed > 0 || photos.coverage.npuIndexed > 0) && query.isNotBlank(), onClick = {
                    keyboard?.hide(); focus.clearFocus(); photos.search(query)
                }) { Text("Search photos with Gemma") }
                Text(photos.status)
                if (photos.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { photos.pause() }) { Text("Pause") }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(photos.access, style = MaterialTheme.typography.titleSmall)
                    Text("NPU: ${photos.coverage.npuIndexed}/${photos.coverage.total} · CPU/GPU: ${photos.coverage.indexed}/${photos.coverage.total}")
                    if (photos.coverage.failed > 0) Text("${photos.coverage.failed} unreadable photos")
                    if (photos.searched) TextButton(enabled = available, onClick = { managePhotos = !managePhotos }) { Text(if (managePhotos) "Hide photo setup" else "Manage photos") }
                    if (!photos.searched || managePhotos) {
                    Text("Originals stay in your gallery. Cloud-only pictures are excluded. Changing or deleting a photo requires a refresh; removing access removes it from search.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = available, onClick = chooseAccess) { Text("Choose photo access") }
                    Button(enabled = available && hasModel && photos.coverage.total > 0, onClick = { photos.index() }) { Text("Build / resume photo index") }
                    Row {
                        TextButton(enabled = available, onClick = { photos.refresh() }) { Text("Refresh library") }
                        if (photos.coverage.indexed > 0 || photos.coverage.npuIndexed > 0) TextButton(enabled = available, onClick = { removeDialog = true }) { Text("Remove index") }
                    }
                    if (!hasModel) Text("Import the Gemma model in Setup to index and search photos.")
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column {
                Text(if (photos.searched) "Closest photos" else "Local photo library", style = MaterialTheme.typography.titleLarge)
                if (photos.searched) Text("For: ${photos.resultQuery}", style = MaterialTheme.typography.titleSmall)
                Text(if (photos.searched) "Ranked by Gemma image similarity. These are possible matches, not verified equipment identifications."
                    else "${visible.size} of ${photos.photos.size} accessible pictures · newest first", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(visible, key = { it.uri }) { photo ->
            Card(onClick = { photos.open(photo) }) {
                PhotoBitmap(photos, photo, 480, Modifier.fillMaxWidth().height(150.dp), ContentScale.Crop)
                Text(photo.name, Modifier.padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (!photos.searched && shown < photos.photos.size) item(span = { GridItemSpan(2) }) {
            TextButton(onClick = { shown += 24 }) { Text("Show more photos") }
        }
    }
    if (removeDialog) AlertDialog(onDismissRequest = { removeDialog = false }, title = { Text("Remove the photo index?") },
        text = { Text("This removes the saved Gemma photo index. Your gallery pictures and manual stay in place. Photo search will need indexing again.") },
        confirmButton = { TextButton(onClick = { removeDialog = false; photos.clearIndex() }) { Text("Remove index") } },
        dismissButton = { TextButton(onClick = { removeDialog = false }) { Text("Keep") } })
}

@Composable private fun PhotoBitmap(photos: PhotoViewModel, photo: DevicePhoto, edge: Int, modifier: Modifier, scale: ContentScale) {
    var failed by remember(photo.uri, photo.version) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, photo.uri, photo.version, edge) {
        try { value = photos.bitmap(photo, edge) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    Box(modifier.background(Color(0xFFE4E8E2)), contentAlignment = androidx.compose.ui.Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = photo.name, modifier = Modifier.fillMaxSize(), contentScale = scale) }
            ?: Text(if (failed) "Photo unavailable. Refresh access." else "Loading photo…", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun PhotoViewer(photos: PhotoViewModel) {
    val photo = photos.viewed ?: return
    val context = LocalContext.current
    var zoom by remember(photo.uri) { mutableFloatStateOf(1f) }
    var offset by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    var message by remember(photo.uri) { mutableStateOf("") }
    BackHandler { photos.close() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        TextButton(onClick = { photos.close() }) { Text("← Back to photos") }
        Text(photo.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Device photo · equipment identity unverified", style = MaterialTheme.typography.labelMedium)
        Text("Pinch to zoom. Preview limited to 2200 pixels; open original for full detail.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(photo.uri), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            catch (_: Exception) { message = "No viewer could open this photo. Check photo access or use the preview." }
        }) { Text("Open original in gallery") }
        if (message.isNotEmpty()) Text(message)
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(photo.uri) {
            detectTransformGestures { _, pan, factor, _ ->
                zoom = (zoom * factor).coerceIn(1f, 5f)
                offset = if (zoom == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-size.width * zoom / 2, size.width * zoom / 2),
                    (offset.y + pan.y).coerceIn(-size.height * zoom / 2, size.height * zoom / 2))
            }
        }) {
            PhotoBitmap(photos, photo, 2200, Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y), ContentScale.Fit)
        }
    }
}
