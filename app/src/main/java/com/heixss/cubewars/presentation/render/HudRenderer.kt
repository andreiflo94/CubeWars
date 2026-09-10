package com.heixss.cubewars.presentation.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.EnemyWeakness
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.WavePhase
import com.heixss.cubewars.presentation.input.ControlButton
import com.heixss.cubewars.presentation.input.ControlZone
import com.heixss.cubewars.presentation.input.TouchControls
import kotlin.math.sin

/**
 * Draws the interface: health, score, the wave banner, the on-screen controls and the overlays.
 *
 * It runs on its own viewport pinned to the virtual screen, so the HUD never shakes with the camera
 * and the control buttons stay exactly where the player's thumbs left them.
 */
class HudRenderer(
    private val textures: GameTextures,
    private val batch: SpriteBatch,
) : Disposable {

    private val camera = OrthographicCamera()
    val viewport: Viewport =
        FitViewport(GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT, camera)

    private val font = BitmapFont().apply { setUseIntegerPositions(false) }
    private val layout = GlyphLayout()

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    fun render(state: GameState, pressed: Set<ControlButton>) {
        viewport.apply()
        batch.projectionMatrix = camera.combined
        batch.begin()

        drawStatusBar(state)
        drawWaveBanner(state)
        if (state.status == GameStatus.RUNNING) drawControls(pressed, state.input.aim)
        drawOverlay(state)

        batch.end()
    }

    // -------------------------------------------------------------------------------------------
    // Status bar
    // -------------------------------------------------------------------------------------------

    private fun drawStatusBar(state: GameState) {
        batch.setColor(Palette.hudPanel.r, Palette.hudPanel.g, Palette.hudPanel.b, 0.45f)
        batch.draw(textures.pixel, 0f, GameConfig.VIEWPORT_HEIGHT - 24f, GameConfig.VIEWPORT_WIDTH, 24f)
        batch.setColor(Color.WHITE)

        drawHealth(state)

        text("SCORE ${state.score}", 148f, GameConfig.VIEWPORT_HEIGHT - 8f, 0.75f, Palette.hudText)
        text("BEST ${maxOf(state.highScore, state.score)}", 148f, GameConfig.VIEWPORT_HEIGHT - 17f, 0.6f, Palette.hudDim)

        text("LVL ${state.levelNumber}", 236f, GameConfig.VIEWPORT_HEIGHT - 8f, 0.75f, Palette.hudText)
        text(state.level.name, 236f, GameConfig.VIEWPORT_HEIGHT - 17f, 0.55f, Palette.hudDim)

        val remaining = state.enemies.size + state.wave.remainingToSpawn
        text(
            "WAVE ${state.wave.numberInLevel}/${state.level.waveQuota}",
            355f,
            GameConfig.VIEWPORT_HEIGHT - 8f,
            0.75f,
            Palette.hudText,
        )
        // Once the gate is open the headcount is no longer the thing the player needs to know.
        val (status, statusColor) = when {
            state.wave.isLevelComplete -> "EXIT OPEN >>" to Palette.gateOpen
            remaining > 0 -> "$remaining LEFT" to Palette.hudDim
            else -> "CLEAR" to Palette.hudAccent
        }
        text(status, 355f, GameConfig.VIEWPORT_HEIGHT - 17f, 0.6f, statusColor)

        drawPauseButton()
        drawThreatReadout(state)
    }

    /**
     * Names the weak spot of the closest cube on the field.
     *
     * The marker drawn on a cube says *where* to shoot it; for the weaknesses that are about how the
     * shot was taken rather than where it landed there is nothing to point at, only a coloured pip.
     * This is the legend for that pip, and it only ever describes the one cube the player is most
     * likely to be shooting at — a list of eight would be a manual, not a HUD.
     */
    private fun drawThreatReadout(state: GameState) {
        val nearest = state.enemies
            .filterNot { it.isMaterialising }
            .minByOrNull { (it.center - state.player.bounds.center).length }
            ?: return

        val weakness = nearest.weakness
        if (weakness == EnemyWeakness.NONE) return
        text(
            "${nearest.archetype.name}  WEAK: ${weakness.label}",
            10f,
            GameConfig.VIEWPORT_HEIGHT - 31f,
            0.6f,
            Palette.weaknessColour(weakness),
        )
    }

    /** Health is a row of little player cubes — the same shape you are trying to keep alive. */
    private fun drawHealth(state: GameState) {
        val pipSize = 9f
        val gap = 4f
        val baseY = GameConfig.VIEWPORT_HEIGHT - 17f
        for (index in 0 until state.player.maxHealth) {
            val x = 10f + index * (pipSize + gap)
            val alive = index < state.player.health
            if (alive) {
                // The last point of health pulses, so a player in trouble knows it without looking.
                val critical = state.player.health == 1
                val pulse = if (critical) 0.65f + 0.35f * sin(state.elapsedTime * 9f) else 1f
                batch.setColor(pulse, pulse, pulse, 1f)
                batch.draw(textures.player, x, baseY, pipSize, pipSize)
            } else {
                batch.setColor(Palette.hudDim.r, Palette.hudDim.g, Palette.hudDim.b, 0.28f)
                batch.draw(textures.pixel, x, baseY, pipSize, pipSize)
            }
        }
        batch.setColor(Color.WHITE)
    }

    private fun drawPauseButton() {
        val zone = TouchControls.zoneFor(ControlButton.PAUSE)
        batch.setColor(Palette.controlTint.r, Palette.controlTint.g, Palette.controlTint.b, 0.75f)
        batch.draw(textures.iconPause, zone.centerX - 8f, zone.centerY - 8f, 16f, 16f)
        batch.setColor(Color.WHITE)
    }

    // -------------------------------------------------------------------------------------------
    // Wave banner
    // -------------------------------------------------------------------------------------------

    private fun drawWaveBanner(state: GameState) {
        if (state.status != GameStatus.RUNNING) return

        if (state.wave.isLevelComplete) {
            drawExitPrompt(state)
            return
        }
        if (state.wave.phase != WavePhase.INTERMISSION) return
        val timer = state.wave.phaseTimer
        if (timer <= 0f) return

        // The first breath of a new arena announces the arena; every other one announces the wave.
        val opening = state.wave.clearedInLevel == 0
        val duration = if (opening) GameConfig.LEVEL_INTRO_TIME else GameConfig.WAVE_INTERMISSION
        // Fade in over the first moments, hold, then fade out at the end.
        val alpha = (timer * 2.5f).coerceAtMost(1f) * ((duration - timer) * 4f).coerceIn(0f, 1f)
        val centerX = GameConfig.VIEWPORT_WIDTH / 2f
        val centerY = GameConfig.VIEWPORT_HEIGHT * 0.62f

        if (opening) {
            text("LEVEL ${state.levelNumber}", centerX, centerY, 1.5f, Palette.hudText.withAlpha(alpha), centered = true)
            text(state.level.name, centerX, centerY - 24f, 1f, Palette.gateOpen.withAlpha(alpha * 0.9f), centered = true)
        } else {
            text(
                "WAVE ${state.wave.numberInLevel}/${state.level.waveQuota}",
                centerX,
                centerY,
                1.8f,
                Palette.hudText.withAlpha(alpha),
                centered = true,
            )
            text("INCOMING", centerX, centerY - 22f, 0.8f, Palette.hudAccent.withAlpha(alpha * 0.9f), centered = true)
        }
    }

    /**
     * The nudge toward the gate.
     *
     * An arena this wide can hide its exit two screens away, and a player who has just won a fight
     * should never have to wonder what the game wants from them next.
     */
    private fun drawExitPrompt(state: GameState) {
        val centerX = GameConfig.VIEWPORT_WIDTH / 2f
        val pulse = 0.6f + 0.4f * sin(state.elapsedTime * 4.5f)
        text("ARENA CLEAR", centerX, 214f, 1.1f, Palette.hudText, centered = true)
        text(
            "HEAD RIGHT TO THE GATE >>",
            centerX,
            196f,
            0.8f,
            Palette.gateOpen.withAlpha(pulse),
            centered = true,
        )
    }

    // -------------------------------------------------------------------------------------------
    // Controls
    // -------------------------------------------------------------------------------------------

    private fun drawControls(pressed: Set<ControlButton>, aim: Vec2?) {
        for (zone in TouchControls.zones) {
            if (zone.button == ControlButton.PAUSE) continue
            val down = zone.button in pressed
            val diameter = zone.radius * 2f

            batch.setColor(1f, 1f, 1f, if (down) 0.95f else 0.55f)
            batch.draw(textures.controlPad, zone.centerX - zone.radius, zone.centerY - zone.radius, diameter, diameter)

            val icon = iconFor(zone.button)
            val iconSize = zone.radius * 1.15f
            batch.setColor(1f, 1f, 1f, if (down) 1f else 0.7f)
            batch.draw(icon, zone.centerX - iconSize / 2f, zone.centerY - iconSize / 2f, iconSize, iconSize)

            if (zone.button == ControlButton.FIRE && aim != null) drawAimKnob(zone, aim)
        }
        batch.setColor(Color.WHITE)
    }

    /**
     * The chevron riding the rim of the fire pad, pointing where the shots are going.
     *
     * Without it a free aim is invisible until a bullet has already left, which is exactly one shot
     * too late to correct.
     */
    private fun drawAimKnob(zone: ControlZone, aim: Vec2) {
        val size = zone.radius * 0.8f
        val x = zone.centerX + aim.x * zone.radius - size / 2f
        val y = zone.centerY + aim.y * zone.radius - size / 2f
        // The icon is drawn pointing up, so its angle is measured from straight up.
        val angle = MathUtils.atan2(aim.y, aim.x) * MathUtils.radiansToDegrees - 90f
        batch.setColor(1f, 1f, 1f, 0.95f)
        batch.draw(textures.iconAim, x, y, size / 2f, size / 2f, size, size, 1f, 1f, angle)
    }

    private fun iconFor(button: ControlButton): TextureRegion = when (button) {
        ControlButton.MOVE_LEFT -> textures.iconLeft
        ControlButton.MOVE_RIGHT -> textures.iconRight
        ControlButton.JUMP -> textures.iconJump
        ControlButton.FIRE -> textures.iconFire
        ControlButton.PAUSE -> textures.iconPause
    }

    // -------------------------------------------------------------------------------------------
    // Overlays
    // -------------------------------------------------------------------------------------------

    private fun drawOverlay(state: GameState) = when (state.status) {
        GameStatus.READY -> drawTitleCard(state)
        GameStatus.PAUSED -> drawPauseCard()
        GameStatus.GAME_OVER -> drawGameOverCard(state)
        GameStatus.RUNNING -> Unit
    }

    private fun drawTitleCard(state: GameState) {
        dim(0.72f)
        val centerX = GameConfig.VIEWPORT_WIDTH / 2f
        text("CUBE WARS", centerX, 214f, 2.4f, Palette.hudText, centered = true)
        text("cubes that shoot other cubes", centerX, 190f, 0.85f, Palette.hudAccent, centered = true)

        text("MOVE  left pad       JUMP  ^ button, twice to jump again", centerX, 162f, 0.7f, Palette.hudDim, centered = true)
        text("FIRE  hold right pad, slide to aim", centerX, 150f, 0.7f, Palette.hudDim, centered = true)
        text("shots fall as they fly — the dotted arc is where yours lands", centerX, 138f, 0.7f, Palette.hudDim, centered = true)
        text(
            "every cube has a weak spot. the marker on it is the weak spot",
            centerX,
            120f,
            0.7f,
            Palette.weakSpot,
            centered = true,
        )
        text(
            "clear the waves, then take the gate east",
            centerX,
            102f,
            0.7f,
            Palette.gateOpen,
            centered = true,
        )

        val pulse = 0.55f + 0.45f * sin(state.elapsedTime * 4f)
        text("TAP ANYWHERE TO START", centerX, 70f, 1.05f, Palette.hudText.withAlpha(pulse), centered = true)
        if (state.highScore > 0) {
            text("BEST ${state.highScore}", centerX, 46f, 0.75f, Palette.hudDim, centered = true)
        }
    }

    private fun drawPauseCard() {
        dim(0.6f)
        val centerX = GameConfig.VIEWPORT_WIDTH / 2f
        text("PAUSED", centerX, 160f, 2f, Palette.hudText, centered = true)
        text("tap anywhere to resume", centerX, 128f, 0.85f, Palette.hudDim, centered = true)
    }

    private fun drawGameOverCard(state: GameState) {
        dim(0.72f)
        val centerX = GameConfig.VIEWPORT_WIDTH / 2f
        text("GAME OVER", centerX, 196f, 2.2f, Palette.hudWarning, centered = true)
        text(
            "you fell in ${state.level.name} — level ${state.levelNumber}",
            centerX,
            168f,
            0.85f,
            Palette.hudDim,
            centered = true,
        )
        text("SCORE ${state.score}", centerX, 138f, 1.3f, Palette.hudText, centered = true)

        if (state.isNewHighScore) {
            text("NEW BEST!", centerX, 114f, 0.95f, Palette.hudAccent, centered = true)
        } else {
            text("BEST ${state.highScore}", centerX, 114f, 0.8f, Palette.hudDim, centered = true)
        }

        text(
            "${state.wave.totalKills} cubes destroyed in ${state.wave.displayNumber - 1} waves",
            centerX,
            90f,
            0.75f,
            Palette.hudDim,
            centered = true,
        )

        val pulse = 0.55f + 0.45f * sin(state.elapsedTime * 4f)
        text("TAP TO TRY AGAIN", centerX, 58f, 1.05f, Palette.hudText.withAlpha(pulse), centered = true)
    }

    private fun dim(alpha: Float) {
        batch.setColor(Palette.hudPanel.r, Palette.hudPanel.g, Palette.hudPanel.b, alpha)
        batch.draw(textures.pixel, 0f, 0f, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT)
        batch.setColor(Color.WHITE)
    }

    // -------------------------------------------------------------------------------------------

    private fun text(
        value: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Color,
        centered: Boolean = false,
    ) {
        font.data.setScale(scale)
        font.color = color
        if (centered) {
            layout.setText(font, value)
            font.draw(batch, layout, x - layout.width / 2f, y)
        } else {
            font.draw(batch, value, x, y)
        }
    }

    private fun Color.withAlpha(alpha: Float): Color = Color(r, g, b, alpha)

    override fun dispose() {
        font.dispose()
    }
}
