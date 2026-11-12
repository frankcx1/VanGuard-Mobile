package com.vanfieldguide

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var speech: OfflineSpeech? = null
    private var listening by mutableStateOf(false)
    private var speechStatus by mutableStateOf("Speak a request, then review the words before searching.")
    private var query by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GemmaRuntime.initialize(this)
        enableEdgeToEdge()
        query = savedInstanceState?.getString("query").orEmpty()
        speech = OfflineSpeech(this, { text, active -> speechStatus = text; listening = active }, { query = it })
        setContent {
            VanGuardTheme {
                val model: ModelProbe = viewModel()
                val binder: BinderViewModel = viewModel()
                val photos: PhotoViewModel = viewModel()
                val scan: ScanViewModel = viewModel()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var semantic by rememberSaveable { mutableStateOf(true) }
                var deleteDialog by remember { mutableStateOf(false) }
                var showAllResults by remember(binder.searchRevision) { mutableStateOf(false) }
                val listState = rememberLazyListState()
                val keyboard = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                val uiScope = rememberCoroutineScope()
                val lifecycle = LocalLifecycleOwner.current
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) photos.refresh()
                        if (event == Lifecycle.Event.ON_STOP) photos.pause()
                    }
                    lifecycle.lifecycle.addObserver(observer)
                    onDispose { lifecycle.lifecycle.removeObserver(observer) }
                }
                DisposableEffect(photos.busy, scan.busy) {
                    if (photos.busy || scan.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                LaunchedEffect(tab) { listState.scrollToItem(0) }
                LaunchedEffect(binder.searchRevision) {
                    if (binder.searchRevision > 0 && tab == 0 && (binder.results.isNotEmpty() || binder.diagrams.isNotEmpty())) listState.animateScrollToItem(5)
                }
                val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importModel) }
                val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::runImage) }
                val manualPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(binder::importManual) }
                val photoAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { photos.refresh() }
                val scanCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), scan::captureFinished)
                val scanPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scan.scan(it) } }
                val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) speech?.start() else speechStatus = "Microphone permission denied. Type your request instead."
                }
                val available = !model.busy && !binder.busy && !photos.busy && !scan.busy && !listening
                Surface(Modifier.fillMaxSize()) {
                    if (photos.viewed != null) {
                        PhotoViewer(photos)
                    } else if (binder.viewedPage != null) {
                        PdfPageScreen(binder)
                    } else {
                        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
                            VanGuardHeader(tab) {
                                    if (tab == 0 || tab == 2) TextButton(
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF8F5ED), disabledContentColor = Color(0xFFA7B8AA)),
                                        enabled = available && (query.isNotEmpty() || binder.results.isNotEmpty() || binder.diagrams.isNotEmpty() || photos.searched),
                                        onClick = {
                                            speech?.cancel("Speak a request, then review the words before searching.")
                                            query = ""
                                            binder.clearSearch()
                                            photos.clearSearch()
                                            keyboard?.hide()
                                            focusManager.clearFocus()
                                            uiScope.launch { listState.scrollToItem(0) }
                                        },
                                    ) { Text("Clear") }
                            }
                            if (tab == 4) Box(Modifier.weight(1f)) {
                                ScanScreen(scan, binder, available,
                                    takePhoto = { try { scanCamera.launch(scan.captureUri()) } catch (_: Exception) { scan.captureFailed() } },
                                    choosePhoto = { scanPicker.launch(arrayOf("image/*")) },
                                    searchManual = { query = ""; binder.clearSearch(); tab = 0 }, setup = { tab = 3 })
                            } else if (tab == 2) Box(Modifier.weight(1f)) {
                                PhotoScreen(photos, query, { query = it }, available, model.hasModel, listening, speechStatus,
                                    speak = {
                                        if (listening) speech?.stop()
                                        else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech?.start()
                                        else microphone.launch(Manifest.permission.RECORD_AUDIO)
                                    }, cancelSpeech = { speech?.cancel() },
                                    chooseAccess = { photoAccess.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) })
                            } else LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (tab == 0) {
                                    item {
                                        Text("What do you need to find?", style = MaterialTheme.typography.titleLarge)
                                        Text("Ask for a section, diagram, or exact fault code.")
                                    }
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(enabled = !binder.busy && !model.busy && !photos.busy && !scan.busy, onClick = {
                                                if (listening) speech?.stop()
                                                else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech?.start()
                                                else microphone.launch(Manifest.permission.RECORD_AUDIO)
                                            }) { Text(if (listening) "Stop" else "Speak") }
                                            if (listening) OutlinedButton(onClick = { speech?.cancel() }) { Text("Cancel") }
                                        }
                                        Text(speechStatus, style = MaterialTheme.typography.bodySmall)
                                    }
                                    item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Type or review your request") }, minLines = 2) }
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = semantic, onClick = { semantic = true }, label = { Text("Gemma + keywords") })
                                            FilterChip(selected = !semantic, onClick = { semantic = false }, label = { Text("Keywords only") })
                                        }
                                        Button(modifier = Modifier.fillMaxWidth(), enabled = available && binder.imported && query.isNotBlank(), onClick = { keyboard?.hide(); binder.search(query, semantic) }) { Text("Search saved manual") }
                                    }
                                    item {
                                        if (binder.status == BinderViewModel.INTRO_STATUS) {
                                            Text("Local AI App showcasing Gemma", style = MaterialTheme.typography.titleMedium)
                                            Text("EmbeddingGemma 2 Text Vision · 440M parameters", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else Text(binder.status)
                                        if (binder.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        if (!binder.imported) OutlinedButton(onClick = { tab = 3 }) { Text("Set up your manual") }
                                    }
                                    if (binder.diagrams.isNotEmpty()) item {
                                        Text("Saved diagrams", style = MaterialTheme.typography.titleLarge)
                                        Text("Matched by saved page labels. OEM references, not as-installed drawings.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    items(binder.diagrams, key = { "diagram-${it.page}" }) { diagram -> DiagramCard(diagram, binder) }
                                    if (binder.results.isNotEmpty()) item {
                                        Text("Manual pages", style = MaterialTheme.typography.titleLarge)
                                        Text("Closest saved references. Open the original for complete context and warnings.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    items(if (showAllResults) binder.results else binder.results.take(3), key = { it.page }) { hit ->
                                        Card(onClick = { binder.openPage(hit.page) }) {
                                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("PDF page ${hit.page + 1} · REGO manual A0", style = MaterialTheme.typography.titleMedium)
                                                Text(hit.excerpt.replace(Regex("\\s+"), " "), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                                Text(hit.methods, style = MaterialTheme.typography.labelSmall)
                                                Text("Open original page →", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    if (binder.results.size > 3) item {
                                        TextButton(onClick = { showAllResults = !showAllResults }) { Text(if (showAllResults) "Show fewer pages" else "Show ${binder.results.size - 3} more pages") }
                                    }
                                } else if (tab == 1) {
                                    item {
                                        Text(StarterManual.TITLE, style = MaterialTheme.typography.titleLarge)
                                        Text("OEM reference · revision ${StarterManual.REVISION}\nInstalled model: ${StarterManual.MODEL}\nEquipment identity confirmed by owner.")
                                        if (binder.imported) {
                                            OutlinedButton(onClick = { binder.openPage(13) }) { Text("OEM wiring diagram · PDF page 14") }
                                            Text("This manufacturer's diagram is not an as-installed drawing of your van.", style = MaterialTheme.typography.bodySmall)
                                        }
                                        CoverageCard(binder.coverage)
                                    }
                                    if (!binder.imported) item { Text("Import your manual in Setup. No source is bundled into the app.") }
                                    items((0 until binder.coverage.totalPages).toList(), key = { it }) { number ->
                                        val page = binder.pages.firstOrNull { it.number == number }
                                        OutlinedCard(onClick = { binder.openPage(number) }, modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text("PDF page ${number + 1}", style = MaterialTheme.typography.titleSmall)
                                                Text(page?.title?.ifBlank { "Original page" } ?: "Original page · extraction pending", maxLines = 2)
                                                if (page?.state != "text") Text("Text coverage: ${page?.state ?: "pending"}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                } else {
                                    item {
                                        Text("Prepare Van Guard", style = MaterialTheme.typography.titleLarge)
                                        Text("${StarterManual.MODEL}\nOwner-confirmed · 12V / 3000W")
                                        Text("Earlier model and speech tests passed offline according to your report. This manual-search build still needs its own disconnected test.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    item {
                                        Text("1. Copy the original manual", style = MaterialTheme.typography.titleMedium)
                                        Text("Select the supplied RIV1230RCL-1SS manual, revision A0. Its file hash identifies the reviewed source; other model manuals are excluded.")
                                        Button(enabled = available, onClick = { manualPicker.launch(arrayOf("application/pdf")) }) { Text(if (binder.imported) "Reimport manual" else "Import REGO manual") }
                                        if (binder.imported) OutlinedButton(enabled = available, onClick = { binder.resumeExtraction() }) { Text("Resume page extraction") }
                                        CoverageCard(binder.coverage)
                                    }
                                    item {
                                        Text("2. Prepare semantic search", style = MaterialTheme.typography.titleMedium)
                                        Text("NPU and CPU/GPU fallback indexes are prepared separately. Completed passages are saved as indexing runs.")
                                        Button(enabled = available && binder.imported && model.hasModel, onClick = { binder.index() }) { Text("Build / resume Gemma index") }
                                        Text(binder.status)
                                        if (binder.busy) {
                                            LinearProgressIndicator(Modifier.fillMaxWidth())
                                            OutlinedButton(onClick = { binder.cancel() }) { Text("Pause indexing") }
                                        }
                                    }
                                    item {
                                        HorizontalDivider()
                                        Text("Model and speech diagnostics", style = MaterialTheme.typography.titleMedium)
                                        Text(if (model.hasNpu) "Tensor G5 model available" else "Import the Tensor G5 model to enable NPU acceleration.")
                                        Text(if (model.hasCpu) "CPU/GPU model available for fallback" else "Import the CPU/GPU model to prepare fallback.", style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = GemmaRuntime.preferNpu, enabled = available, onClick = { GemmaRuntime.setNpuPreferred(this@MainActivity, true) }, label = { Text("NPU preferred") })
                                            FilterChip(selected = !GemmaRuntime.preferNpu, enabled = available, onClick = { GemmaRuntime.setNpuPreferred(this@MainActivity, false) }, label = { Text("CPU/GPU") })
                                        }
                                        GemmaRuntime.npuIssue?.let { issue ->
                                            Text("NPU unavailable this session: $issue", style = MaterialTheme.typography.bodySmall)
                                            TextButton(enabled = available, onClick = { GemmaRuntime.setNpuPreferred(this@MainActivity, true) }) { Text("Retry NPU") }
                                        }
                                        OutlinedButton(enabled = available, onClick = { modelPicker.launch(arrayOf("*/*")) }) { Text("Import NPU or CPU/GPU model") }
                                        Text(model.status, style = MaterialTheme.typography.bodySmall)
                                        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        OutlinedButton(enabled = available && model.hasModel && query.isNotBlank(), onClick = { model.runText(query) }) { Text("Test current text embedding") }
                                        OutlinedButton(enabled = available && model.hasModel, onClick = { imagePicker.launch(arrayOf("image/jpeg", "image/png")) }) { Text("Test one image embedding") }
                                        OutlinedButton(enabled = available, onClick = { speech?.preflight() }) { Text("Check speech resources") }
                                        OutlinedButton(enabled = available, onClick = { speech?.prepare() }) { Text("Prepare English speech resources") }
                                        Text(speechStatus, style = MaterialTheme.typography.bodySmall)
                                    }
                                    item {
                                        Text("Readiness: originals, extraction, and semantic coverage are shown separately. Photos has its own device-library index. A full off-grid rehearsal is still pending.", style = MaterialTheme.typography.bodySmall)
                                        if (binder.imported) TextButton(enabled = available, onClick = { deleteDialog = true }) { Text("Remove app copy and manual index") }
                                    }
                                }
                            }
                            VanGuardNavigation(tab) { next -> keyboard?.hide(); focusManager.clearFocus(); tab = next }
                        }
                    }
                }
                if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Remove the app's manual copy?") }, text = { Text("This removes only the imported copy and its index. The source in Downloads and your Gemma model remain.") }, confirmButton = { TextButton(onClick = { deleteDialog = false; binder.deleteAppCopy() }) { Text("Remove app copy") } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Keep") } })
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("query", query); super.onSaveInstanceState(outState) }
    override fun onStop() { if (listening) speech?.cancel("Listening interrupted. Tap Speak to start again."); super.onStop() }
    override fun onDestroy() { speech?.close(); speech = null; super.onDestroy() }
}

@Composable private fun DiagramCard(diagram: DiagramReference, binder: BinderViewModel) {
    val preview by produceState<Bitmap?>(null, diagram.page) {
        try { value = binder.diagramPreview(diagram.page) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { value = null }
    }
    Card(onClick = { binder.openPage(diagram.page) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(diagram.title, style = MaterialTheme.typography.titleMedium)
            Text("REGO manual A0 · PDF page ${diagram.page + 1}", style = MaterialTheme.typography.labelMedium)
            preview?.let { Image(it.asImageBitmap(), contentDescription = diagram.title, modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.White), contentScale = ContentScale.Fit) }
            Button(onClick = { binder.openPage(diagram.page) }) { Text("Open diagram") }
        }
    }
}

@Composable private fun CoverageCard(coverage: Coverage) {
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${coverage.totalPages} original pages · ${coverage.extracted} with text")
            Text("${coverage.empty} without text · ${coverage.failed} extraction errors")
            Text("NPU: ${coverage.npuEmbedded}/${coverage.chunks} passages")
            Text("CPU/GPU fallback: ${coverage.embedded}/${coverage.chunks} passages")
            val pending = coverage.totalPages - coverage.extracted - coverage.empty - coverage.failed
            if (pending > 0) Text("$pending pages still awaiting extraction")
        }
    }
}

@Composable private fun PdfPageScreen(binder: BinderViewModel) {
    val number = binder.viewedPage ?: return
    var zoom by remember(number) { mutableFloatStateOf(1f) }
    var offset by remember(number) { mutableStateOf(Offset.Zero) }
    BackHandler { binder.closeViewer() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        TextButton(onClick = { binder.closeViewer() }) { Text("← Back to references") }
        Text("REGO manual · A0 · OEM reference", style = MaterialTheme.typography.titleMedium)
        Text("PDF page ${number + 1} of ${binder.coverage.totalPages}", style = MaterialTheme.typography.bodyMedium)
        Text("${StarterManual.MODEL} · complete original retained", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = number > 0, onClick = { binder.openPage(number - 1) }) { Text("Previous") }
            OutlinedButton(enabled = number + 1 < binder.coverage.totalPages, onClick = { binder.openPage(number + 1) }) { Text("Next") }
            TextButton(onClick = { zoom = 1f; offset = Offset.Zero; binder.openPage(number, 2) }) { Text("Detail") }
        }
        Text("Pinch to zoom and drag. Detail reloads at higher resolution.", style = MaterialTheme.typography.labelSmall)
        if (binder.viewerStatus.isNotEmpty()) Text(binder.viewerStatus)
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(number) {
            detectTransformGestures { _, pan, factor, _ ->
                zoom = (zoom * factor).coerceIn(1f, 5f)
                offset = if (zoom == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-size.width * zoom / 2, size.width * zoom / 2), (offset.y + pan.y).coerceIn(-size.height * zoom / 2, size.height * zoom / 2))
            }
        }) {
            binder.pageBitmap?.let { bitmap -> Image(bitmap.asImageBitmap(), contentDescription = "Original manual PDF page ${number + 1}", modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) }
        }
    }
}
