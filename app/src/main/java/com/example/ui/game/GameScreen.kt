package com.example.ui.game

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRecord
import java.util.Date
import kotlin.math.sqrt

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val records by viewModel.records.collectAsState()

    // Enforce RTL (Right to Left) Arabic layout for all screens
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color(0xFF1C1B1F) // Elegant Dark main canvas color
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Subtle elegant dark gradient background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF252429), Color(0xFF1C1B1F)),
                            center = center,
                            radius = size.maxDimension * 0.7f
                        )
                    )
                }

                // Primary state routing switch
                when (state.playState) {
                    GamePlayState.INTRO -> IntroScreen(
                        state = state,
                        records = records,
                        onTiltStartTriggered = { viewModel.startIntroToGame() }
                    )
                    GamePlayState.PLAYING,
                    GamePlayState.FALLING,
                    GamePlayState.ESCAPING -> PlayingScreen(
                        state = state,
                        viewModel = viewModel
                    )
                    GamePlayState.LEVEL_FAILED -> LevelFailedScreen(
                        state = state,
                        onRestart = { viewModel.restartCurrentLevel() },
                        onExit = { viewModel.exitToIntro() }
                    )
                    GamePlayState.LEVEL_SUCCESS -> LevelSuccessScreen(
                        state = state,
                        onProceed = { viewModel.proceedToNextLevel() },
                        onRestart = { viewModel.restartCurrentLevel() },
                        onExit = { viewModel.exitToIntro() }
                    )
                    GamePlayState.GAME_COMPLETED -> GameCompletedScreen(
                        records = records,
                        onReplayAll = { viewModel.selectLevelDirectly(1) },
                        onExit = { viewModel.exitToIntro() }
                    )
                }
            }
        }
    }
}

/**
 * 1. Intro Splash Screen with Interactive Ball Focus Lock (Tilt-To-Start) Tutorial.
 * Completely buttons-free to start gameplay!
 */
