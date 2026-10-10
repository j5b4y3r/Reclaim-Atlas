package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BlockMediaItem
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeBlockScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val bgType by viewModel.blockScreenBgType.collectAsState()
    val mediaPath by viewModel.blockScreenMediaPath.collectAsState()
    val title by viewModel.blockScreenTitle.collectAsState()
    val subtitle by viewModel.blockScreenSubtitle.collectAsState()
    val buttonText by viewModel.blockScreenButtonText.collectAsState()
    val alignment by viewModel.blockScreenAlignment.collectAsState()
    val showStreak by viewModel.blockScreenShowStreak.collectAsState()
    val showLock by viewModel.blockScreenShowLock.collectAsState()
    val showTitle by viewModel.blockScreenShowTitle.collectAsState()
    val showSubtitle by viewModel.blockScreenShowSubtitle.collectAsState()
    val showButton by viewModel.blockScreenShowButton.collectAsState()
    val showSpeaker by viewModel.blockScreenShowSpeaker.collectAsState()
    val showTimer by viewModel.blockScreenShowTimer.collectAsState()
    val showTagline by viewModel.blockScreenShowTagline.collectAsState()
    val showDarkOverlay by viewModel.blockScreenShowDarkOverlay.collectAsState()
    val persistentBlockScreen by viewModel.persistentBlockScreen.collectAsState()
    val videoMuted by viewModel.blockScreenVideoMuted.collectAsState()

    val mediaList by viewModel.blockScreenMediaList.collectAsState()

    var showLivePreview by remember { mutableStateOf(false) }
    var previewItemIndex by remember { mutableIntStateOf(0) }
    var isImportingMedia by remember { mutableStateOf(false) }

    val currentPreviewItem = remember(mediaList, previewItemIndex) {
        if (mediaList.isNotEmpty()) {
            mediaList[previewItemIndex.coerceIn(0, mediaList.lastIndex)]
        } else null
    }

    // Draft local state for editing
    var draftTitle by remember(title) { mutableStateOf(title) }
    var draftSubtitle by remember(subtitle) { mutableStateOf(subtitle) }
    var draftButtonText by remember(buttonText) { mutableStateOf(buttonText) }

    // Video picker launcher (supports 1 or multiple videos)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            isImportingMedia = true
            viewModel.importMultipleCustomBlockMedia(uris = uris, forcedType = "VIDEO") { count, error ->
                isImportingMedia = false
                if (count > 0) {
                    viewModel.updateBlockScreenCustomization(bgType = "CUSTOM")
                    Toast.makeText(context, "Added $count motivation video(s)!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, error ?: "Failed to import video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Image picker launcher (supports 1 or multiple photos)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            isImportingMedia = true
            viewModel.importMultipleCustomBlockMedia(uris = uris, forcedType = "IMAGE") { count, error ->
                isImportingMedia = false
                if (count > 0) {
                    viewModel.updateBlockScreenCustomization(bgType = "CUSTOM")
                    Toast.makeText(context, "Added $count motivation photo(s)!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, error ?: "Failed to import photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // If live preview is active, render the exact BlockedInterventionScreen full screen!
    if (showLivePreview) {
        val previewConfig = BlockScreenCustomizationData(
            bgType = bgType,
            mediaPath = currentPreviewItem?.path ?: mediaPath,
            mediaList = mediaList,
            activeMediaItem = currentPreviewItem,
            title = draftTitle,
            subtitle = draftSubtitle,
            buttonText = draftButtonText,
            alignment = alignment,
            showStreak = showStreak,
            showLock = showLock,
            showTitle = showTitle,
            showSubtitle = showSubtitle,
            showButton = showButton,
            showSpeaker = showSpeaker,
            showTimer = showTimer,
            showTagline = showTagline,
            showDarkOverlay = showDarkOverlay,
            videoMuted = videoMuted
        )

        Box(modifier = Modifier.fillMaxSize()) {
            BlockedInterventionScreen(
                target = "Preview Mode",
                reason = "This is a live preview of your customized block screen.",
                onGoHome = { showLivePreview = false },
                overrideConfig = previewConfig
            )

            // Top preview banner with Shuffle next media option
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mediaList.size > 1 && currentPreviewItem != null) {
                            "Preview #${previewItemIndex + 1}/${mediaList.size} (${currentPreviewItem.type})"
                        } else {
                            "Live Preview"
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (mediaList.size > 1) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryGreen,
                            modifier = Modifier.clickable {
                                previewItemIndex = (previewItemIndex + 1) % mediaList.size
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Shuffle", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { showLivePreview = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit preview",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        return
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Customize Block Screen",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkText
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLivePreview = true },
                        modifier = Modifier.testTag("preview_block_screen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Preview",
                            tint = PrimaryGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("customize_block_screen")
        ) {
            // Preview Action Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLivePreview = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(PrimaryGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Preview Full Block Screen",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = DarkText
                            )
                            Text(
                                text = "Test looping video, audio volume & layout live",
                                fontSize = 12.sp,
                                color = SubtleText
                            )
                        }
                    }
                    Button(
                        onClick = { showLivePreview = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("Preview", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Background Media Section
            SectionHeader(title = "Motivation Media (Videos & Images)", icon = Icons.Default.Movie)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Load one or multiple personal motivation videos and photos from your gallery. When multiple items are loaded, Reclaim will randomly show one whenever content is blocked, keeping you emotionally obsessed with protecting your streak!",
                        fontSize = 13.sp,
                        color = SubtleText,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Media Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaTypeOption(
                            label = "Default Mountain",
                            icon = Icons.Default.Landscape,
                            isSelected = bgType == "DEFAULT",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.updateBlockScreenCustomization(bgType = "DEFAULT")
                            }
                        )
                        MediaTypeOption(
                            label = if (mediaList.isNotEmpty()) "Custom (${mediaList.size})" else "Custom Media",
                            icon = Icons.Default.Movie,
                            isSelected = bgType != "DEFAULT",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (mediaList.isNotEmpty()) {
                                    viewModel.updateBlockScreenCustomization(bgType = "CUSTOM")
                                } else {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Loading indicator
                    if (isImportingMedia) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Saving media to app storage...", fontSize = 13.sp, color = SubtleText)
                        }
                    } else if (bgType != "DEFAULT" && mediaList.isNotEmpty()) {
                        // Rotation Status Banner
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PrimaryGreenLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (mediaList.size > 1) Icons.Default.Shuffle else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (mediaList.size > 1) {
                                            "🎲 Random Rotation Active (${mediaList.size} items)"
                                        } else {
                                            "1 Motivation Media Item Loaded"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = DarkText
                                    )
                                    Text(
                                        text = if (mediaList.size > 1) {
                                            "A random video or image is picked every time content is blocked"
                                        } else {
                                            "Add more videos or photos below to enable automatic random shuffle"
                                        },
                                        fontSize = 11.sp,
                                        color = SubtleText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // List of loaded media items
                        Text(
                            text = "Loaded Media in Rotation (${mediaList.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            mediaList.forEachIndexed { idx, item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (item.type == "VIDEO") PrimaryGreenLight else Color(0xFFE0F2FE)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (item.type == "VIDEO") Icons.Default.Movie else Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = if (item.type == "VIDEO") PrimaryGreen else Color(0xFF0284C7),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = item.name.ifBlank { "Media Item #${idx + 1}" },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DarkText,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = if (item.type == "VIDEO") "Video • Loops with sound" else "Image • High-resolution",
                                                    fontSize = 11.sp,
                                                    color = SubtleText
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    previewItemIndex = idx
                                                    showLivePreview = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Visibility,
                                                    contentDescription = "Preview",
                                                    tint = PrimaryGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            IconButton(
                                                onClick = {
                                                    viewModel.removeBlockMediaItem(item)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove",
                                                    tint = Color(0xFFDC2626),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Add More and Clear buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Video", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Image", fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = {
                                    viewModel.clearAllBlockMedia()
                                    viewModel.updateBlockScreenCustomization(bgType = "DEFAULT", mediaPath = "")
                                }
                            ) {
                                Text("Clear All", fontSize = 12.sp, color = Color(0xFFDC2626))
                            }
                        }
                    } else if (bgType != "DEFAULT" && mediaList.isEmpty()) {
                        // Empty custom media prompt
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No custom media uploaded yet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select one or multiple videos and photos from your gallery:",
                                fontSize = 12.sp,
                                color = SubtleText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Videos", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Images", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Video Sound settings (tied to media volume)
                    AnimatedVisibility(visible = bgType != "DEFAULT" && (mediaList.any { it.type == "VIDEO" } || mediaList.isEmpty())) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = DarkText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Video Audio (Tied to Media Volume)",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DarkText
                                                )
                                                Text(
                                                    text = if (videoMuted) "Starts muted (tap speaker icon to unmute)" else "Plays with sound (at current phone media volume)",
                                                    fontSize = 11.sp,
                                                    color = SubtleText
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = !videoMuted,
                                            onCheckedChange = { isUnmuted ->
                                                viewModel.setBlockScreenVideoMuted(!isUnmuted)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = PrimaryGreen
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "• Loops continuously until you tap Return to Safety or press Back.\n• Does not override your phone volume; respects device media stream.\n• You can also tap the speaker button directly on the block screen anytime.",
                                        fontSize = 11.sp,
                                        color = MutedText,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dark Dimming Background Overlay Toggle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dark Dimming Background Overlay",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Text(
                                    text = if (showDarkOverlay) {
                                        "Subtle dark gradient active for text contrast"
                                    } else {
                                        "Off • Media is 100% full brightness and crystal clear"
                                    },
                                    fontSize = 11.sp,
                                    color = SubtleText
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = showDarkOverlay,
                                onCheckedChange = {
                                    viewModel.updateBlockScreenCustomization(showDarkOverlay = it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryGreen
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Persistent Block Screen Lock on Top Toggle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Always Keep Block Screen on Top",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Text(
                                    text = if (persistentBlockScreen) {
                                        "On • Prevents minimizing or bypassing block screen to force stop or uninstall"
                                    } else {
                                        "Off • Standard block screen behavior"
                                    },
                                    fontSize = 11.sp,
                                    color = SubtleText
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = persistentBlockScreen,
                                onCheckedChange = {
                                    viewModel.setPersistentBlockScreen(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryGreen
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Custom Clean Texts Section
            SectionHeader(title = "Clean Texts & Motivation Copy", icon = Icons.Default.TextFields)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = {
                            draftTitle = it
                            viewModel.updateBlockScreenCustomization(title = it)
                        },
                        label = { Text("Custom Title / Headline") },
                        placeholder = { Text("e.g. Protect Your Streak") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = draftSubtitle,
                        onValueChange = {
                            draftSubtitle = it
                            viewModel.updateBlockScreenCustomization(subtitle = it)
                        },
                        label = { Text("Emotional Subtitle / Quote") },
                        placeholder = { Text("e.g. Look at your progress. Is scrolling worth sacrificing your streak?") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = draftButtonText,
                        onValueChange = {
                            draftButtonText = it
                            viewModel.updateBlockScreenCustomization(buttonText = it)
                        },
                        label = { Text("Primary Action Button Text") },
                        placeholder = { Text("e.g. Stay Disciplined (Go Home)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                draftTitle = ""
                                draftSubtitle = ""
                                draftButtonText = ""
                                viewModel.updateBlockScreenCustomization(
                                    title = "",
                                    subtitle = "",
                                    buttonText = ""
                                )
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Texts to Default", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Layout Position & Alignment Section
            SectionHeader(title = "Content & Button Position", icon = Icons.Default.Tune)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Customize the vertical positioning of text and buttons on the screen.",
                        fontSize = 13.sp,
                        color = SubtleText
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PositionOption(
                            label = "Top",
                            icon = Icons.Default.AlignVerticalTop,
                            isSelected = alignment == "TOP",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateBlockScreenCustomization(alignment = "TOP") }
                        )
                        PositionOption(
                            label = "Center",
                            icon = Icons.Default.AlignVerticalCenter,
                            isSelected = alignment == "CENTER",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateBlockScreenCustomization(alignment = "CENTER") }
                        )
                        PositionOption(
                            label = "Bottom",
                            icon = Icons.Default.AlignVerticalBottom,
                            isSelected = alignment == "BOTTOM",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateBlockScreenCustomization(alignment = "BOTTOM") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. View Visibility Toggles Section
            SectionHeader(title = "View Visibility Toggles", icon = Icons.Default.Visibility)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VisibilityToggleRow(
                        title = "Streak Badge (🔥 X Days Active)",
                        subtitle = "Highlights your active streak to reinforce discipline",
                        checked = showStreak,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showStreak = it) }
                    )
                    VisibilityToggleRow(
                        title = "Lock Icon",
                        subtitle = "Translucent circular shield badge",
                        checked = showLock,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showLock = it) }
                    )
                    VisibilityToggleRow(
                        title = "Title / Headline",
                        subtitle = "Primary alert or custom title",
                        checked = showTitle,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showTitle = it) }
                    )
                    VisibilityToggleRow(
                        title = "Subtitle / Emotional Message",
                        subtitle = "Detailed reasoning or custom motivation quote",
                        checked = showSubtitle,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showSubtitle = it) }
                    )
                    VisibilityToggleRow(
                        title = "Action Button",
                        subtitle = "Return to safety / Return to home button",
                        checked = showButton,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showButton = it) }
                    )
                    VisibilityToggleRow(
                        title = "Speaker Mute / Unmute Button",
                        subtitle = "Top-right button to toggle video sound or chime",
                        checked = showSpeaker,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showSpeaker = it) }
                    )
                    VisibilityToggleRow(
                        title = "Urge Surfer Timer Button",
                        subtitle = "30-second breathing pause for urge surfing",
                        checked = showTimer,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showTimer = it) }
                    )
                    VisibilityToggleRow(
                        title = "Bottom Tagline",
                        subtitle = "'A better you is waiting.'",
                        checked = showTagline,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showTagline = it) }
                    )
                    VisibilityToggleRow(
                        title = "Dark Dimming Background Overlay",
                        subtitle = "Dims background slightly for text contrast. Turn OFF for 100% full brightness and crystal clear media readability.",
                        checked = showDarkOverlay,
                        onCheckedChange = { viewModel.updateBlockScreenCustomization(showDarkOverlay = it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reset All Customizations
            OutlinedButton(
                onClick = {
                    viewModel.resetBlockScreenCustomization()
                    draftTitle = ""
                    draftSubtitle = ""
                    draftButtonText = ""
                    Toast.makeText(context, "Block screen reset to default", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = SubtleText)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All to Defaults", color = SubtleText)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
    }
}

@Composable
private fun MediaTypeOption(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryGreenLight else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) PrimaryGreen else Color(0xFFE2E8F0)
        ),
        modifier = modifier.height(60.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) PrimaryGreen else SubtleText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PrimaryGreen else DarkText
            )
        }
    }
}

@Composable
private fun PositionOption(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryGreenLight else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) PrimaryGreen else Color(0xFFE2E8F0)
        ),
        modifier = modifier.height(54.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) PrimaryGreen else SubtleText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PrimaryGreen else DarkText
            )
        }
    }
}

@Composable
private fun VisibilityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DarkText
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SubtleText
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen
            )
        )
    }
}
