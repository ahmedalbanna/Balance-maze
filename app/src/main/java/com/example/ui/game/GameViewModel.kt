package com.example.ui.game

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameRecord
import com.example.data.GameRepository
import com.example.sensor.TiltSensorTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class GamePlayState {
    INTRO,
    PLAYING,
    FALLING,   // Active animation falling in progress
    ESCAPING,  // Active portal escape in progress
    LEVEL_FAILED,
    LEVEL_SUCCESS,
    GAME_COMPLETED
}

data class GameParticle(
    val position: Offset,
    val velocity: Offset,
    val color: Long,
    val size: Float,
    var age: Float,
    val maxAge: Float
)

data class UiState(
    val playState: GamePlayState = GamePlayState.INTRO,
    val currentLevelIndex: Int = 1,
    val ballX: Float = 15f,
    val ballY: Float = 15f,
    val ballRadius: Float = 3.0f,
    val ballScale: Float = 1.0f,
    val ballAlpha: Float = 1.0f,
    val timeElapsed: Float = 0.0f,
    val isGoldEarned: Boolean = false,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val particles: List<GameParticle> = emptyList(),
    val calibrationX: Float = 0f,
    val calibrationY: Float = 0f
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    private val sensorTracker = TiltSensorTracker(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Expose all records reactively from Room to show scoreboard
    val records: StateFlow<List<GameRecord>>

    private var physicsJob: Job? = null
    private var sensorCollectionJob: Job? = null

    // Game physics configuration
    private val gravityMultiplier = 165f // Speed response
    private val friction = 0.94f         // Dynamic damping representing heavy material
    private val wallRestitution = 0.28f  // Bounciness bounce-off force
    private val targetFpsMs = 16L        // Aiming for smooth ~60fps ticks

    init {
        val database = AppDatabase.getDatabase(application)
        repository = GameRepository(database.gameRecordDao())
        records = repository.allRecords
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Listen to phone tilting sensors
        startSensorMonitoring()
    }

    private fun startSensorMonitoring() {
        sensorTracker.startTracking()
        sensorCollectionJob?.cancel()
        sensorCollectionJob = viewModelScope.launch(Dispatchers.Default) {
            // Collect the fused sensor roll and pitch values and store them in the state flow
            launch {
                sensorTracker.roll.collect { roll ->
                    _uiState.value = _uiState.value.copy(tiltX = roll)
                }
            }
            launch {
                sensorTracker.pitch.collect { pitch ->
                    _uiState.value = _uiState.value.copy(tiltY = pitch)
                }
            }
        }
    }

    fun calibrateSensors() {
        val currentTiltX = _uiState.value.tiltX
        val currentTiltY = _uiState.value.tiltY
        _uiState.value = _uiState.value.copy(
            calibrationX = currentTiltX,
            calibrationY = currentTiltY
        )
    }

    fun selectLevelDirectly(levelIndex: Int) {
        stopPhysicsLoop()
        val requestedIndex = levelIndex.coerceIn(1, LevelRegistry.levels.size)
        loadLevel(requestedIndex)
    }

    private fun loadLevel(levelIndex: Int) {
        val level = LevelRegistry.levels.firstOrNull { it.index == levelIndex } ?: LevelRegistry.levels.first()
        _uiState.value = _uiState.value.copy(
            playState = GamePlayState.PLAYING,
            currentLevelIndex = level.index,
            ballX = level.startPos.x,
            ballY = level.startPos.y,
            ballScale = 1.0f,
            ballAlpha = 1.0f,
            timeElapsed = 0.0f,
            isGoldEarned = false,
            particles = emptyList()
        )
        startPhysicsLoop()
    }

    fun startIntroToGame() {
        // First calibrate automatically based on current position
        calibrateSensors()
        loadLevel(1)
    }

    fun restartCurrentLevel() {
        loadLevel(_uiState.value.currentLevelIndex)
    }

    fun proceedToNextLevel() {
        val nextIndex = _uiState.value.currentLevelIndex + 1
        if (nextIndex <= LevelRegistry.levels.size) {
            loadLevel(nextIndex)
        } else {
            _uiState.value = _uiState.value.copy(
                playState = GamePlayState.GAME_COMPLETED
            )
            stopPhysicsLoop()
        }
    }

    fun exitToIntro() {
        stopPhysicsLoop()
        _uiState.value = _uiState.value.copy(
            playState = GamePlayState.INTRO
        )
    }

    private fun startPhysicsLoop() {
        stopPhysicsLoop()
        physicsJob = viewModelScope.launch(Dispatchers.Default) {
            var vx = 0f
            var vy = 0f
            val dt = targetFpsMs / 1000f

            while (true) {
                val state = _uiState.value
                when (state.playState) {
                    GamePlayState.PLAYING -> {
                        // 1. Process Sensor Tilt with Calibration Offset applied
                        val tiltX = (state.tiltX - state.calibrationX).coerceIn(-1f, 1f)
                        val tiltY = (state.tiltY - state.calibrationY).coerceIn(-1f, 1f)

                        // Accelerate using calibrated gravity indices
                        vx += tiltX * gravityMultiplier * dt
                        vy += -tiltY * gravityMultiplier * dt // Screen Y is inverted relative to phone pitch

                        // Apply dynamic friction damping
                        vx *= friction
                        vy *= friction

                        // Move ball in normalized space
                        var nextBX = state.ballX + vx * dt
                        var nextBY = state.ballY + vy * dt

                        val r = state.ballRadius
                        val level = LevelRegistry.levels.first { it.index == state.currentLevelIndex }

                        // 2. Bound checks against board walls (0..100)
                        if (nextBX < r) {
                            nextBX = r
                            vx = -vx * wallRestitution
                        } else if (nextBX > 100f - r) {
                            nextBX = 100f - r
                            vx = -vx * wallRestitution
                        }

                        if (nextBY < r) {
                            nextBY = r
                            vy = -vy * wallRestitution
                        } else if (nextBY > 100f - r) {
                            nextBY = 100f - r
                            vy = -vy * wallRestitution
                        }

                        // 3. Coordinate Wall Slidings
                        for (wall in level.walls) {
                            // Find closest point to wall segment
                            val cp = getClosestPointOnSegment(Offset(nextBX, nextBY), wall.start, wall.end)
                            val distVector = Offset(nextBX, nextBY) - cp
                            val dist = distVector.getDistance()

                            if (dist < r) {
                                // Push ball center along the wall normal
                                val normalVector = if (dist > 0.01f) {
                                    distVector / dist
                                } else {
                                    // Fallback arbitrary correct normal
                                    Offset(0f, -1f)
                                }
                                val pushAmount = r - dist
                                nextBX += normalVector.x * pushAmount
                                nextBY += normalVector.y * pushAmount

                                // Bounce projection
                                val normVelocity = vx * normalVector.x + vy * normalVector.y
                                if (normVelocity < 0) {
                                    // Deduct penetration velocity and add subtle restitution
                                    vx -= normVelocity * normalVector.x * (1f + wallRestitution)
                                    vy -= normVelocity * normalVector.y * (1f + wallRestitution)
                                }
                            }
                        }

                        // 4. Trap holes checks
                        var enteredHoleId: Int? = null
                        for (hole in level.holes) {
                            val distToHole = (Offset(nextBX, nextBY) - hole.center).getDistance()
                            // Center has to be mostly over the hole to trigger catastrophic draw down
                            if (distToHole <= (hole.radius - r * 0.4f)) {
                                enteredHoleId = hole.id
                                break
                            }
                        }

                        // 5. Goal portal checks
                        val distToGoal = (Offset(nextBX, nextBY) - level.goal.center).getDistance()
                        val reachedGoal = distToGoal <= (level.goal.radius * 0.85f)

                        // Update State & Particles
                        val updatedParticles = updateParticles(state.particles, dt)

                        if (enteredHoleId != null) {
                            // Fall animation trigger
                            _uiState.value = state.copy(
                                ballX = nextBX,
                                ballY = nextBY,
                                playState = GamePlayState.FALLING,
                                particles = updatedParticles
                            )
                            triggerFailingSequence(vx, vy, enteredHoleId)
                            break
                        } else if (reachedGoal) {
                            // Escape animation trigger
                            _uiState.value = state.copy(
                                ballX = nextBX,
                                ballY = nextBY,
                                playState = GamePlayState.ESCAPING,
                                particles = updatedParticles
                            )
                            triggerSuccessSequence(level)
                            break
                        } else {
                            // Continuous game update
                            val newTime = state.timeElapsed + dt
                            _uiState.value = state.copy(
                                ballX = nextBX,
                                ballY = nextBY,
                                timeElapsed = newTime,
                                particles = updatedParticles
                            )
                        }
                    }
                    else -> {
                        // Still update particles even during victory overlays
                        val updatedParticles = updateParticles(state.particles, dt)
                        _uiState.value = state.copy(particles = updatedParticles)
                    }
                }
                delay(targetFpsMs)
            }
        }
    }

    private fun stopPhysicsLoop() {
        physicsJob?.cancel()
        physicsJob = null
    }

    private fun updateParticles(particles: List<GameParticle>, dt: Float): List<GameParticle> {
        return particles.mapNotNull { p ->
            p.age += dt
            if (p.age > p.maxAge) null
            else {
                p.copy(
                    position = p.position + p.velocity * dt,
                    age = p.age
                )
            }
        }
    }

    private fun generateGoalSparkles(goalPos: Offset, count: Int): List<GameParticle> {
        return List(count) {
            val angle = (Math.random() * 2 * Math.PI).toFloat()
            val speed = (10f + Math.random() * 40f).toFloat() // particles speed
            GameParticle(
                position = goalPos,
                velocity = Offset(cos(angle) * speed, sin(angle) * speed),
                color = if (Math.random() > 0.5) 0xFF4CAF50 else 0xFF81C784, // Glowing emeralds
                size = (4f + Math.random() * 6f).toFloat(),
                age = 0f,
                maxAge = (0.5f + Math.random() * 0.8f).toFloat()
            )
        }
    }

    private fun generateFailureSmoke(holePos: Offset, count: Int): List<GameParticle> {
        return List(count) {
            val angle = (Math.random() * 2 * Math.PI).toFloat()
            val speed = (5f + Math.random() * 15f).toFloat()
            GameParticle(
                position = holePos,
                velocity = Offset(cos(angle) * speed, sin(angle) * speed),
                color = 0xFFEF5350, // Crimson red smoke
                size = (6f + Math.random() * 8f).toFloat(),
                age = 0f,
                maxAge = (0.4f + Math.random() * 0.5f).toFloat()
            )
        }
    }

    private suspend fun triggerFailingSequence(vx: Float, vy: Float, holeId: Int) {
        val stateAtFailure = _uiState.value
        val level = LevelRegistry.levels.first { it.index == stateAtFailure.currentLevelIndex }
        val hole = level.holes.first { it.id == holeId }

        val steps = 25
        val duration = 400L
        val delayPerStep = duration / steps

        var currentX = stateAtFailure.ballX
        var currentY = stateAtFailure.ballY

        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            // Pull gradually towards center of hole
            currentX = currentX + (hole.center.x - currentX) * 0.25f
            currentY = currentY + (hole.center.y - currentY) * 0.25f

            // Particle sparks during vortex suction
            val smoke = generateFailureSmoke(hole.center, if (i % 5 == 0) 4 else 0)

            _uiState.value = _uiState.value.copy(
                ballX = currentX,
                ballY = currentY,
                ballScale = 1.0f - (progress * 0.95f), // shrinking inside trap
                ballAlpha = 1.0f - progress,
                particles = (_uiState.value.particles + smoke).take(120)
            )
            delay(delayPerStep)
        }

        // Save failed record in background database as attempt log
        val elapsed = _uiState.value.timeElapsed
        val record = GameRecord(
            levelIndex = level.index,
            timeSpentSec = elapsed,
            isWin = false
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRecord(record)
        }

        _uiState.value = _uiState.value.copy(
            playState = GamePlayState.LEVEL_FAILED,
            ballAlpha = 0f
        )
    }

    private suspend fun triggerSuccessSequence(level: Level) {
        val stateAtSucc = _uiState.value
        val steps = 30
        val duration = 500L
        val delayPerStep = duration / steps

        var currentX = stateAtSucc.ballX
        var currentY = stateAtSucc.ballY

        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            // Magnetic force suction to the exact center of goal
            currentX = currentX + (level.goal.center.x - currentX) * 0.22f
            currentY = currentY + (level.goal.center.y - currentY) * 0.22f

            // Explode vibrant emerald target fireworks!
            val fireworks = generateGoalSparkles(level.goal.center, if (i % 3 == 0) 8 else 0)

            _uiState.value = _uiState.value.copy(
                ballX = currentX,
                ballY = currentY,
                ballScale = 1.0f - (progress * 0.85f),
                ballAlpha = 1.0f - progress,
                particles = (_uiState.value.particles + fireworks).take(180)
            )
            delay(delayPerStep)
        }

        // Success validation values
        val elapsed = _uiState.value.timeElapsed
        val earnedGold = elapsed <= level.goldTime

        // Persistent save to Room DB (IO Dispatcher)
        val record = GameRecord(
            levelIndex = level.index,
            timeSpentSec = elapsed,
            isWin = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRecord(record)
        }

        _uiState.value = _uiState.value.copy(
            playState = GamePlayState.LEVEL_SUCCESS,
            isGoldEarned = earnedGold,
            ballAlpha = 0f
        )
    }

    private fun getClosestPointOnSegment(p: Offset, s1: Offset, s2: Offset): Offset {
        val segmentVector = s2 - s1
        val pointVector = p - s1
        val segmentLengthSq = segmentVector.x * segmentVector.x + segmentVector.y * segmentVector.y
        if (segmentLengthSq < 0.001f) return s1

        var t = (pointVector.x * segmentVector.x + pointVector.y * segmentVector.y) / segmentLengthSq
        t = t.coerceIn(0f, 1f)

        return Offset(s1.x + t * segmentVector.x, s1.y + t * segmentVector.y)
    }

    override fun onCleared() {
        super.onCleared()
        stopPhysicsLoop()
        sensorTracker.stopTracking()
        sensorCollectionJob?.cancel()
    }
}