@Composable
fun IntroScreen(
    state: UiState,
    records: List<GameRecord>,
    onTiltStartTriggered: () -> Unit
) {
    // Local ball state simulator for tilt-to-start visual lock
    var lockBallX by remember { mutableStateOf(50f) }
    var lockBallY by remember { mutableStateOf(50f) }
    var holdDurationMs by remember { mutableStateOf(0L) }
    val targetThresholdRadius = 15f // lock accuracy bounds

    val context = LocalContext.current

    // Physics ticks for Focus Lock
    LaunchedEffect(state.tiltX, state.tiltY) {
        // Accelerate starter ball local simulated coordinate relative to tilt
        val simulatedVX = state.tiltX * 65f
        val simulatedVY = -state.tiltY * 65f

        lockBallX = (lockBallX + simulatedVX * 0.016f).coerceIn(15f, 85f)
        lockBallY = (lockBallY + simulatedVY * 0.016f).coerceIn(15f, 85f)

        // Check closeness to true dead center (50, 50)
        val distanceToCenter = sqrt((lockBallX - 50f) * (lockBallX - 50f) + (lockBallY - 50f) * (lockBallY - 50f))

        if (distanceToCenter < targetThresholdRadius) {
            // User is stabilizing the circle
            holdDurationMs += 16
            if (holdDurationMs >= 1500) {
                onTiltStartTriggered()
            }
        } else {
            // Focus broken, reset countdown
            holdDurationMs = (holdDurationMs - 40).coerceAtLeast(0)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val lockRingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Visual Logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = Color(0xFF4F378B).copy(alpha = 0.15f),
                        shape = CircleShape
                    )
                    .border(1.dp, Color(0xFF4F378B).copy(alpha = 0.35f), CircleShape)
            ) {
                Canvas(modifier = Modifier.size(54.dp)) {
                    // Lavender nested balance gyro matching Elegant Dark
                    drawCircle(
                        color = Color(0xFFD0BCFF),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFEADDFF),
                        radius = size.width * 0.33f * lockRingPulse,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF4F378B),
                        radius = size.width * 0.15f
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "متاهة التوازن ثلاثية الأبعاد",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6E1E5),
                textAlign = TextAlign.Center
            )

            Text(
                text = "تحكّم في الجاذبية لتفادي السقوط والوصول للأهداف",
                fontSize = 13.sp,
                color = Color(0xFFCAC4D0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Focus Lock Interactive Ring Widget
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "🔒 قفل بدء التوازن",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0BCFF),
                textAlign = TextAlign.Center
            )
            Text(
                text = "ثبّت الكرة الفضية في المنتصف للبدء",
                fontSize = 12.sp,
                color = Color(0xFFCAC4D0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Focus Lock Screen Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
                    .background(Color(0xFF2B2930), shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(24.dp))
                    .shadow(12.dp, shape = RoundedCornerShape(24.dp))
                    .testTag("tilt_to_start_region")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scale = size.minDimension
                    val c = center

                    // Target zone
                    drawCircle(
                        color = if (holdDurationMs > 0) Color(0xFFD0BCFF).copy(alpha = 0.15f) else Color(0xFF1C1B1F),
                        radius = scale * (targetThresholdRadius / 100f),
                        center = c
                    )
                    drawCircle(
                        color = if (holdDurationMs > 500) Color(0xFFD0BCFF) else Color(0xFF49454F),
                        radius = scale * (targetThresholdRadius / 100f) * lockRingPulse,
                        center = c,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw focus lock progress sweep ring in neon lavender
                    val progressSweep = (holdDurationMs.toFloat() / 1500f) * 360f
                    drawArc(
                        color = Color(0xFFD0BCFF),
                        startAngle = -90f,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = Offset(16.dp.toPx(), 16.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 32.dp.toPx(), size.height - 32.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw local starter simulated metal ball
                    val ballPos = Offset(
                        lockBallX / 100f * size.width,
                        lockBallY / 100f * size.height
                    )
                    // Ball Drop shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = scale * 0.05f,
                        center = ballPos + Offset(2.dp.toPx(), 2.dp.toPx())
                    )
                    // Ball silver layout
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFEADDFF), Color(0xFF4F378B)),
                            center = ballPos - Offset(1.5.dp.toPx(), 1.5.dp.toPx())
                        ),
                        radius = scale * 0.045f,
                        center = ballPos
                    )
                }

                if (holdDurationMs > 0) {
                    val pct = ((holdDurationMs.toFloat() / 1500f) * 100).toInt().coerceIn(0, 100)
                    Text(
                        text = "$pct%",
                        color = Color(0xFFD0BCFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }
            }

            // Clickable bypass button for accessibility/testing
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTiltStartTriggered() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "أو اضغط البدء المباشر اضطرارياً ⚡",
                    color = Color(0xFFD0BCFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("skip_tutorial_button")
                )
            }
        }

        // Instructions and past records logs
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🏆 سجل الأداء والسباقات",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (records.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "info",
                            tint = Color(0xFFCAC4D0)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "لا توجد أرقام قياسية مسجلة بعد. ابدأ جولتك الأولى!",
                            fontSize = 12.sp,
                            color = Color(0xFF938F99)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(records.filter { it.isWin }.take(3)) { record ->
                            val level = LevelRegistry.levels.firstOrNull { it.index == record.levelIndex }
                            val dateStr = DateFormat.format("yyyy/MM/dd", Date(record.dateTimestamp)).toString()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1C1B1F), shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFF49454F).copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "win",
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = level?.name ?: "مرحلة ${record.levelIndex}",
                                        fontSize = 12.sp,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${"%.2f".format(record.timeSpentSec)} ثانية",
                                        fontSize = 12.sp,
                                        color = Color(0xFFD0BCFF),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = dateStr,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCAC4D0)
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

/**
 * 2. Active Gameplay Screen. Displays direct physical telemetries,
 * standard timer counting up, level details, and the custom MazeCanvas.
 */
