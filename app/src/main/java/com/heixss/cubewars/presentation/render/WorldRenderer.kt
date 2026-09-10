package com.heixss.cubewars.presentation.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.Aabb
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.EnemyWeakness
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.model.ParticleTint
import com.heixss.cubewars.domain.model.PlatformBlock
import com.heixss.cubewars.domain.model.PlayerCube
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.physics.Ballistics
import com.heixss.cubewars.domain.physics.WorldPhysics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the world from a [GameState] and nothing else.
 *
 * The renderer holds no gameplay state of its own — no positions, no timers, no interpolation
 * buffers. Give it the same state twice and it draws the same frame twice. Everything that looks
 * like animation (squash, hit flashes, the spawn fade, screen shake) is read out of the state,
 * which is why the picture can never drift out of sync with the simulation.
 */
class WorldRenderer(
    private val textures: GameTextures,
    private val batch: SpriteBatch,
) {

    private val camera = OrthographicCamera()
    val viewport: Viewport =
        FitViewport(GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT, camera)

    private val tint = Color()

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, false)
    }

    fun render(state: GameState) {
        val shakeOffsetX = sin(state.camera.shakeSeed * 41.7f) * state.camera.shake
        val shakeOffsetY = cos(state.camera.shakeSeed * 57.3f) * state.camera.shake
        val centerX = state.camera.center.x + shakeOffsetX
        val centerY = state.camera.center.y + shakeOffsetY

        camera.position.set(centerX, centerY, 0f)
        camera.update()
        viewport.apply()
        batch.projectionMatrix = camera.combined

        val left = centerX - GameConfig.VIEWPORT_WIDTH / 2f
        val bottom = centerY - GameConfig.VIEWPORT_HEIGHT / 2f

        // The arena's art set is picked once per frame; nothing below asks again which level it is
        // drawing, it just draws what it was handed.
        val art = textures.theme(state.level.theme)

        batch.begin()
        drawBackdrop(art, left, bottom, centerX)
        drawLevel(art, state, left)
        drawExit(state)
        drawParticles(state.particles, behindEntities = true)
        state.enemies.forEach { drawEnemy(it, state.elapsedTime) }
        if (state.player.isAlive) {
            drawPlayer(state.player, state.elapsedTime)
            drawAimGuide(state)
        }
        drawProjectiles(state.projectiles)
        drawParticles(state.particles, behindEntities = false)
        batch.end()
    }

    // -------------------------------------------------------------------------------------------
    // Backdrop
    // -------------------------------------------------------------------------------------------

    private fun drawBackdrop(art: ThemeTextures, left: Float, bottom: Float, centerX: Float) {
        batch.setColor(Color.WHITE)
        batch.draw(
            art.sky,
            left,
            bottom,
            GameConfig.VIEWPORT_WIDTH,
            GameConfig.VIEWPORT_HEIGHT,
        )

        // Three layers at different parallax rates. The further back a layer is, the less it moves,
        // which is the whole of the depth illusion.
        drawParallaxStrip(art.stars, left, bottom, GameConfig.VIEWPORT_HEIGHT, 256f, centerX, 0.88f)
        drawParallaxStrip(art.hillsFar, left, bottom + 22f, 128f, 256f, centerX, 0.62f)
        drawParallaxStrip(art.hillsNear, left, bottom + 6f, 128f, 256f, centerX, 0.38f)
    }

    /**
     * Tiles [region] horizontally across the view, shifted by [factor] of the camera's travel.
     *
     * The tile index is derived from the camera rather than accumulated, so the layer is a pure
     * function of the camera position and cannot drift over a long session.
     */
    private fun drawParallaxStrip(
        region: TextureRegion,
        left: Float,
        y: Float,
        height: Float,
        tileWidth: Float,
        centerX: Float,
        factor: Float,
    ) {
        val shift = centerX * factor
        var index = MathUtils.floor((left + shift) / tileWidth) - 1
        val rightEdge = left + GameConfig.VIEWPORT_WIDTH
        while (true) {
            val x = index * tileWidth - shift
            if (x > rightEdge) break
            batch.draw(region, x, y, tileWidth, height)
            index++
        }
    }

    // -------------------------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------------------------

    private fun drawLevel(art: ThemeTextures, state: GameState, left: Float) {
        val right = left + GameConfig.VIEWPORT_WIDTH
        batch.setColor(Color.WHITE)
        for (block in state.level.blocks) {
            // Skip the invisible containment walls and anything off-screen.
            if (block.bounds.right <= 0f || block.bounds.left >= state.level.width) continue
            if (block.bounds.right < left || block.bounds.left > right) continue
            when (block.kind) {
                BlockKind.SOLID -> drawSolidBlock(art, block)
                BlockKind.ONE_WAY -> drawLedge(art, block)
            }
        }
    }

    private fun drawSolidBlock(art: ThemeTextures, block: PlatformBlock) {
        val bounds = block.bounds
        val tile = GameConfig.TILE
        var y = bounds.bottom
        while (y < bounds.top - 0.01f) {
            val rowHeight = min(tile, bounds.top - y)
            // Only the topmost row gets the lit cap, so a stack of tiles reads as a solid mass with
            // a walkable surface.
            val isSurface = y + rowHeight >= bounds.top - 0.01f
            val region = if (isSurface) art.groundCap else art.groundBody
            var x = bounds.left
            while (x < bounds.right - 0.01f) {
                val columnWidth = min(tile, bounds.right - x)
                drawClipped(region, x, y, columnWidth, rowHeight, tile, tile, fromTop = isSurface)
                x += tile
            }
            y += tile
        }
    }

    private fun drawLedge(art: ThemeTextures, block: PlatformBlock) {
        val bounds = block.bounds
        val tile = GameConfig.TILE
        var x = bounds.left
        while (x < bounds.right - 0.01f) {
            val columnWidth = min(tile, bounds.right - x)
            drawClipped(art.ledge, x, bounds.bottom, columnWidth, bounds.height, tile, 8f, fromTop = true)
            x += tile
        }
    }

    /**
     * The gate at the far end of the arena: shut and dark while there are still waves to fight,
     * lit and breathing once the level is cleared.
     *
     * The opening is drawn from the same state flag the rule reads, so what the player sees and what
     * the simulation will let them do can never disagree.
     */
    private fun drawExit(state: GameState) {
        val exit = state.level.exit
        val open = state.wave.isLevelComplete
        val inset = GATE_POST_WIDTH
        val openingX = exit.left + inset
        val openingY = exit.bottom + GATE_SILL_HEIGHT
        val openingWidth = exit.width - inset * 2f
        val openingHeight = exit.height - GATE_LINTEL_HEIGHT - GATE_SILL_HEIGHT

        if (open) {
            // A slow pulse, drawn additively so the gateway looks like light rather than paint.
            val pulse = 0.55f + 0.45f * sin(state.elapsedTime * 3.4f)
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            tint.set(Palette.gateOpen.r, Palette.gateOpen.g, Palette.gateOpen.b, 0.30f + 0.35f * pulse)
            batch.color = tint
            batch.draw(textures.pixel, openingX, openingY, openingWidth, openingHeight)
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            tint.set(Palette.gateLocked.r, Palette.gateLocked.g, Palette.gateLocked.b, 0.92f)
            batch.color = tint
            batch.draw(textures.pixel, openingX, openingY, openingWidth, openingHeight)
        }

        // The frame itself brightens when the gate opens, so it reads even from across the arena.
        if (open) batch.setColor(Color.WHITE) else batch.setColor(0.72f, 0.74f, 0.82f, 1f)
        batch.draw(textures.gate, exit.left, exit.bottom, exit.width, exit.height)
        batch.setColor(Color.WHITE)
    }

    /**
     * Draws a possibly-partial tile by sampling only the part of the texture that fits, so a block
     * whose size is not a whole number of tiles is cropped rather than squashed.
     */
    private fun drawClipped(
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tileWidth: Float,
        tileHeight: Float,
        fromTop: Boolean,
    ) {
        val sourceWidth = (region.regionWidth * (width / tileWidth)).toInt().coerceAtLeast(1)
        val sourceHeight = (region.regionHeight * (height / tileHeight)).toInt().coerceAtLeast(1)
        val sourceY = if (fromTop) region.regionY else region.regionY + region.regionHeight - sourceHeight
        batch.draw(
            region.texture,
            x,
            y,
            width,
            height,
            region.regionX,
            sourceY,
            sourceWidth,
            sourceHeight,
            false,
            false,
        )
    }

    // -------------------------------------------------------------------------------------------
    // Entities
    // -------------------------------------------------------------------------------------------

    private fun drawPlayer(player: PlayerCube, elapsedTime: Float) {
        // Invulnerability is shown by strobing the cube rather than hiding it, so the player never
        // loses track of where they are.
        val alpha = if (player.isInvulnerable && (elapsedTime * 18f).toInt() % 2 == 0) 0.4f else 1f
        batch.setColor(1f, 1f, 1f, alpha)
        batch.draw(
            textures.player,
            player.position.x,
            player.position.y,
            player.size / 2f,
            0f,
            player.size,
            player.size,
            player.squash.x,
            player.squash.y,
            0f,
        )
        drawHitFlash(
            player.position.x,
            player.position.y,
            player.size,
            player.squash.x,
            player.squash.y,
            player.hitFlashTimer,
        )
        batch.setColor(Color.WHITE)
    }

    /**
     * The arc the next shot will actually take, dotted out of the muzzle.
     *
     * Bullets fall now, so a straight tracer along the aim would be a lie — the further the shot, the
     * bigger the lie. This walks the same ballistic curve the simulation will fly, stops the moment
     * it meets geometry, and marks where it lands. That turns the drop from a thing the player has to
     * discover by missing into a thing they can read before pulling the trigger.
     *
     * It is drawn from [Ballistics] and [WorldPhysics] — the very code the bullet will be integrated
     * by — so the promise and the shot cannot drift apart.
     */
    private fun drawAimGuide(state: GameState) {
        val aim = state.input.aim ?: return
        val muzzle = state.player.muzzle
        val velocity = aim * GameConfig.PLAYER_BULLET_SPEED

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        var landed: Vec2? = null
        for (step in 1..TRACER_DOTS) {
            val time = step * TRACER_INTERVAL
            val point = Ballistics.positionAt(
                muzzle,
                velocity,
                GameConfig.PLAYER_BULLET_GRAVITY_SCALE,
                time,
            )
            if (WorldPhysics.collidesWithSolid(Aabb.centeredSquare(point, 2f), state.level)) {
                landed = point
                break
            }
            val fade = 1f - step / (TRACER_DOTS + 1f)
            val size = 1.2f + 1.4f * fade
            tint.set(Palette.muzzle.r, Palette.muzzle.g, Palette.muzzle.b, 0.55f * fade)
            batch.color = tint
            batch.draw(textures.pixel, point.x - size / 2f, point.y - size / 2f, size, size)
        }

        landed?.let { impact ->
            tint.set(Palette.impact.r, Palette.impact.g, Palette.impact.b, 0.75f)
            batch.color = tint
            batch.draw(textures.pixel, impact.x - 2f, impact.y - 2f, 4f, 4f)
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    private fun drawEnemy(enemy: EnemyCube, elapsedTime: Float) {
        // Spawning cubes fade and grow into place so they never appear on top of the player.
        val progress = enemy.spawnProgress
        val alpha = if (enemy.isMaterialising) progress else 1f
        val scale = if (enemy.isMaterialising) 0.35f + 0.65f * progress else 1f
        // A slow idle bob keeps the cubes from looking like static props.
        val bob = if (enemy.onGround) abs(sin(enemy.animationTime * 3.4f)) * 0.7f else 0f

        batch.setColor(1f, 1f, 1f, alpha)
        batch.draw(
            textures.enemy(enemy.archetype),
            enemy.position.x,
            enemy.position.y + bob,
            enemy.size / 2f,
            0f,
            enemy.size,
            enemy.size,
            scale,
            scale,
            0f,
        )
        if (!enemy.isMaterialising) drawWeakSpot(enemy, bob, elapsedTime)
        drawHitFlash(enemy.position.x, enemy.position.y + bob, enemy.size, scale, scale, enemy.hitFlashTimer)
        batch.setColor(Color.WHITE)
    }

    /**
     * Marks the one place on a cube that is worth shooting.
     *
     * Every archetype has a weakness and the player cannot be expected to learn eight of them by
     * dying, so each one is drawn on the cube it belongs to. The three positional weaknesses get a
     * band exactly where the rule measures — the head, the belly, the back — which means the marker
     * is not a hint about the weak spot, it *is* the weak spot. The conditional ones cannot be
     * painted on a body, so they hang above it as a coloured pip whose colour is the only thing the
     * player has to learn.
     *
     * The band is drawn additively at low alpha so it lights the cube's own colour rather than
     * covering it, and the whole marker flares for a moment after a shot lands in it.
     */
    private fun drawWeakSpot(enemy: EnemyCube, bob: Float, elapsedTime: Float) {
        val weakness = enemy.weakness
        if (weakness == EnemyWeakness.NONE) return

        val colour = Palette.weaknessColour(weakness)
        val struck = (enemy.weakSpotFlashTimer / GameConfig.WEAK_SPOT_FLASH_TIME).coerceIn(0f, 1f)
        val pulse = 0.42f + 0.18f * sin(elapsedTime * 5f + enemy.id * 0.7f)
        val strength = pulse + struck * 0.8f

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        tint.set(colour.r, colour.g, colour.b, strength)
        batch.color = tint

        val x = enemy.position.x
        val y = enemy.position.y + bob
        val size = enemy.size
        val band = size * GameConfig.WEAK_SPOT_BAND

        when (weakness) {
            EnemyWeakness.HEADSHOT -> batch.draw(textures.pixel, x, y + size - band, size, band)
            EnemyWeakness.UNDERBELLY -> batch.draw(textures.pixel, x, y, size, band)
            EnemyWeakness.BACKSTAB -> {
                // Painted on the side the cube is *not* looking at, so it moves as the cube turns —
                // which is exactly the tell the player is being asked to read.
                val backX = if (enemy.facing == Facing.RIGHT) x else x + size - band
                batch.draw(textures.pixel, backX, y, band, size)
            }

            else -> {
                // A pip riding just above the head, bobbing gently in place.
                val pip = 3.5f
                val hover = 3f + sin(elapsedTime * 3f + enemy.id) * 0.8f
                batch.draw(textures.pixel, x + size / 2f - pip / 2f, y + size + hover, pip, pip)
            }
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    /**
     * Whitens a cube that was just hit.
     *
     * Because every cube sprite is an opaque square, laying a white square of the same footprint
     * over it produces a perfect flash — no shader, no second texture set.
     */
    private fun drawHitFlash(
        x: Float,
        y: Float,
        size: Float,
        scaleX: Float,
        scaleY: Float,
        flashTimer: Float,
    ) {
        if (flashTimer <= 0f) return
        val strength = (flashTimer / GameConfig.HIT_FLASH_TIME).coerceIn(0f, 1f)
        batch.setColor(1f, 1f, 1f, strength * 0.8f)
        batch.draw(textures.pixel, x, y, size / 2f, 0f, size, size, scaleX, scaleY, 0f)
    }

    private fun drawProjectiles(projectiles: List<Projectile>) {
        if (projectiles.isEmpty()) return
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (projectile in projectiles) {
            val region = if (projectile.owner == ProjectileOwner.PLAYER) {
                textures.playerBullet
            } else {
                textures.enemyBullet
            }
            // Stretching along the direction of travel reads as speed.
            val angle = MathUtils.atan2(projectile.velocity.y, projectile.velocity.x) * MathUtils.radiansToDegrees
            batch.setColor(Color.WHITE)
            batch.draw(
                region,
                projectile.position.x - projectile.size / 2f,
                projectile.position.y - projectile.size / 2f,
                projectile.size / 2f,
                projectile.size / 2f,
                projectile.size,
                projectile.size,
                1.7f,
                0.85f,
                angle,
            )
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    private fun drawParticles(particles: List<Particle>, behindEntities: Boolean) {
        if (particles.isEmpty()) return
        val additive = !behindEntities
        if (additive) batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (particle in particles) {
            val glowing = particle.tint == ParticleTint.MUZZLE || particle.tint == ParticleTint.IMPACT
            if (glowing != additive) continue

            val colour = Palette.tintFor(particle.tint)
            tint.set(colour.r, colour.g, colour.b, particle.lifeFraction)
            batch.color = tint
            // Debris shrinks as it dies, which hides the pop when it is removed.
            val size = particle.size * (0.45f + 0.55f * particle.lifeFraction)
            batch.draw(
                textures.pixel,
                particle.position.x - size / 2f,
                particle.position.y - size / 2f,
                size / 2f,
                size / 2f,
                size,
                size,
                1f,
                1f,
                particle.rotation * MathUtils.radiansToDegrees,
            )
        }

        if (additive) batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    private companion object {
        // The gate sprite is authored at its world size, so these are both texture and world units.
        const val GATE_POST_WIDTH = 6f
        const val GATE_LINTEL_HEIGHT = 6f
        const val GATE_SILL_HEIGHT = 2f

        /** How many dots the aim arc is drawn from, and how far apart in flight time they sit. */
        const val TRACER_DOTS = 14
        const val TRACER_INTERVAL = 0.045f
    }
}
