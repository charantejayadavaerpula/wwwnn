package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.RecordingEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    // Gather states
    val currentMode by viewModel.currentMode.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isLiveListening by viewModel.isLiveListening.collectAsState()
    val liveAmplitude by viewModel.liveAmplitude.collectAsState()
    val scoState by viewModel.scoState.collectAsState()

    val recordings by viewModel.recordings.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTrackPath by viewModel.currentTrackPath.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val trackDuration by viewModel.trackDuration.collectAsState()

    val liveGain by viewModel.liveGain.collectAsState()

    // Permissions State
    var recordingPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var bluetoothPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        recordingPermissionGranted = results[Manifest.permission.RECORD_AUDIO] ?: recordingPermissionGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionGranted = results[Manifest.permission.BLUETOOTH_CONNECT] ?: bluetoothPermissionGranted
        }
        
        if (recordingPermissionGranted) {
            Toast.makeText(context, "Microphone access successfully granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone access is required to capture audio.", Toast.LENGTH_LONG).show()
        }
    }

    // Dynamic amplitude history for soundwave visualizer
    val amplitudeHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(liveAmplitude, isRecording, isLiveListening) {
        if (isRecording || isLiveListening) {
            amplitudeHistory.add(liveAmplitude)
            if (amplitudeHistory.size > 28) {
                amplitudeHistory.removeAt(0)
            }
        } else {
            if (amplitudeHistory.size > 0) {
                amplitudeHistory.removeAt(0)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "STUDIO RECORD & LISTEN",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = StudioTextPrimary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = StudioBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(StudioBackground)
                .padding(horizontal = 16.dp)
        ) {
            // Mode Segmented Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentMode == 0) StudioCard else Color.Transparent)
                        .clickable { viewModel.setMode(0) }
                        .padding(vertical = 12.dp)
                        .testTag("mode_tab_recorder"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomMicIcon(
                        tint = if (currentMode == 0) StudioPrimary else StudioTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Voice Recorder",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentMode == 0) StudioTextPrimary else StudioTextSecondary
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentMode == 1) StudioCard else Color.Transparent)
                        .clickable { viewModel.setMode(1) }
                        .padding(vertical = 12.dp)
                        .testTag("mode_tab_live"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomHeadphonesIcon(
                        tint = if (currentMode == 1) StudioSecondary else StudioTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live Listen Booster",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentMode == 1) StudioTextPrimary else StudioTextSecondary
                    )
                }
            }

            // Central Permissions Block if record audio is missing
            if (!recordingPermissionGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(StudioAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Requirement",
                                tint = StudioAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Microphone Access Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To capture high-fidelity live recordings and output voice live to your earpods, please grant microphone permissions.",
                            fontSize = 14.sp,
                            color = StudioTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                                requestPermissionsLauncher.launch(perms.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_permission_btn")
                        ) {
                            Text("Grant Access Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Interactive content Based on Active Tab
                if (currentMode == 0) {
                    // TAB ONE: Voice Recorder Mode (Record Audio)
                    RecorderTab(
                        viewModel = viewModel,
                        isRecording = isRecording,
                        recordings = recordings,
                        amplitudeHistory = amplitudeHistory,
                        isPlaying = isPlaying,
                        currentTrackPath = currentTrackPath,
                        playbackPosition = playbackPosition,
                        trackDuration = trackDuration
                    )
                } else {
                    // TAB TWO: Live Listen Booster Mode
                    LiveListenTab(
                        viewModel = viewModel,
                        isLiveListening = isLiveListening,
                        amplitudeHistory = amplitudeHistory,
                        liveGain = liveGain,
                        scoState = scoState,
                        bluetoothPermissionGranted = bluetoothPermissionGranted,
                        onRequestBluetoothPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                            }
                        }
                    )
                }
            }
        }
    }
}

// --- TAB SUB-SUITES ---

