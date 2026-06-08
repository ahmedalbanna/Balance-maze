package com.example.ui.game

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun MazeCanvas(
    state: UiState,
    modifier: Modifier = Modifier
) {
    val level = LevelRegistry.levels.firstOrNull { it.index == state.currentLevelIndex } ?: return

    // Infinite transition for goal pulsing and wall glow aura
    val infiniteTransition = rememberInfiniteTransition(label = "portal_glow")
    val portalPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val environmentGlowAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_rotation"
    )

    Box(
        modifier = modifier
            .padding(16.dp)
            .aspectRatio(1f) // Keep game board strictly square to prevent stretch
            .shadow(24.dp, shape = RoundedCornerShape(32.dp))
            .border(
                width = 10.dp,
                color = Color(0xFF313033), // Elegant Dark frame border
                shape = RoundedCornerShape(32.dp)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF252429)) // Elegant Dark board background
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val boardSize = size.minDimension
            val offsetX = (size.width - boardSize) / 2f
            val offsetY = (size.height - boardSize) / 2f

            // Helper to translate coordinate from normalized space (0..100) to physical local offset
            fun mapCoord(x: Float, y: Float): Offset {
                return Offset(
                    offsetX + (x / 100f) * boardSize,
                    offsetY + (y / 100f) * boardSize
                )
            }

            fun mapSize(dim: Float): Float {
                return (dim / 100f) * boardSize
            }

            // Unpack calibrated tilts for rendering perspective
            val tiltX = state.tiltX - state.calibrationX
            val tiltY = state.tiltY - state.calibrationY

            // 3D Extrusion Factor: represents how high walls stand
            val wallExtrusionPx = mapSize(1.6f)
            val dx = tiltX * wallExtrusionPx       // wall lean vector x
            val dy = -tiltY * wallExtrusionPx      // wall lean vector y

            // 1. DRAW PERSPECTIVE BACKDROP SHADOW & GRID DOTS
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD0BCFF).copy(alpha = 0.05f), Color.Transparent),
                    center = mapCoord(50f, 50f) + Offset(-dx * 2, -dy * 2),
                    radius = boardSize * 0.7f
                )
            )

            // Draw a subtle dotted background grid representing engineering precision
            val dotSpacing = mapSize(10f)
            var curX = dotSpacing
            while (curX < boardSize) {
                var curY = dotSpacing
                while (curY < boardSize) {
                    drawCircle(
                        color = Color(0xFFD0BCFF).copy(alpha = 0.08f),
                        radius = mapSize(0.3f),
                        center = Offset(offsetX + curX, offsetY + curY)
                    )
                    curY += dotSpacing
                }
                curX += dotSpacing
            }

            // 2. DRAW TARGET HOLES (TRAPS) WITH 3D HOLLOW EFFECT
            for (hole in level.holes) {
                val centerOffset = mapCoord(hole.center.x, hole.center.y)
                val physRadius = mapSize(hole.radius)

                // Hole dark well
                drawCircle(
                    color = Color(0xFF0D0D11),
                    radius = physRadius,
                    center = centerOffset
                )

                // Inner 3D perspective shadow rim (makes the hole look deep)
                val innerRimOffset = Offset(dx * 0.45f, dy * 0.45f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black,
                            Color(0xFFEADDFF).copy(alpha = 0.1f), // Glowing purple heat
                            Color.Transparent
                        ),
                        center = centerOffset + innerRimOffset,
                        radius = physRadius
                    ),
                    radius = physRadius * 0.95f,
                    center = centerOffset
                )

                // Outer border glow rim mapping to classic M3 Dark outlining
                drawCircle(
                    color = Color(0xFF49454F).copy(alpha = 0.9f),
                    radius = physRadius * 1.0f,
                    center = centerOffset,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = mapSize(0.5f))
                )
            }

            // 3. DRAW EXIT GOAL PORTAL IN LAVENDER NEON RIPPLES
            val goalCenter = mapCoord(level.goal.center.x, level.goal.center.y)
            val goalRadius = mapSize(level.goal.radius)

            // Draw glowing lavender core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD0BCFF), // Intense elegant lavender
                        Color(0xFF8062C7).copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = goalCenter
                ),
                radius = goalRadius * portalPulse,
                center = goalCenter
            )

            // Pulsing target halos
            drawCircle(
                color = Color(0xFFEADDFF).copy(alpha = 0.7f * (2f - portalPulse)),
                radius = goalRadius * (0.5f + (portalPulse * 0.5f)),
                center = goalCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = mapSize(0.5f))
            )
            drawCircle(
                color = Color(0xFFB19DFF).copy(alpha = 0.4f * (2.2f - portalPulse)),
                radius = goalRadius * portalPulse * 1.3f,
                center = goalCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = mapSize(0.3f))
            )

            // 4. DRAW 3D EXTRUDED LABYRINTH WALLS
            val wallStrokeWidth = mapSize(1.4f)
            val topWallStroke = mapSize(1.2f)

            for (wall in level.walls) {
                val startBase = mapCoord(wall.start.x, wall.start.y)
                val endBase = mapCoord(wall.end.x, wall.end.y)

                val startTop = startBase + Offset(dx, dy)
                val endTop = endBase + Offset(dx, dy)

                // Draw solid 3D depth face between baseline and lean top-line
                val wallPolygon = Path().apply {
                    moveTo(startBase.x, startBase.y)
                    lineTo(endBase.x, endBase.y)
                    lineTo(endTop.x, endTop.y)
                    lineTo(startTop.x, startTop.y)
                    close()
                }

                // Extrusion carbon shadows
                drawPath(
                    path = wallPolygon,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1C1B1F), // Deep space black base shadow
                            Color(0xFF313033)  // Moderate grey upper limit
                        ),
                        start = startBase,
                        end = endTop
                    )
                )

                // Side ambient shadow occlusion
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = startBase,
                    end = endBase,
                    strokeWidth = wallStrokeWidth * 1.3f,
                    cap = StrokeCap.Round
                )

                // Elevated top glowing railing matching elegant purple theme
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD0BCFF), // Glowing lavender crown
                            Color(0xFF49454F), // Charcoal intermediate accent
                            Color(0xFFEADDFF)  // Brighter highlighting
                        ),
                        start = startTop,
                        end = endTop
                    ),
                    start = startTop,
                    end = endTop,
                    strokeWidth = topWallStroke,
                    cap = StrokeCap.Round
                )
            }

            // 5. DRAW THE PHYSICAL STEEL BALL WITH PARALLAX OR HIGHLIGHTS Shading
            if (state.ballAlpha > 0.01f) {
                val ballCenter = mapCoord(state.ballX, state.ballY)
                val ballRadius = mapSize(state.ballRadius) * state.ballScale

                // Ball drop shadow (drawn offset OPPOSITE to the tilt direction)
                val shadowOffset = Offset(-dx * 0.75f, -dy * 0.75f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                        center = ballCenter + shadowOffset,
                        radius = ballRadius * 1.3f
                    ),
                    radius = ballRadius * 1.4f,
                    center = ballCenter + shadowOffset
                )

                // Realistic metallic reflection coloring gradient matching M3 Elegant Dark
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF), // Direct light specular point
                            Color(0xFFEADDFF), // Chrome/purple grey
                            Color(0xFFB19DFF), // Polished metal reflection
                            Color(0xFF313033)  // Deep boundary dark shadow
                        ),
                        center = ballCenter + Offset(-ballRadius * 0.3f - dx * 0.15f, -ballRadius * 0.3f - dy * 0.15f),
                        radius = ballRadius * 1.05f
                    ),
                    radius = ballRadius,
                    center = ballCenter,
                    alpha = state.ballAlpha
                )

                // Thin lavender-purple outer rim representing precision metal core
                drawCircle(
                    color = Color(0xFFD0BCFF).copy(alpha = 0.55f * state.ballAlpha),
                    radius = ballRadius,
                    center = ballCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = mapSize(0.2f)),
                    alpha = state.ballAlpha
                )
            }

            // 6. DRAW CONFETTI PARTICLES SPARKS
            for (particle in state.particles) {
                val pPos = mapCoord(particle.position.x, particle.position.y)
                val pSize = mapSize(particle.size / 10f)

                drawCircle(
                    color = Color(particle.color).copy(alpha = 1f - (particle.age / particle.maxAge)),
                    radius = pSize,
                    center = pPos
                )
                // Small core flare
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f * (1f - (particle.age / particle.maxAge))),
                    radius = pSize * 0.4f,
                    center = pPos
                )
            }
        }
    }
}
