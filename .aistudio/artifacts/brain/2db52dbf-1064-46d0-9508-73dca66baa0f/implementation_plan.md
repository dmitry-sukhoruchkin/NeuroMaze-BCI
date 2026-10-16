# Seamless Maze Progression & Sagittal BCI Steering Engine

Non-blocking maze navigation with instant auto-advancement, restored sagittal curvature steering for ego-centric view yaw rotation from midline electrodes (FCz), and on-demand live channel microvolt telemetry.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following core user decisions were confirmed during clarification and are integrated as the baseline for this implementation:
>
> - **Seamless Progression (No Blocking Dialog)**: The intrusive "Next Maze / Level Complete" popup modal is eliminated. When the avatar reaches the goal, it seamlessly advances into the next maze layout with an unobtrusive, celebratory floating toast (`+1 Maze Solved!`), allowing continuous, undisturbed gameplay.
> - **Sagittal Curvature Steering in Ego View**: Restores independent sagittal curvature ($r_x$ torque / trajectory bending) derived from midline electrode phase differences (FCz / sagittal axis), driving continuous, smooth camera and heading yaw rotation in `EGO_ROTATING` view while the cyan intention vector drives translation.
> - **On-Demand µV Telemetry**: Live channel microvolt amplitudes ($\mu\text{V}$) and per-channel saturation statuses are populated on-demand when the telemetry sheet is active, eliminating unnecessary CPU/memory churn during gameplay while providing accurate debug metrics.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Uninterrupted Flow**: Removes the disruptive modal dialog on maze completion. Reaching the maze exit triggers an instantaneous, smooth transition to the next procedural maze with a brief glowing HUD banner, preserving momentum.
  2. **True BCI Steering in Ego View**: Reconstructs the 2D spatio-temporal trajectory curvature across the theta cycle ($32$ slots) using both lateral (left-right) and sagittal (anterior-posterior / FCz) electrode pairs. The cross-product between the principal intention chord and intermediate theta trajectory segments yields non-zero sagittal curvature ($r_x$ / torque), smoothly steering avatar heading and yaw in ego mode.
  3. **Live Microvolt Telemetry**: Reads the latest 16-channel $\mu\text{V}$ vector from incoming BLE packets or synthetic generator whenever the user opens the telemetry/debug tab, restoring live amplitude bars and saturation alerts.
- **Target Persona**: Neuro-gamers, BCI researchers, and users controlling gameplay through real FreeEEG16 hardware and synthetic working-memory simulations.
- **Key Value**: Fluid, immersion-preserving maze solving with authentic 2D translation + rotational steering fidelity.

---

## 2. User Experience & Visual Design

- **Goal Reached Interaction**:
  - Avatar touches the exit tile $\to$ A subtle green/cyan pulsing beacon flashes at the exit tile.
  - A compact top-screen floating toast appears: `✨ Maze #N Complete! Loading next challenge...` (auto-fades in 1.8s).
  - The avatar resets to the start position of the freshly generated maze with zero modal obstruction or button clicks required.
- **Ego-Centric Rotating View**:
  - The camera aligns smoothly with the avatar's heading angle.
  - As the user curves their intention (sagittal phase shift from midline FCz), the avatar continuously turns (yaw rate), while the forward velocity follows the decoded intention vector.
  - Both Cyan (Working Memory Decoded Intent) and Pink (Predictive Monty) vectors remain clearly visible on the HUD radar and world canvas.
- **Telemetry Sheet Inspection**:
  - Opening the sheet displays live signal amplitudes (e.g., $+24.3\ \mu\text{V}$) with active cyan level meters for all 16 channels, highlighting red/pink if ADS131M08 rails are saturated.
  - Closing the sheet stops the per-frame channel array allocation, keeping the game loop lightweight.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Elimination of `LevelWinDialog` in favor of HUD Badge / Toast**:
  - *Chosen Approach*: Replace the fullscreen modal dialog with an inline HUD notification and immediate maze re-generation.
  - *Why*: Directly solves the user's explicit complaint that the popup interrupted their flow while solving mazes.
