package com.example.ui.game

import androidx.compose.ui.geometry.Offset

data class LineSegment(
    val start: Offset,
    val end: Offset
)

data class Hole(
    val id: Int,
    val center: Offset,
    val radius: Float
)

data class Goal(
    val center: Offset,
    val radius: Float
)

data class Level(
    val index: Int,
    val name: String,
    val startPos: Offset,
    val walls: List<LineSegment>,
    val holes: List<Hole>,
    val goal: Goal,
    val goldTime: Float // Gold award target (seconds)
)

object LevelRegistry {
    val levels = listOf(
        Level(
            index = 1,
            name = "الهدوء والبداية السلسة",
            startPos = Offset(15f, 15f),
            walls = listOf(
                // Centered vertical wall blocking straight path to exit
                LineSegment(Offset(50f, 0f), Offset(50f, 65f)),
                // Horizontal barrier
                LineSegment(Offset(50f, 65f), Offset(80f, 65f))
            ),
            holes = listOf(
                Hole(id = 11, center = Offset(50f, 82f), radius = 5.5f),
                Hole(id = 12, center = Offset(25f, 60f), radius = 5.0f)
            ),
            goal = Goal(center = Offset(85f, 85f), radius = 6.0f),
            goldTime = 12f
        ),
        Level(
            index = 2,
            name = "الممرات والمنعطفات المتتالية",
            startPos = Offset(12f, 12f),
            walls = listOf(
                // Corridor structure
                LineSegment(Offset(0f, 35f), Offset(75f, 35f)),
                LineSegment(Offset(25f, 68f), Offset(100f, 68f))
            ),
            holes = listOf(
                Hole(id = 21, center = Offset(45f, 18f), radius = 5.2f),
                Hole(id = 22, center = Offset(50f, 52f), radius = 5.2f),
                Hole(id = 23, center = Offset(88f, 52f), radius = 4.8f),
                Hole(id = 24, center = Offset(12f, 85f), radius = 5.5f)
            ),
            goal = Goal(center = Offset(88f, 15f), radius = 6.0f),
            goldTime = 18f
        ),
        Level(
            index = 3,
            name = "حقل ألغام الجاذبية",
            startPos = Offset(10f, 85f),
            walls = listOf(
                // Asymmetrical grid dividers
                LineSegment(Offset(30f, 0f), Offset(30f, 75f)),
                LineSegment(Offset(65f, 25f), Offset(65f, 100f))
            ),
            holes = listOf(
                Hole(id = 31, center = Offset(12f, 35f), radius = 5.0f),
                Hole(id = 32, center = Offset(48f, 18f), radius = 4.8f),
                Hole(id = 33, center = Offset(48f, 55f), radius = 4.8f),
                LineSegment(Offset(30f, 75f), Offset(50f, 75f)), // extra wall piece
                Hole(id = 34, center = Offset(48f, 88f), radius = 5.0f),
                Hole(id = 35, center = Offset(82f, 30f), radius = 5.2f),
                Hole(id = 36, center = Offset(82f, 72f), radius = 5.2f)
            ).filterIsInstance<Hole>(), // Keep cast clean
            goal = Goal(center = Offset(85f, 15f), radius = 6.0f),
            goldTime = 25f
        ),
        Level(
            index = 4,
            name = "ملاذ الحلزون الأخير",
            startPos = Offset(12f, 12f),
            walls = listOf(
                // Spiral chamber
                LineSegment(Offset(0f, 30f), Offset(70f, 30f)),
                LineSegment(Offset(70f, 30f), Offset(70f, 80f)),
                LineSegment(Offset(30f, 80f), Offset(70f, 80f)),
                LineSegment(Offset(30f, 52f), Offset(30f, 80f)),
                LineSegment(Offset(30f, 52f), Offset(52f, 52f))
            ),
            holes = listOf(
                Hole(id = 41, center = Offset(12f, 55f), radius = 4.8f),
                Hole(id = 42, center = Offset(48f, 15f), radius = 4.8f),
                Hole(id = 43, center = Offset(85f, 52f), radius = 4.8f),
                Hole(id = 44, center = Offset(50f, 66f), radius = 4.5f),
                Hole(id = 45, center = Offset(15f, 90f), radius = 5.2f)
            ),
            goal = Goal(center = Offset(50f, 40f), radius = 5.5f),
            goldTime = 30f
        )
    )
}
