package com.heixss.cubewars.presentation.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.LevelTheme
import com.heixss.cubewars.presentation.render.Palette.CubeSkin
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the entire art set into memory at start-up.
 *
 * Nothing here is loaded from disk. Each sprite is a `Pixmap` filled a rectangle at a time, then
 * uploaded once as a GPU texture and never touched again. Sprites are authored at their exact world
 * size and sampled with `Nearest`, so the 480x270 virtual screen scales up to any device as crisp
 * pixel art rather than as a blur.
 */
object TextureFactory {

    /** Builds every texture. The caller owns the result and must dispose it. */
    fun create(): GameTextures {
        val owned = mutableListOf<Texture>()

        fun crisp(pixmap: Pixmap): TextureRegion = upload(pixmap, owned, Texture.TextureFilter.Nearest)
        fun smooth(pixmap: Pixmap): TextureRegion = upload(pixmap, owned, Texture.TextureFilter.Linear)

        val enemyRegions = EnemyArchetype.entries.associateWith { archetype ->
            crisp(
                cube(
                    size = archetype.size.roundToInt(),
                    skin = Palette.skinFor(archetype),
                    scowling = true,
                    vented = archetype.size >= 18f,
                ),
            )
        }

        // One art set per theme. Ten arenas is forty little tiles and thirty backdrop strips, which
        // costs a few hundred kilobytes once at start-up and buys the rotation ten distinct places
        // to fight in — far cheaper than tinting shared tiles at draw time, which can only ever
        // darken them.
        val themeSets = LevelTheme.entries.associateWith { theme ->
            val skin = Palette.arena(theme)
            ThemeTextures(
                groundBody = crisp(groundTile(skin, capped = false)),
                groundCap = crisp(groundTile(skin, capped = true)),
                ledge = crisp(ledgeTile(skin)),
                sky = smooth(verticalGradient(4, 256, skin.skyTop, skin.skyBottom)),
                stars = crisp(starField(skin)),
                hillsFar = crisp(hillLayer(skin.hillsFar, baseHeight = 46, amplitude = 17f, seedPhase = 0.4f)),
                hillsNear = crisp(hillLayer(skin.hillsNear, baseHeight = 30, amplitude = 12f, seedPhase = 2.1f)),
            )
        }

        return GameTextures(
            pixel = crisp(solid(1, 1, Color.WHITE)),
            player = crisp(cube(14, Palette.player, scowling = false, vented = false)),
            enemies = enemyRegions,
            playerBullet = crisp(bullet(6, Palette.playerBullet)),
            enemyBullet = crisp(bullet(6, Palette.enemyBullet)),
            themes = themeSets,
            gate = crisp(gateFrame()),
            controlPad = smooth(controlPad()),
            iconLeft = smooth(arrowIcon(pointingRight = false)),
            iconRight = smooth(arrowIcon(pointingRight = true)),
            iconJump = smooth(jumpIcon()),
            iconFire = smooth(fireIcon()),
            iconAim = smooth(aimIcon()),
            iconPause = smooth(pauseIcon()),
            owned = owned,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Cubes
    // ---------------------------------------------------------------------------------------------

    /**
     * A cube with a lit top-left bevel, a shaded bottom-right bevel, and a face.
     *
     * The bevel is what sells a flat square as a solid object: one tone lighter where an overhead
     * light would land, one tone darker on the opposite corner, and a hard outline so it reads
     * against the backdrop at any size.
     */
    private fun cube(size: Int, skin: CubeSkin, scowling: Boolean, vented: Boolean): Pixmap {
        val pixmap = blank(size, size)
        val bevel = (size / 9).coerceAtLeast(1)

        pixmap.setColor(skin.body)
        pixmap.fill()

        pixmap.setColor(skin.light)
        pixmap.fillRectangle(0, 0, size, bevel)
        pixmap.fillRectangle(0, 0, bevel, size)

        pixmap.setColor(skin.dark)
        pixmap.fillRectangle(0, size - bevel, size, bevel)
        pixmap.fillRectangle(size - bevel, 0, bevel, size)

        pixmap.setColor(skin.dark.shaded(0.5f))
        pixmap.drawRectangle(0, 0, size, size)

        // A faint inner panel line gives the face some depth without adding detail noise.
        pixmap.setColor(skin.dark.shaded(0.85f).withAlpha(0.5f))
        val inset = bevel + 1
        if (size - inset * 2 > 3) {
            pixmap.drawRectangle(inset, inset, size - inset * 2, size - inset * 2)
        }

        drawFace(pixmap, size, skin, scowling, vented)
        return pixmap
    }

    private fun drawFace(pixmap: Pixmap, size: Int, skin: CubeSkin, scowling: Boolean, vented: Boolean) {
        val eyeWidth = (size * 0.21f).roundToInt().coerceAtLeast(2)
        val eyeHeight = (size * 0.26f).roundToInt().coerceAtLeast(2)
        val eyeTop = (size * 0.33f).roundToInt()
        val leftEyeX = (size * 0.21f).roundToInt().coerceAtLeast(2)
        val rightEyeX = size - leftEyeX - eyeWidth

        pixmap.setColor(skin.eye)
        pixmap.fillRectangle(leftEyeX, eyeTop, eyeWidth, eyeHeight)
        pixmap.fillRectangle(rightEyeX, eyeTop, eyeWidth, eyeHeight)

        // A single lit pixel per eye is enough to make a flat block look alive.
        pixmap.setColor(1f, 1f, 1f, 0.85f)
        pixmap.drawPixel(leftEyeX, eyeTop)
        pixmap.drawPixel(rightEyeX, eyeTop)

        if (scowling && eyeTop >= 3) {
            // Brows angled down toward the nose read as hostile even at eleven pixels wide.
            pixmap.setColor(skin.eye)
            pixmap.drawLine(leftEyeX, eyeTop - 3, leftEyeX + eyeWidth - 1, eyeTop - 1)
            pixmap.drawLine(rightEyeX, eyeTop - 1, rightEyeX + eyeWidth - 1, eyeTop - 3)
        }

        if (vented) {
            val ventWidth = (size * 0.38f).roundToInt()
            pixmap.setColor(skin.dark.shaded(0.7f))
            pixmap.fillRectangle((size - ventWidth) / 2, (size * 0.68f).roundToInt(), ventWidth, 2)
        } else if (size >= 14) {
            pixmap.setColor(skin.dark.shaded(0.7f))
            pixmap.fillRectangle((size * 0.38f).roundToInt(), (size * 0.7f).roundToInt(), (size * 0.24f).roundToInt(), 1)
        }
    }

    /** A bullet is a cube too — a bright core inside a soft halo. */
    private fun bullet(size: Int, color: Color): Pixmap {
        val pixmap = blank(size, size)
        pixmap.setColor(color.withAlpha(0.32f))
        pixmap.fill()
        pixmap.setColor(color)
        pixmap.fillRectangle(1, 1, size - 2, size - 2)
        pixmap.setColor(1f, 1f, 1f, 0.92f)
        pixmap.fillRectangle(size / 2 - 1, size / 2 - 1, 2, 2)
        return pixmap
    }

    // ---------------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------------

    /**
     * A 16x16 chunk of floor in one arena's colours. [capped] adds the lit surface layer used on the
     * topmost row of a block, so a solid mass reads as ground rather than as a wall.
     *
     * Only the theme's two anchor tones are given; the speckle, the seam and the highlight are all
     * shaded off them, which is what keeps ten themes in step with each other.
     */
    private fun groundTile(skin: Palette.ArenaSkin, capped: Boolean): Pixmap {
        val size = 16
        val pixmap = blank(size, size)
        val noise = Random(if (capped) 91_741 else 20_617)
        val dark = skin.ground.shaded(0.65f)
        val light = skin.ground.shaded(1.36f)

        pixmap.setColor(skin.ground)
        pixmap.fill()

        // Speckle breaks up the flat fill so a wall of tiles does not band.
        for (y in 0 until size) {
            for (x in 0 until size) {
                val roll = noise.nextFloat()
                when {
                    roll < 0.10f -> pixmap.setColor(dark)
                    roll < 0.20f -> pixmap.setColor(light)
                    else -> continue
                }
                pixmap.drawPixel(x, y)
            }
        }

        if (capped) {
            pixmap.setColor(skin.cap)
            pixmap.fillRectangle(0, 0, size, 4)
            pixmap.setColor(skin.cap.shaded(1.42f))
            pixmap.fillRectangle(0, 0, size, 1)
            pixmap.setColor(skin.cap.shaded(0.8f))
            for (x in 0 until size) {
                if (noise.nextFloat() < 0.4f) pixmap.drawPixel(x, 4)
            }
        }

        // A darker seam on two sides turns a tiled fill into visible masonry.
        pixmap.setColor(dark)
        pixmap.drawLine(size - 1, 0, size - 1, size - 1)
        pixmap.drawLine(0, size - 1, size - 1, size - 1)
        return pixmap
    }

    /**
     * The exit gate: two posts and a lintel around an empty opening.
     *
     * The opening is left transparent so the renderer can fill it with whatever the gate's state
     * calls for — dead shadow while the arena still has waves in it, a pulsing glow once it opens.
     */
    private fun gateFrame(): Pixmap {
        val width = 32
        val height = 48
        val pixmap = blank(width, height)
        val post = 6

        pixmap.setColor(Palette.gateFrame)
        pixmap.fillRectangle(0, 0, width, post)
        pixmap.fillRectangle(0, 0, post, height)
        pixmap.fillRectangle(width - post, 0, post, height)

        // A lit rim along the top and down the inside of each post makes the opening read as a way
        // through rather than as a painted-on rectangle.
        pixmap.setColor(Palette.gateFrameLight)
        pixmap.fillRectangle(0, 0, width, 2)
        pixmap.fillRectangle(post - 1, post, 1, height - post)
        pixmap.fillRectangle(width - post, post, 1, height - post)

        pixmap.setColor(Palette.gateFrame.shaded(0.6f))
        pixmap.fillRectangle(0, height - 2, width, 2)
        pixmap.drawRectangle(0, 0, width, height)
        return pixmap
    }

    /** A thin one-way ledge: lit walking surface, dark underside, a couple of rivets. */
    private fun ledgeTile(skin: Palette.ArenaSkin): Pixmap {
        val width = 16
        val height = 8
        val pixmap = blank(width, height)

        pixmap.setColor(skin.ledge)
        pixmap.fill()
        pixmap.setColor(skin.ledgeEdge)
        pixmap.fillRectangle(0, 0, width, 2)
        pixmap.setColor(skin.ledgeEdge.shaded(1.15f))
        pixmap.fillRectangle(0, 0, width, 1)
        pixmap.setColor(skin.ledge.shaded(0.55f))
        pixmap.fillRectangle(0, height - 2, width, 2)
        pixmap.setColor(skin.ledgeEdge.shaded(0.75f))
        pixmap.drawPixel(3, 4)
        pixmap.drawPixel(12, 4)
        return pixmap
    }

    // ---------------------------------------------------------------------------------------------
    // Backdrop
    // ---------------------------------------------------------------------------------------------

    private fun verticalGradient(width: Int, height: Int, top: Color, bottom: Color): Pixmap {
        val pixmap = blank(width, height)
        for (y in 0 until height) {
            val t = y / (height - 1f)
            pixmap.setColor(
                top.r + (bottom.r - top.r) * t,
                top.g + (bottom.g - top.g) * t,
                top.b + (bottom.b - top.b) * t,
                1f,
            )
            pixmap.drawLine(0, y, width - 1, y)
        }
        return pixmap
    }

    private fun starField(skin: Palette.ArenaSkin): Pixmap {
        val size = 256
        val pixmap = blank(size, size)
        val random = Random(4_412)
        repeat(150) {
            val x = random.nextInt(size)
            val y = random.nextInt(size)
            val brightness = 0.25f + random.nextFloat() * 0.75f
            pixmap.setColor(skin.star.withAlpha(brightness))
            pixmap.drawPixel(x, y)
            if (random.nextFloat() < 0.12f) {
                pixmap.setColor(skin.star.withAlpha(brightness * 0.6f))
                pixmap.drawPixel(x + 1, y)
                pixmap.drawPixel(x, y + 1)
            }
        }
        return pixmap
    }

    /**
     * A silhouette skyline.
     *
     * The height profile is a sum of sines whose periods all divide the texture width, so the layer
     * tiles horizontally without a visible seam no matter how far the camera pans.
     */
    private fun hillLayer(color: Color, baseHeight: Int, amplitude: Float, seedPhase: Float): Pixmap {
        val width = 256
        val height = 128
        val pixmap = blank(width, height)
        pixmap.setColor(color)

        for (x in 0 until width) {
            val phase = 2f * PI.toFloat() * x / width
            val ridge = baseHeight +
                amplitude * sin(phase + seedPhase) +
                amplitude * 0.45f * sin(phase * 3f + seedPhase * 2f) +
                amplitude * 0.2f * sin(phase * 7f + seedPhase * 3f)
            val top = (height - ridge).roundToInt().coerceIn(0, height - 1)
            pixmap.drawLine(x, top, x, height - 1)
        }

        // A lighter rim along the ridge separates the layers from each other.
        pixmap.setColor(color.shaded(1.45f))
        for (x in 0 until width) {
            val phase = 2f * PI.toFloat() * x / width
            val ridge = baseHeight +
                amplitude * sin(phase + seedPhase) +
                amplitude * 0.45f * sin(phase * 3f + seedPhase * 2f) +
                amplitude * 0.2f * sin(phase * 7f + seedPhase * 3f)
            val top = (height - ridge).roundToInt().coerceIn(0, height - 1)
            pixmap.drawPixel(x, top)
        }
        return pixmap
    }

    // ---------------------------------------------------------------------------------------------
    // On-screen controls
    // ---------------------------------------------------------------------------------------------

    private fun controlPad(): Pixmap {
        val size = 96
        val pixmap = blank(size, size)
        val centre = size / 2
        pixmap.setColor(Palette.controlTint.withAlpha(0.13f))
        pixmap.fillCircle(centre, centre, centre - 3)
        pixmap.setColor(Palette.controlTint.withAlpha(0.4f))
        pixmap.drawCircle(centre, centre, centre - 3)
        pixmap.drawCircle(centre, centre, centre - 4)
        return pixmap
    }

    private fun arrowIcon(pointingRight: Boolean): Pixmap {
        val size = 48
        val pixmap = blank(size, size)
        pixmap.setColor(Palette.controlTint.withAlpha(0.92f))
        if (pointingRight) {
            pixmap.fillTriangle(16, 10, 16, 38, 36, 24)
        } else {
            pixmap.fillTriangle(32, 10, 32, 38, 12, 24)
        }
        return pixmap
    }

    private fun jumpIcon(): Pixmap {
        val size = 48
        val pixmap = blank(size, size)
        pixmap.setColor(Palette.controlTint.withAlpha(0.92f))
        pixmap.fillTriangle(24, 10, 10, 28, 38, 28)
        pixmap.fillRectangle(14, 32, 20, 5)
        return pixmap
    }

    /** A little cube with speed lines behind it — the fire button shoots cubes, after all. */
    private fun fireIcon(): Pixmap {
        val size = 48
        val pixmap = blank(size, size)
        pixmap.setColor(Palette.controlTint.withAlpha(0.92f))
        pixmap.fillRectangle(26, 18, 12, 12)
        pixmap.setColor(Palette.controlTint.withAlpha(0.55f))
        pixmap.fillRectangle(8, 20, 13, 2)
        pixmap.fillRectangle(12, 26, 9, 2)
        return pixmap
    }

    private fun aimIcon(): Pixmap {
        val size = 48
        val pixmap = blank(size, size)
        pixmap.setColor(Palette.controlTint.withAlpha(0.92f))
        pixmap.fillTriangle(24, 12, 12, 26, 36, 26)
        pixmap.setColor(Palette.controlTint.withAlpha(0.55f))
        pixmap.fillTriangle(24, 24, 14, 35, 34, 35)
        return pixmap
    }

    private fun pauseIcon(): Pixmap {
        val size = 48
        val pixmap = blank(size, size)
        pixmap.setColor(Palette.controlTint.withAlpha(0.92f))
        pixmap.fillRectangle(16, 13, 6, 22)
        pixmap.fillRectangle(27, 13, 6, 22)
        return pixmap
    }

    // ---------------------------------------------------------------------------------------------
    // Plumbing
    // ---------------------------------------------------------------------------------------------

    /**
     * A transparent canvas with blending switched off, so every subsequent draw writes its alpha
     * verbatim instead of compositing — which is what pixel art wants.
     */
    private fun blank(width: Int, height: Int): Pixmap =
        Pixmap(width, height, Pixmap.Format.RGBA8888).apply {
            blending = Pixmap.Blending.None
            setColor(0f, 0f, 0f, 0f)
            fill()
        }

    private fun solid(width: Int, height: Int, color: Color): Pixmap =
        blank(width, height).apply {
            setColor(color)
            fill()
        }

    private fun upload(
        pixmap: Pixmap,
        owned: MutableList<Texture>,
        filter: Texture.TextureFilter,
    ): TextureRegion {
        val texture = Texture(pixmap)
        texture.setFilter(filter, filter)
        // The pixel data lives on the GPU now; the CPU copy is dead weight.
        pixmap.dispose()
        owned += texture
        return TextureRegion(texture)
    }

    private fun Color.shaded(factor: Float): Color =
        Color((r * factor).coerceAtMost(1f), (g * factor).coerceAtMost(1f), (b * factor).coerceAtMost(1f), a)

    private fun Color.withAlpha(alpha: Float): Color = Color(r, g, b, alpha)
}