@Composable
fun RecorderTab(
    viewModel: MainViewModel,
    isRecording: Boolean,
    recordings: List<RecordingEntity>,
    amplitudeHistory: List<Float>,
    isPlaying: Boolean,
    currentTrackPath: String?,
    playbackPosition: Long,
    trackDuration: Long
) {
    var textPreName by remember { mutableStateOf("") }
    var renameTargetEntity by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameNewInputText by remember { mutableStateOf("") }
    var showDeleteConfirmEntity by remember { mutableStateOf<RecordingEntity?>(null) }

    // Live Tick Counter during active record
    var liveSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isRecording) {
                liveSeconds = (System.currentTimeMillis() - start) / 1000
                delay(200)
            }
        } else {
            liveSeconds = 0L
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isRecording) "NOW CAPTURING LIVE AUDIO" else "CAPTURE NEW STUDIO CLIP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = if (isRecording) StudioAccent else StudioTextSecondary
                )

                // Visualizer
                Spacer(modifier = Modifier.height(14.dp))
                LivePulseWaveform(
                    amplitudes = amplitudeHistory,
                    isActive = isRecording,
                    waveColor = StudioAccent
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isRecording) {
                    // Timer Counter
                    Text(
                        text = formatDuration(liveSeconds * 1000),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StudioTextPrimary
                    )
                    Text(
                        text = "Recording file: ${textPreName.trim().ifEmpty { "Audio_Clip" }}.m4a",
                        fontSize = 12.sp,
                        color = StudioAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    IconButton(
                        onClick = {
                            viewModel.stopRecording()
                            textPreName = ""
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(StudioAccent)
                            .testTag("stop_record_button")
                    ) {
                        CustomStopIcon(
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    // Idle mode. Display name prompt first.
                    OutlinedTextField(
                        value = textPreName,
                        onValueChange = { textPreName = it },
                        label = { Text("Enter recording name (Optional)", color = StudioTextSecondary) },
                        placeholder = { Text("e.g. Ambient Soundscape, Voice draft") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("recording_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StudioPrimary,
                            unfocusedBorderColor = StudioMuted,
                            focusedTextColor = StudioTextPrimary,
                            unfocusedTextColor = StudioTextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            val defaultName = textPreName.trim().ifEmpty {
                                "Clip_${SimpleDateFormat("MMM_dd_HH_mm", Locale.getDefault()).format(Date())}"
                            }
                            viewModel.startRecording(defaultName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_record_button")
                    ) {
                        CustomMicIcon(
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Live Recording", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of archived library records
        Text(
            text = "RECORDINGS LIBRARY (${recordings.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = StudioTextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Your studio file archives are empty.",
                        fontSize = 14.sp,
                        color = StudioTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Press 'Start Live Recording' to archive audio.",
                        fontSize = 12.sp,
                        color = StudioMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(recordings, key = { it.id }) { item ->
                    val isThisPlaying = isPlaying && currentTrackPath == item.filePath
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isThisPlaying) StudioCard else StudioSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isThisPlaying) StudioPrimary.copy(alpha = 0.2f)
                                            else StudioMuted.copy(alpha = 0.2f)
                                        )
                                        .clickable {
                                            if (isThisPlaying) {
                                                viewModel.pausePlayback()
                                            } else {
                                                viewModel.playRecording(item)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isThisPlaying) {
                                        CustomPauseIcon(
                                            tint = StudioPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Track",
                                            tint = StudioTextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StudioTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row {
                                        Text(
                                            text = formatDuration(item.durationMs),
                                            fontSize = 12.sp,
                                            color = StudioTextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "•",
                                            fontSize = 12.sp,
                                            color = StudioMuted
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatSize(item.fileSize),
                                            fontSize = 12.sp,
                                            color = StudioTextSecondary
                                        )
                                    }
                                }

                                // Rename / Delete icons
                                Row {
                                    IconButton(
                                        onClick = {
                                            renameTargetEntity = item
                                            renameNewInputText = item.name
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename File",
                                            tint = StudioTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showDeleteConfirmEntity = item },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete File",
                                            tint = StudioAccent.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Interactive Timeline Slider if active play
                            if (currentTrackPath == item.filePath) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column {
                                    Slider(
                                        value = playbackPosition.toFloat(),
                                        onValueChange = { viewModel.seekPlayback(it.toLong()) },
                                        valueRange = 0f..trackDuration.toFloat().coerceAtLeast(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = StudioPrimary,
                                            activeTrackColor = StudioPrimary,
                                            inactiveTrackColor = StudioMuted
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(18.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatDuration(playbackPosition),
                                            fontSize = 11.sp,
                                            color = StudioPrimary
                                        )
                                        Text(
                                            text = formatDuration(trackDuration),
                                            fontSize = 11.sp,
                                            color = StudioTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR LIBRARY MANAGEMENT ---

    if (renameTargetEntity != null) {
        AlertDialog(
            onDismissRequest = { renameTargetEntity = null },
            title = { Text("Rename Studio File", color = StudioTextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Enter a new name for the audio recording:",
                        color = StudioTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameNewInputText,
                        onValueChange = { renameNewInputText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StudioPrimary,
                            unfocusedBorderColor = StudioMuted,
                            focusedTextColor = StudioTextPrimary,
                            unfocusedTextColor = StudioTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTargetEntity?.let {
                            viewModel.renameRecording(it, renameNewInputText)
                        }
                        renameTargetEntity = null
                    }
                ) {
                    Text("Rename", color = StudioPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetEntity = null }) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            },
            containerColor = StudioSurface
        )
    }

    if (showDeleteConfirmEntity != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmEntity = null },
            title = { Text("Delete Recording", color = StudioAccent) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${showDeleteConfirmEntity?.name}'? This will permanently erase the audio file from your device.",
                    color = StudioTextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmEntity?.let {
                            viewModel.deleteRecording(it)
                        }
                        showDeleteConfirmEntity = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioAccent)
                ) {
                    Text("Delete Track", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmEntity = null }) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            },
            containerColor = StudioSurface
        )
    }
}

@Composable
fun LiveListenTab(
    viewModel: MainViewModel,
    isLiveListening: Boolean,
    amplitudeHistory: List<Float>,
    liveGain: Float,
    scoState: Boolean,
    bluetoothPermissionGranted: Boolean,
    onRequestBluetoothPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isLiveListening) "NOW AMPLIFYING LIVE SOUND" else "HEAR-THROUGH ASSIST / BOOST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = if (isLiveListening) StudioSecondary else StudioTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Wave visualizer
                LivePulseWaveform(
                    amplitudes = amplitudeHistory,
                    isActive = isLiveListening,
                    waveColor = StudioSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Large Power toggle
                IconButton(
                    onClick = { viewModel.toggleLiveListening() },
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(if (isLiveListening) StudioSecondary else StudioMuted.copy(alpha = 0.25f))
                        .testTag("live_listen_power_button")
                ) {
                    if (isLiveListening) {
                        CustomStopIcon(
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        CustomMicIcon(
                            tint = StudioTextPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isLiveListening) "Tap to turn Off" else "Tap to Boost Live Audio",
                    fontSize = 13.sp,
                    color = if (isLiveListening) StudioSecondary else StudioTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Live Gain Booster Volume slider
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CustomVolumeUpIcon(
                            tint = StudioSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loudness Power Boost",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%.1fx", liveGain),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = liveGain,
                    onValueChange = { viewModel.setGainFactor(it) },
                    valueRange = 1.0f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioSecondary,
                        activeTrackColor = StudioSecondary,
                        inactiveTrackColor = StudioMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("amplification_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1.0x (Normal)", fontSize = 11.sp, color = StudioTextSecondary)
                    Text("3.0x (Strong)", fontSize = 11.sp, color = StudioTextSecondary)
                    Text("5.0x (Hearing Aid)", fontSize = 11.sp, color = StudioSecondary)
                }
            }
        }

        // Feature: Bluetooth Remote Microphone / Earphones integration
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CustomBluetoothIcon(
                            tint = StudioSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Use Bluetooth Microphone",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "Route microphone of earpods as input",
                                fontSize = 11.sp,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    if (!bluetoothPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = onRequestBluetoothPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioMuted),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    } else {
                        Switch(
                            checked = scoState,
                            onCheckedChange = { viewModel.toggleBluetoothSco(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StudioBackground,
                                checkedTrackColor = StudioSecondary,
                                uncheckedThumbColor = StudioTextSecondary,
                                uncheckedTrackColor = StudioMuted
                            ),
                            modifier = Modifier.testTag("bluetooth_mic_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Friendly warning about headphones/feedback loops!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioAccent.copy(alpha = 0.12f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Acoustic Feedback Notice",
                        tint = StudioAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PREVENT ACOUSTIC FEEDBACK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioAccent,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Always wear earbuds or headphones while live listening. If sound is broadcast near your phone's microphone, you will hear a loud high-pitched whistle.",
                            fontSize = 11.sp,
                            color = StudioTextPrimary
                        )
                    }
                }
            }
        }

        // Instructions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HOW TO USE REMOTE COGNITION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = StudioSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                InstructionBullet(index = "1.", text = "Connect your Bluetooth earpods or headphones to your phone.")
                InstructionBullet(index = "2.", text = "Toggle on 'Use Bluetooth Microphone' above if you wish to use the microphone on your earpods as the active input source.")
                InstructionBullet(index = "3.", text = "To use your phone's mic as a Hearing-Aid (Live Listen), leave Bluetooth Microphone unchecked, wear your earpods, and put your phone near the speaker or person you want to hear.")
                InstructionBullet(index = "4.", text = "Adjust the Loudness Power Boost slider to hear distant audio with crystal clarity.")
            }
        }
    }
}

@Composable
fun InstructionBullet(index: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = index,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = StudioSecondary,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = StudioTextSecondary
        )
    }
}

@Composable
fun LivePulseWaveform(
    amplitudes: List<Float>,
    isActive: Boolean,
    waveColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val count = 28
        val width = size.width
        val height = size.height
        val barSpacing = width / count
        val barWidth = barSpacing * 0.65f

        // Center line
        drawLine(
            color = StudioMuted.copy(alpha = 0.25f),
            start = Offset(0f, height / 2),
            end = Offset(width, height / 2),
            strokeWidth = 1f
        )

        for (i in 0 until count) {
            // Pick a value from amplitudes list or use idle sine wave if idle
            val waveVal = if (isActive) {
                if (i < amplitudes.size) amplitudes[i] else 0.05f
            } else {
                val waveProgress = System.currentTimeMillis() / 400.0
                (Math.sin(i * 0.45 + waveProgress).toFloat() + 1f) * 0.12f + 0.03f
            }

            // Exaggerate slightly for better dynamic aesthetic
            val drawHeight = (waveVal * height * 1.6f).coerceIn(3.dp.toPx(), height * 0.95f)
            val left = i * barSpacing + (barSpacing - barWidth) / 2
            val top = (height - drawHeight) / 2

            drawRoundRect(
                color = if (isActive) waveColor else StudioMuted.copy(alpha = 0.4f),
                topLeft = Offset(left, top),
                size = Size(barWidth, drawHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

// --- SPECIAL HAND-CRAFTED HIGH-PERFORMANCE ICONS ---

@Composable
fun CustomMicIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.38f, h * 0.15f),
            size = Size(w * 0.24f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.25f, h * 0.35f),
            size = Size(w * 0.5f, h * 0.4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.75f),
            end = Offset(w * 0.5f, h * 0.9f),
            strokeWidth = w * 0.08f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.9f),
            end = Offset(w * 0.65f, h * 0.9f),
            strokeWidth = w * 0.08f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun CustomHeadphonesIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.7f, w * 0.7f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.1f, h * 0.45f),
            size = Size(w * 0.15f, h * 0.35f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.75f, h * 0.45f),
            size = Size(w * 0.15f, h * 0.35f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
    }
}

@Composable
fun CustomStopIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.25f, h * 0.25f),
            size = Size(w * 0.5f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
    }
}

@Composable
fun CustomPauseIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.25f, h * 0.15f),
            size = Size(w * 0.18f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.57f, h * 0.15f),
            size = Size(w * 0.18f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
    }
}

@Composable
fun CustomVolumeUpIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.35f, h * 0.35f)
            lineTo(w * 0.6f, h * 0.15f)
            lineTo(w * 0.6f, h * 0.85f)
            lineTo(w * 0.35f, h * 0.65f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(path, color = tint)
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.45f, h * 0.35f),
            size = Size(w * 0.35f, h * 0.3f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.35f, h * 0.2f),
            size = Size(w * 0.65f, h * 0.6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
fun CustomBluetoothIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.3f, h * 0.28f)
            lineTo(w * 0.7f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.94f)
            lineTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.7f, h * 0.28f)
            lineTo(w * 0.3f, h * 0.72f)
        }
        drawPath(
            path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.08f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

// Helpers
fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
