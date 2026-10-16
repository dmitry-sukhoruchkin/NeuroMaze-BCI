package com.example

import com.example.eeg.CameraViewMode
import com.example.eeg.EegConstants
import com.example.eeg.WorkingMemoryEngine
import com.example.maze.AvatarState
import com.example.maze.TopoMaze
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class NeuroMazeUnitTest {

    @Test
    fun testMazeGeneration() {
        val maze = TopoMaze(dim = 13)
        assertEquals(13, maze.dim)
        // Start cell (1, 1) should be open pathway
        assertEquals(0, maze.grid[1][1])
        // Exit cell should be marked 2
        val (ex, ey) = maze.exitPos
        assertEquals(2, maze.grid[ey][ex])
        assertTrue("Exit distance should be at least 10", maze.optimalDistance >= 10)
    }

    @Test
    fun testContinuous360Gradient() {
        val maze = TopoMaze(dim = 13)
        val (dx, dy, path) = maze.getContinuous360Gradient(1.5f, 1.5f, 2.2f)
        assertTrue("Path should contain waypoints", path.isNotEmpty())
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        assertEquals(1.0f, len, 0.05f)
    }

    @Test
    fun testAvatarKinematicsAndCollision() {
        val maze = TopoMaze(dim = 13)
        val avatar = AvatarState()
        avatar.reset(1.5f, 1.5f)

        // Wall at (0, 1): moving left in SCREEN_WORLD should collide and keep avatar within bounds
        for (i in 0 until 10) {
            avatar.updateMotion(
                intentX = -1f,
                intentY = 0f,
                intentTorque = 0f,
                persistence = 0.5f,
                maze = maze,
                cameraMode = CameraViewMode.SCREEN_WORLD
            )
        }
        assertTrue("Avatar x should not be inside outer wall (x >= 1.0)", avatar.x >= 1.0f)

        // Mode 3 (TANK_PRO): Avatar rotated 90 deg right (PI/2), moving forward (-Y) should move right (+X)
        avatar.reset(1.5f, 1.5f)
        avatar.angle = (Math.PI / 2.0).toFloat()
        avatar.updateMotion(
            intentX = 0f,
            intentY = -1f, // Forward intent
            intentTorque = 0f,
            persistence = 0.5f,
            maze = maze,
            cameraMode = CameraViewMode.TANK_PRO
        )
        // With angle=PI/2, forward moves +X (right)
        assertTrue("Avatar moved right along facing angle in TANK_PRO", avatar.x >= 1.5f)

        // Mode 4 (GENSHIN_ORBIT): Camera at 0, moving forward moves up (-Y) and avatar faces up
        avatar.reset(1.5f, 1.5f)
        avatar.cameraYaw = 0f
        avatar.updateMotion(
            intentX = 0f,
            intentY = -1f,
            intentTorque = 0f,
            persistence = 0.5f,
            maze = maze,
            cameraMode = CameraViewMode.GENSHIN_ORBIT
        )
        assertTrue("Avatar moved up along cameraYaw in GENSHIN_ORBIT", avatar.y <= 1.5f)
    }

    @Test
    fun testWorkingMemoryEngineLismanJensenParity() {
        val wm = WorkingMemoryEngine()

        // 1. Target moving East/Right (+1, 0)
        for (step in 0 until 6) {
            val chunk = wm.generateSyntheticChunk(targetX = 1.0f, targetY = 0.0f, gain = 16)
            assertEquals(10, chunk.size)
            assertEquals(16, chunk[0].size)
            for (sample in chunk) {
                wm.ingestSample(sample)
            }
        }

        val resRight = wm.processWorkingMemory(idealX = 1.0f, idealY = 0.0f, sps = 250f)
        assertNotNull(resRight)

        // Both real EEG intent and Monty predicted vector MUST point towards +X
        assertTrue("Working memory Real X should be positive for Right target", resRight.realX > 0f)
        assertTrue("Monty X should be positive for Right target", resRight.montyX > 0f)
        assertTrue("PAC anomaly loss should be bounded", resRight.anomalyLoss in 0.005f..0.080f)

        // 2. Downbeat Slot 0 Anchor check: Slot 0 PAC is zero-referenced
        assertEquals(32, resRight.pac32x120.size)
        assertEquals(120, resRight.pac32x120[0].size)
    }
}