- **Decision 2: Re-enabling True Non-Zero Sagittal Trajectory Curvature in `WorkingMemoryEngine`**:
  - *Chosen Approach*: When constructing the $32$-slot theta-gamma trajectory, inject the phase-dependent sagittal dipole component (using FCz/midline pairs) into `traj2D[k]`. The perpendicular sagitta cross-product $\sum (endX \cdot py_k - endY \cdot px_k)$ will yield authentic, dynamic turning curvature ($r_x$).
  - *Why*: Without this perpendicular component, the trajectory was mathematically collinear with the chord, forcing sagitta to zero and disabling ego turning.
- **Decision 3: On-Demand Telemetry Sampling**:
  - *Chosen Approach*: Populate `channelMicrovolts` in `NeuroTelemetry` only when `_uiState.value.showTelemetrySheet` is true or during HUD expansion.
  - *Why*: Prevents creating 16-element float arrays on every 60 FPS frame while delivering full precision when the user inspects the signal.

---

## 4. Technical Architecture & Component Structure

```
┌──────────────────────────────────────────────────────────────┐
│                    NeuroMazeViewModel                        │
│                                                              │
│  ┌───────────────────────┐        ┌────────────────────────┐ │
│  │   FreeEegBleManager   │        │  WorkingMemoryEngine   │ │
│  │  (Latest µV & Sat)    │        │ (Theta-Gamma PAC,      │ │
│  └──────────┬────────────┘        │  Sagittal FCz Torque,  │ │
│             │                     │  Monty NLMS)           │ │
│             ▼                     └───────────┬────────────┘ │
│      Game Loop Tick (60 FPS)                  │              │
│      - Process Working Memory ────────────────┘              │
│      - Compute driveX, driveY, driveTorque                   │
│      - Update AvatarState (Continuous Yaw in Ego Mode)       │
│      - Check Exit Collision ───► Seamless Advance (No Modal) │
│      - If Telemetry Open ──────► Attach live µV array        │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                         Compose UI                           │
│                                                              │
│  ┌─────────────────────────┐    ┌──────────────────────────┐ │
│  │       MazeCanvas        │    │         NeuroHud         │ │
│  │ (Maze Grid, Avatar,     │    │ (Radar, Cyan/Pink Vector,│ │
│  │  Glowing Intent Arrows, │    │  Gain Selector, Toast)   │ │
│  │  Trail, Beacon)         │    └──────────────────────────┘ │
│  └─────────────────────────┘    ┌──────────────────────────┐ │
│                                 │    EegTelemetrySheet     │ │
│                                 │ (Live 16-ch µV Bars,     │ │
│                                 │  ADS131M08 Saturation)   │ │
│                                 └──────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Components to Update

1. **`NeuroMazeViewModel.kt`**:
   - In `handleMazeWon()`: Remove `showWinDialog = true`. Instead, increment solved count, update stats, generate the new maze immediately, and trigger a lightweight transient `winToastMessage` in `GameUiState`.
   - In telemetry assembly: When `showTelemetrySheet` is active, retrieve the latest 16-channel microvolt buffer from `wmEngine.getLatestMicrovolts()` or `bleManager.latestPacket`.
   - Pass torque (`wmResult.realTq`) into `driveTorque` for `BCI_DIRECT` and `BCI_AUTOPILOT`.

2. **`WorkingMemoryEngine.kt`**:
   - Update 32-slot trajectory generation: Incorporate the sagittal midline phase modulation (from FCz, Fz, Cz channels) to create curved trajectory arcs across theta slots.
   - Calculate non-zero `rx` (sagitta curvature) and output it in `realTq` so the avatar smoothly rotates in ego view.
   - Expose `latestMicrovolts` buffer for telemetry inspection.

3. **`MainActivity.kt`**:
   - Remove `if (uiState.showWinDialog) { LevelWinDialog(...) }`.
   - Add a sleek floating `MazeToast` / notification banner at the top of the maze screen when a level is passed.

4. **`AvatarState.kt`**:
   - Ensure `isRotatingView` applies `ctrlTorque` with smooth dampening, allowing continuous yaw orientation in ego view.