@Composable
fun PlayingScreen(
    state: UiState,
    viewModel: GameViewModel
) {
    val level = LevelRegistry.levels.firstOrNull { it.index == state.currentLevelIndex } ?: return

    val correctedX = state.tiltX - state.calibrationX
    val correctedY = state.tiltY - state.calibrationY
    val totalTiltDeg = sqrt(correctedX * correctedX + correctedY * correctedY) * 57.3f
    val tiltPercent = (totalTiltDeg / 35f).coerceIn(0f, 1f) // Normalize up to 35 degrees

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper stats bar: Material 3 Small Top App Bar Style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level indicator details with a custom balance icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF4F378B), CircleShape)
                        .clickable { viewModel.calibrateSensors() }, // Interactive shortcut
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏸",
                        fontSize = 14.sp,
                        color = Color(0xFFEADDFF)
                    )
                }
                Column {
                    Text(
                        text = "المستوى ${level.index} / ${LevelRegistry.levels.size}",
                        fontSize = 11.sp,
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = level.name,
                        fontSize = 16.sp,
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live stopwatch stopwatch
            Box(
                modifier = Modifier
                    .background(Color(0xFF313033), shape = RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⏱",
                        fontSize = 12.sp,
                        color = Color(0xFFD0BCFF)
                    )
                    Text(
                        text = "${"%.2f".format(state.timeElapsed)} ث",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )
                }
            }
        }

        // Dynamic 3D Maze Board
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            MazeCanvas(
                state = state,
                modifier = Modifier.fillMaxSize()
            )

            // Success overlay transition
            if (state.playState == GamePlayState.ESCAPING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFFD0BCFF).copy(alpha = 0.15f))
                )
            }
        }

        // Interaction Indicator / Footer matching Elegant Dark spec
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gyroscope Sensitivity Display
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "🔄",
                            fontSize = 12.sp,
                            color = Color(0xFFCAC4D0)
                        )
                        Text(
                            text = "قوة ميلان المستشعر",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFCAC4D0)
                        )
                    }
                    Text(
                        text = "أفقياً: ${"%.1f".format(correctedX * 57.3f)}° | عمودياً: ${"%.1f".format(correctedY * 57.3f)}°",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Smooth M3 elegant track bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFF313033), shape = CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(tiltPercent)
                            .height(6.dp)
                            .background(Color(0xFFD0BCFF), shape = CircleShape)
                    )
                }
            }

            // Bottom Stats Cards Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gold Target card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2B2930), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(20.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "الهدف الذهبي",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCAC4D0)
                        )
                        Text(
                            text = "${level.goldTime} ثانية",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                    }
                }

                // Live Action calibration panel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2B2930), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(20.dp))
                        .clickable { viewModel.calibrateSensors() }
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "معايرة الاتجاه",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCAC4D0)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "اضبط الصفر ⚡",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                modifier = Modifier.testTag("calibrate_button")
                            )
                        }
                    }
                }
            }

            // Instruction Hint
            Text(
                text = "أمل جهازك لتوجيه الكرة الفولاذية في المتاهة وتفادي السقوط في ثقوب الجاذبية العميقة.",
                fontSize = 11.sp,
                color = Color(0xFF938F99),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 3. Screen overlays for Level Failure.
 */
@Composable
fun LevelFailedScreen(
    state: UiState,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F).copy(alpha = 0.95f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFEF5350).copy(alpha = 0.15f), shape = CircleShape)
                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🕳️", fontSize = 48.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "جذبتك ثقوب اللابؤرة!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF5350),
                textAlign = TextAlign.Center
            )

            Text(
                text = "لقد سقطت كرة التوازن في أحد التجويفات الحرجة للمتاهة. تتطلب هذه الانحناءات مستويات ثبات عالية وتركيزاً مطلقاً.",
                fontSize = 13.sp,
                color = Color(0xFFCAC4D0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Failure telemetry analysis readout with Elegant Dark cards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), shape = RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "الوقت المنقضي", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                        Text(text = "${"%.1f".format(state.timeElapsed)} ثانية", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "رقم المرحلة", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                        Text(text = "${state.currentLevelIndex}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .testTag("retry_level_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "retry", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "أعد المحاولة بثبات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(14.dp))
                    .testTag("exit_failed_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "القائمة الرئيسية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
            }
        }
    }
}

