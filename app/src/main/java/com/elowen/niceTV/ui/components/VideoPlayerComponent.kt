package com.elowen.niceTV.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerComponent(
    player: ExoPlayer, 
    modifier: Modifier = Modifier,
    cacheProgress: Float = 0f,
    isFullScreen: Boolean = false,
    controlsVisible: Boolean = true, // [HOISTED]
    onBack: () -> Unit = {},
    onFullScreenToggle: (Boolean) -> Unit = {},
    onControlsVisibilityChanged: (Boolean) -> Unit = {} // [NEW CALLBACK]
) {
    val context = LocalContext.current
    val gestureSlop = LocalViewConfiguration.current.touchSlop * 1.5f
    val seekSensitivity = 0.07f
    val doubleTapTimeoutMs = 300L
    val singleTapDelayMs = 260L
    val scope = rememberCoroutineScope()
    
    // 状态管理
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var currentTime by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var bufferedPercentage by remember { mutableIntStateOf(player.bufferedPercentage) }
    var isBuffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }

    // 手势状态
    var gestureInfo by remember { mutableStateOf<String?>(null) }
    var isGesturing by remember { mutableStateOf(false) }
    
    // 用于水平进度滑动时保存 seek 目标
    var seekingPosition by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                duration = p.duration.coerceAtLeast(0L)
                if (seekingPosition == null) {
                    currentTime = p.currentPosition.coerceAtLeast(0L)
                }
                bufferedPercentage = p.bufferedPercentage
                isPlaying = p.isPlaying
                isBuffering = p.playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        
        onDispose {
            player.removeListener(listener)
        }
    }

    // 更新进度条/缓冲的定时器（确保缓存与缓冲变化能实时反映）
    LaunchedEffect(player) {
        while (true) {
            if (seekingPosition == null) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                if (pos != currentTime) currentTime = pos
            }
            val newDuration = player.duration.coerceAtLeast(0L)
            if (newDuration != duration) duration = newDuration
            val newBuffered = player.bufferedPercentage
            if (newBuffered != bufferedPercentage) bufferedPercentage = newBuffered
            val newIsPlaying = player.isPlaying
            if (newIsPlaying != isPlaying) isPlaying = newIsPlaying
            val newIsBuffering = player.playbackState == Player.STATE_BUFFERING
            if (newIsBuffering != isBuffering) isBuffering = newIsBuffering
            delay(500)
        }
    }

    var isLocked by remember { mutableStateOf(false) }

    // 自动隐藏控制栏
    LaunchedEffect(controlsVisible, isPlaying, isGesturing) {
        if (controlsVisible && isPlaying && !isGesturing) {
            delay(4000)
            if (!isGesturing) {
                onControlsVisibilityChanged(false)
            }
        }
    }

    // 保持屏幕常亮
    DisposableEffect(isPlaying) {
        val window = (context as? Activity)?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 用于处理单击/双击冲突的状态
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // 集中处理所有手势，避免多个 pointerInput 冲突
            .pointerInput(controlsVisible, isPlaying, isLocked, isFullScreen) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var gestureType: String? = null
                    var dragXAtGestureStart = 0f
                    
                    var volumeAccumulator: Float
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val window = (context as? Activity)?.window
                    val startBrightness = window?.attributes?.screenBrightness?.let {
                        if (it < 0) {
                            // Read actual system brightness (0-255) and normalize to 0-1
                            try {
                                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                            } catch (_: Exception) { 0.5f }
                        } else it
                    } ?: 0.5f
                    val startPosition = player.currentPosition
                    
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val dragX = change.positionChange().x
                            val dragY = change.positionChange().y
                            totalDragX += dragX
                            totalDragY += dragY
                            
                            // 确定手势方向 (动态阈值，降低误触)
                            if (gestureType == null && (abs(totalDragX) > gestureSlop || abs(totalDragY) > gestureSlop)) {
                                isGesturing = true
                                gestureType = if (abs(totalDragX) > abs(totalDragY) && (!isFullScreen || (startX > width * 0.2f && startX < width * 0.8f))) "seek" else if (startX < width * 0.5f) "brightness" else if (!isLocked) "volume" else null
                                dragXAtGestureStart = totalDragX
                                // 如果开始滑动，立即取消任何待处理的点击逻辑
                                tapJob?.cancel()
                                tapJob = null
                            }
                            
                            when (gestureType) {
                                "seek" -> {
                                    val effectiveDragX = totalDragX - dragXAtGestureStart
                                    val seekDelta = (effectiveDragX / width * duration * seekSensitivity).toLong()
                                    val newPosition = (startPosition + seekDelta).coerceIn(0, duration)
                                    seekingPosition = newPosition
                                    currentTime = newPosition
                                    val sign = if ((newPosition - startPosition) >= 0) "+" else ""
                                    gestureInfo = "${formatTime(newPosition)} (${sign}${(newPosition - startPosition)/1000}s)"
                                }
                                "brightness" -> {
                                    val delta = -totalDragY / height
                                    val newBrightness = (startBrightness + delta).coerceIn(0.01f, 1f)
                                    window?.let {
                                        val lp = it.attributes
                                        lp.screenBrightness = newBrightness
                                        it.attributes = lp
                                    }
                                    gestureInfo = "亮度: ${(newBrightness * 100).toInt()}%"
                                }
                                "volume" -> {
                                    val delta = -totalDragY / height
                                    volumeAccumulator = delta * maxVolume * 1.2f
                                    val newVolume = (startVolume + volumeAccumulator).toInt().coerceIn(0, maxVolume)
                                    if (newVolume != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                    }
                                    val volumePercent = if (maxVolume > 0) {
                                        (newVolume.toFloat() / maxVolume * 100).toInt()
                                    } else {
                                        0
                                    }
                                    gestureInfo = "音量: $volumePercent%"
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    
                    // 抬起后的处理
                    isGesturing = false
                    if (gestureType != null) {
                        // 滑动手势结束
                        if (gestureType == "seek") {
                            seekingPosition?.let { player.seekTo(it) }
                            seekingPosition = null
                        }
                        scope.launch { delay(800); try { gestureInfo = null } catch(_: Exception) {} }
                    } else {
                        // 点击手势逻辑
                        val currentTimeMillis = System.currentTimeMillis()
                        if (currentTimeMillis - lastTapTime < doubleTapTimeoutMs) {
                            tapJob?.cancel()
                            tapJob = null
                            lastTapTime = 0L

                            if (isLocked) {
                                isLocked = false
                                onControlsVisibilityChanged(true)
                                gestureInfo = "已解锁"
                                scope.launch { delay(800); try { gestureInfo = null } catch(_: Exception) {} }
                            } else {
                                val offset = down.position
                                if (offset.x < width * 0.35) {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                    gestureInfo = "-10s"
                                } else if (offset.x > width * 0.65) {
                                    val seekTarget = player.currentPosition + 10000
                                    val boundedSeekTarget = player.duration
                                        .takeIf { it > 0 }
                                        ?.let { seekTarget.coerceAtMost(it) }
                                        ?: seekTarget
                                    player.seekTo(boundedSeekTarget)
                                    gestureInfo = "+10s"
                                } else {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                                scope.launch { delay(1000); try { gestureInfo = null } catch(_: Exception) {} }
                            }
                        } else if (!isLocked) {
                            // [疑似单击] - 启动定时器等待双击确认
                            lastTapTime = currentTimeMillis
                            tapJob?.cancel()
                            tapJob = scope.launch {
                                delay(singleTapDelayMs)
                                onControlsVisibilityChanged(!controlsVisible)
                            }
                        } else {
                            lastTapTime = 0L
                        }
                    }
                }
            }
    ) {
        // 1. 播放器视图
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView -> playerView.player = player }
        )

        // 2. 加载缓冲指示器 (animated ring)
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "buffer")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
                label = "rotation"
            )
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "pulse"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse; rotationZ = rotation },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(56.dp)) {
                        val strokeWidth = 3.dp.toPx()
                        // Outer glow ring
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, Color(0xFF00E5FF), Color(0xFF00BCD4), Color.Transparent)
                            ),
                            startAngle = 0f, sweepAngle = 270f, useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${bufferedPercentage}%",
                    color = Color(0xFF00E5FF),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. 手势反馈指示器
        val rememberedGestureInfo by remember {
            derivedStateOf { gestureInfo }
        }
        var lastGestureInfo by remember { mutableStateOf("") }
        LaunchedEffect(rememberedGestureInfo) {
            rememberedGestureInfo?.let {
                lastGestureInfo = it
            }
        }
        AnimatedVisibility(
            visible = gestureInfo != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text(
                    text = lastGestureInfo,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. 锁定按钮（全屏时显示在左边）
        if (isFullScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                val lockIcon = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen
                IconButton(
                    onClick = {
                        isLocked = !isLocked
                        if (isLocked) onControlsVisibilityChanged(false)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color.Black.copy(alpha = if (isLocked) 0.5f else 0.3f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = if (isLocked) 0.6f else 0.2f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = lockIcon,
                        contentDescription = if (isLocked) "已锁定" else "锁定",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 5. 控制层
        AnimatedVisibility(
            visible = controlsVisible && !isGesturing && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val safePadding = if (isFullScreen) WindowInsets.systemBars.asPaddingValues() else PaddingValues(0.dp)

            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {

                // -- Top bar with gradient --
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                        .padding(top = safePadding.calculateTopPadding())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // -- Center play/pause with frosted circle --
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .shadow(12.dp, CircleShape, ambientColor = Color(0xFF00BCD4).copy(alpha = 0.3f), spotColor = Color(0xFF00BCD4).copy(alpha = 0.3f))
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // [Premium Progress Bar Container]
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        )
                        .padding(bottom = safePadding.calculateBottomPadding() + 12.dp, start = 16.dp, end = 8.dp, top = 28.dp)
                ) {
                    // Progress Bar with Premium Design
                    val interactionSource = remember { MutableInteractionSource() }
                    val isDragging by interactionSource.collectIsDraggedAsState()
                    
                    val bufferedFraction = (bufferedPercentage.toFloat().coerceIn(0f, 100f) / 100f)
                    val cacheFraction = (cacheProgress.coerceIn(0f, 100f) / 100f)
                    val playbackFraction = if (duration > 0) (currentTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                    
                    // Animated progress for smooth transitions
                    val animatedPlayback by animateFloatAsState(
                        targetValue = playbackFraction,
                        animationSpec = tween(durationMillis = if (isDragging) 0 else 150),
                        label = "playback"
                    )
                    val animatedCache by animateFloatAsState(
                        targetValue = cacheFraction,
                        animationSpec = tween(durationMillis = 300),
                        label = "cache"
                    )
                    val animatedBuffer by animateFloatAsState(
                        targetValue = bufferedFraction,
                        animationSpec = tween(durationMillis = 200),
                        label = "buffer"
                    )
                    
                    // Track height animates when dragging
                    val trackHeight by animateFloatAsState(
                        targetValue = if (isDragging) 8f else 4f,
                        animationSpec = tween(durationMillis = 150),
                        label = "trackHeight"
                    )
                    
                    // Cyan gradient colors
                    val cyanGradient = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00E5FF),
                            Color(0xFF00BCD4),
                            Color(0xFF00ACC1)
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Layered Progress Tracks
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight.dp)
                                .clip(RoundedCornerShape(50))
                        ) {
                            // 1. Background track with glassmorphism
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(
                                        width = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                            
                            // 2. Disk cache progress (subtle cyan tint)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedCache)
                                    .background(
                                        Color(0xFF00BCD4).copy(alpha = 0.25f),
                                        RoundedCornerShape(50)
                                    )
                            )
                            
                            // 3. Buffer progress (brighter)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedBuffer)
                                    .background(
                                        Color.White.copy(alpha = 0.35f),
                                        RoundedCornerShape(50)
                                    )
                            )
                            
                            // 4. Playback progress with gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedPlayback)
                                    .background(cyanGradient, RoundedCornerShape(50))
                            )
                        }
                        
                        // Interactive Slider (invisible, just for interaction)
                        val sliderMax = duration.toFloat().coerceAtLeast(1f)
                        val sliderValue = currentTime.toFloat().coerceIn(0f, sliderMax)
                        Slider(
                            value = sliderValue,
                            onValueChange = { currentTime = it.toLong() },
                            onValueChangeFinished = { player.seekTo(currentTime) },
                            valueRange = 0f..sliderMax,
                            interactionSource = interactionSource,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            thumb = {
                                // Custom glowing thumb
                                Box(
                                    modifier = Modifier
                                        .size(if (isDragging) 20.dp else 14.dp)
                                        .shadow(
                                            elevation = if (isDragging) 8.dp else 4.dp,
                                            shape = CircleShape,
                                            ambientColor = Color(0xFF00BCD4),
                                            spotColor = Color(0xFF00BCD4)
                                        )
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color.White,
                                                    Color(0xFF00E5FF)
                                                )
                                            ),
                                            CircleShape
                                        )
                                        .border(
                                            width = 2.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF00E5FF),
                                                    Color(0xFF00BCD4)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))

                    // Time labels and controls row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { onFullScreenToggle(!isFullScreen) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "FullScreen",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
    }
}