/**
 * 4. Screen overlays for Level Success and Medals indicators.
 */
@Composable
fun LevelSuccessScreen(
    state: UiState,
    onProceed: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    val level = LevelRegistry.levels.firstOrNull { it.index == state.currentLevelIndex } ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F).copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF4F378B).copy(alpha = 0.15f), shape = CircleShape)
                    .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔮", fontSize = 52.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "هروب مظفّر!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0BCFF),
                textAlign = TextAlign.Center
            )

            Text(
                text = "لقد نجحت في التحكم بقوانين الجاذبية واجتياز متاهة العقبات!",
                fontSize = 13.sp,
                color = Color(0xFFCAC4D0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Standard details card with Gold Trophy state if earned
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "الوقت المسجل", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                            Text(
                                text = "${"%.2f".format(state.timeElapsed)} ثانية",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6E1E5)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "الزمن الذهبي القياسي", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                            Text(
                                text = "${"%.1f".format(level.goldTime)} ثانية",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF)
                            )
                        }
                    }

                    if (state.isGoldEarned) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF4F378B).copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(text = "👑", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "وسام توازن الذهب الأبدي!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF)
                                )
                                Text(
                                    text = "مذهل! لقد سحقت الزمن القياسي تحت تأثير الجاذبية!",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEADDFF)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "💡 نصيحة: أنهِ المرحلة أسرع من ${"%.1f".format(level.goldTime)} ثوانٍ لربح وسام الذهب الملوكي!",
                            fontSize = 11.sp,
                            color = Color(0xFF938F99),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            val nextButtonLabel = if (state.currentLevelIndex < LevelRegistry.levels.size) {
                "تحدّي المرحلة التالية"
            } else {
                "عرض الفوز والتخرّج النهائي 🏅"
            }

            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp)
                    .testTag("next_level_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "next", tint = Color(0xFF1C1B1F))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = nextButtonLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("restart_success_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "إعادة اللعب", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                }

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(12.dp))
                        .testTag("exit_success_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "الرئيسية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
                }
            }
        }
    }
}

/**
 * 5. Screen overlays for Overall Game Graduation.
 */
@Composable
fun GameCompletedScreen(
    records: List<GameRecord>,
    onReplayAll: () -> Unit,
    onExit: () -> Unit
) {
    val totalWins = records.filter { it.isWin }.distinctBy { it.levelIndex }.size
    val totalGoldEarned = LevelRegistry.levels.count { level ->
        val record = records.firstOrNull { it.levelIndex == level.index && it.isWin }
        record != null && record.timeSpentSec <= level.goldTime
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F).copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color(0xFF4F378B).copy(alpha = 0.15f), shape = CircleShape)
                    .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "👑", fontSize = 56.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "بطل التوازن الأبدي!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0BCFF),
                textAlign = TextAlign.Center
            )

            Text(
                text = "تخرّج كامل مبارك! لقد قهرت الجاذبية وتفوقت على دهاليز المتاهات الأربعة بدقة وثبات منقطعي النظير.",
                fontSize = 13.sp,
                color = Color(0xFFCAC4D0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Global Statistics Display Cards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "📊 تفاصيل التحصيل القياسي",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "العقبات المجتازة", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                            Text(text = "$totalWins / ${LevelRegistry.levels.size}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                        }

                        Column {
                            Text(text = "الأوسمة الفخرية", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                            Text(text = "$totalGoldEarned / ${LevelRegistry.levels.size} 🏆", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                        }

                        val totalTime = records.filter { it.isWin }.distinctBy { it.levelIndex }.sumOf { it.timeSpentSec.toDouble() }
                        Column {
                            Text(text = "الزمن الصافي التراكمي", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                            Text(text = "${"%.2f".format(totalTime)} ث", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEADDFF))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onReplayAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .testTag("restart_all_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "replay", tint = Color(0xFF1C1B1F))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "أعد الكرّة لكسر الأرقام القياسية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .border(1.dp, Color(0xFF49454F), shape = RoundedCornerShape(14.dp))
                    .testTag("exit_completed_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "العودة للقائمة الرئيسية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
            }
        }
    }
}
