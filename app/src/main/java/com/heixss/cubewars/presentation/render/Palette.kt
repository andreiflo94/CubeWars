package com.heixss.cubewars.presentation.render

import com.badlogic.gdx.graphics.Color
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.EnemyWeakness
import com.heixss.cubewars.domain.model.LevelTheme
import com.heixss.cubewars.domain.model.ParticleTint

/**
 * Every colour in the game, in one place.
 *
 * The domain names roles (`ParticleTint.ENEMY`, `EnemyArchetype.BRUTE`, `LevelTheme.FROST`); this
 * file is the only place that turns a role into pixels. Re-skinning the game is a one-file change,
 * and no rule anywhere has to know that a cinder pit is red.
 */
object Palette {

    /** A cube is drawn from three tones: a flat body, a lit bevel and a shaded bevel. */
    data class CubeSkin(val body: Color, val light: Color, val dark: Color, val eye: Color)

    /**
     * What one arena is dressed in.
     *
     * Only the anchor tones are named. Every shade around them — the lit edge of a ledge, the seam
     * between two floor tiles, the rim along a distant ridge — is derived from these by the same
     * arithmetic for every theme, which is what stops ten arenas from needing ten sets of eleven
     * hand-picked colours that drift apart the moment one of them is adjusted.
     */
    data class ArenaSkin(
        val skyTop: Color,
        val skyBottom: Color,
        val hillsFar: Color,
        val hillsNear: Color,
        val star: Color,
        /** Mid tone of the floor mass. */
        val ground: Color,
        /** The lit, walkable surface on top of it. */
        val cap: Color,
        /** Body tone of the one-way ledges. */
        val ledge: Color,
        /** The lit edge of a ledge — the one colour that says "you can stand on this". */
        val ledgeEdge: Color,
    )

    private fun rgb(hex: Int): Color = Color(
        ((hex shr 16) and 0xFF) / 255f,
        ((hex shr 8) and 0xFF) / 255f,
        (hex and 0xFF) / 255f,
        1f,
    )

    // --- Arenas ---------------------------------------------------------------------------------

    private val arenaSkins: Map<LevelTheme, ArenaSkin> = mapOf(
        LevelTheme.FOUNDRY to ArenaSkin(
            skyTop = rgb(0x0B0E1A),
            skyBottom = rgb(0x232C4A),
            hillsFar = rgb(0x161C2E),
            hillsNear = rgb(0x1F2740),
            star = rgb(0x8FA4D6),
            ground = rgb(0x35425A),
            cap = rgb(0x6E86AC),
            ledge = rgb(0x4A3F5F),
            ledgeEdge = rgb(0xA98BD6),
        ),
        LevelTheme.SCRAPYARD to ArenaSkin(
            skyTop = rgb(0x160F0B),
            skyBottom = rgb(0x4A3524),
            hillsFar = rgb(0x2A1D16),
            hillsNear = rgb(0x3B2A1E),
            star = rgb(0xD6B48F),
            ground = rgb(0x5A4232),
            cap = rgb(0xA8794C),
            ledge = rgb(0x5F4A2B),
            ledgeEdge = rgb(0xD6B06B),
        ),
        LevelTheme.GANTRY to ArenaSkin(
            skyTop = rgb(0x081420),
            skyBottom = rgb(0x1E4460),
            hillsFar = rgb(0x102534),
            hillsNear = rgb(0x173548),
            star = rgb(0x9FD6E8),
            ground = rgb(0x2E4756),
            cap = rgb(0x5F93AC),
            ledge = rgb(0x384F3E),
            ledgeEdge = rgb(0x7FD69B),
        ),
        LevelTheme.CINDER to ArenaSkin(
            skyTop = rgb(0x1A0708),
            skyBottom = rgb(0x5A1A16),
            hillsFar = rgb(0x2E100E),
            hillsNear = rgb(0x431814),
            star = rgb(0xE8A18F),
            ground = rgb(0x4A2A26),
            cap = rgb(0x9C4A38),
            ledge = rgb(0x4A2018),
            ledgeEdge = rgb(0xF08040),
        ),
        LevelTheme.FROST to ArenaSkin(
            skyTop = rgb(0x0A1622),
            skyBottom = rgb(0x2E5A78),
            hillsFar = rgb(0x142838),
            hillsNear = rgb(0x1E3A4E),
            star = rgb(0xDFF2FF),
            ground = rgb(0x3D5568),
            cap = rgb(0x9EC6DE),
            ledge = rgb(0x2F4C60),
            ledgeEdge = rgb(0xB6E8FF),
        ),
        LevelTheme.TOXIC to ArenaSkin(
            skyTop = rgb(0x0A1408),
            skyBottom = rgb(0x24401C),
            hillsFar = rgb(0x122210),
            hillsNear = rgb(0x1B3318),
            star = rgb(0xB6E88F),
            ground = rgb(0x37472C),
            cap = rgb(0x6E9C46),
            ledge = rgb(0x2E4426),
            ledgeEdge = rgb(0xA8F05A),
        ),
        LevelTheme.CIRCUIT to ArenaSkin(
            skyTop = rgb(0x04120F),
            skyBottom = rgb(0x0E3A34),
            hillsFar = rgb(0x0A2320),
            hillsNear = rgb(0x0F332E),
            star = rgb(0x6BF0D2),
            ground = rgb(0x1E3A38),
            cap = rgb(0x3E8C80),
            ledge = rgb(0x24382E),
            ledgeEdge = rgb(0x5BF0C4),
        ),
        LevelTheme.VOID to ArenaSkin(
            skyTop = rgb(0x030308),
            skyBottom = rgb(0x14102A),
            hillsFar = rgb(0x0A0818),
            hillsNear = rgb(0x120E28),
            star = rgb(0xC8B4FF),
            ground = rgb(0x241E3A),
            cap = rgb(0x5A4C8C),
            ledge = rgb(0x2C2050),
            ledgeEdge = rgb(0x9A7CF0),
        ),
        LevelTheme.DUSK to ArenaSkin(
            skyTop = rgb(0x160B26),
            skyBottom = rgb(0x8A3A2E),
            hillsFar = rgb(0x2A1330),
            hillsNear = rgb(0x421A34),
            star = rgb(0xFFD6A3),
            ground = rgb(0x462C42),
            cap = rgb(0x9C5A5E),
            ledge = rgb(0x4A2838),
            ledgeEdge = rgb(0xFFA36B),
        ),
        LevelTheme.SPIRE to ArenaSkin(
            skyTop = rgb(0x0D0620),
            skyBottom = rgb(0x3A2470),
            hillsFar = rgb(0x1A1038),
            hillsNear = rgb(0x281A52),
            star = rgb(0xFFE8A3),
            ground = rgb(0x38306A),
            cap = rgb(0x7A6ED6),
            ledge = rgb(0x4A3A20),
            ledgeEdge = rgb(0xFFD46B),
        ),
    )

    fun arena(theme: LevelTheme): ArenaSkin = arenaSkins.getValue(theme)

    // --- The exit gate --------------------------------------------------------------------------
    val gateFrame = rgb(0x3B4A69)
    val gateFrameLight = rgb(0x8CA3D4)

    /** Dead metal while waves remain. */
    val gateLocked = rgb(0x151B2C)

    /** Nothing else in the game is this colour, which is the point. */
    val gateOpen = rgb(0x5BF0C4)

    // --- Cubes ---------------------------------------------------------------------------------
    val player = CubeSkin(
        body = rgb(0x3FB6F0),
        light = rgb(0x9FE6FF),
        dark = rgb(0x1A6C9C),
        eye = rgb(0x07131E),
    )

    private val cubeSkins: Map<EnemyArchetype, CubeSkin> = mapOf(
        EnemyArchetype.GRUNT to CubeSkin(
            body = rgb(0x5FC65C),
            light = rgb(0xA7EE9C),
            dark = rgb(0x2C7430),
            eye = rgb(0x0B1A0C),
        ),
        EnemyArchetype.HOPPER to CubeSkin(
            body = rgb(0xF25FA8),
            light = rgb(0xFFA8D2),
            dark = rgb(0x8E2A5E),
            eye = rgb(0x210714),
        ),
        EnemyArchetype.TURRET to CubeSkin(
            body = rgb(0x9A6CF0),
            light = rgb(0xD1B4FF),
            dark = rgb(0x54339A),
            eye = rgb(0x160B29),
        ),
        EnemyArchetype.BRUTE to CubeSkin(
            body = rgb(0xF08A3C),
            light = rgb(0xFFC58A),
            dark = rgb(0x914618),
            eye = rgb(0x27130A),
        ),
        EnemyArchetype.SNIPER to CubeSkin(
            body = rgb(0xE0DE5A),
            light = rgb(0xFFF7A8),
            dark = rgb(0x8A821F),
            eye = rgb(0x1E1C05),
        ),
        EnemyArchetype.DRONE to CubeSkin(
            body = rgb(0x4FD9D2),
            light = rgb(0xB0FFFA),
            dark = rgb(0x1E7A78),
            eye = rgb(0x04201F),
        ),
        EnemyArchetype.MORTAR to CubeSkin(
            body = rgb(0x8C6A4A),
            light = rgb(0xCFA982),
            dark = rgb(0x4A331F),
            eye = rgb(0x1A0F06),
        ),
        EnemyArchetype.WARDEN to CubeSkin(
            body = rgb(0xC0C8D6),
            light = rgb(0xF2F6FF),
            dark = rgb(0x5C6478),
            eye = rgb(0x11141C),
        ),
    )

    fun skinFor(archetype: EnemyArchetype): CubeSkin = cubeSkins.getValue(archetype)

    // --- Weak spots ------------------------------------------------------------------------------

    /**
     * One colour per weakness, so the marker on a cube and the burst when a shot finds it agree.
     *
     * The positional weaknesses get warm colours because they are drawn *on* the cube; the
     * conditional ones get cool colours because they hang above it as a pip.
     */
    private val weaknessColours: Map<EnemyWeakness, Color> = mapOf(
        EnemyWeakness.NONE to rgb(0x7E8CA8),
        EnemyWeakness.HEADSHOT to rgb(0xFFE04A),
        EnemyWeakness.UNDERBELLY to rgb(0xFF9E4A),
        EnemyWeakness.BACKSTAB to rgb(0xFF5A7A),
        EnemyWeakness.PLUNGING_FIRE to rgb(0x7ADBFF),
        EnemyWeakness.POINT_BLANK to rgb(0xFF7AE8),
        EnemyWeakness.LONG_SHOT to rgb(0x9AFF7A),
        EnemyWeakness.AIRBORNE to rgb(0xBFA3FF),
    )

    fun weaknessColour(weakness: EnemyWeakness): Color = weaknessColours.getValue(weakness)

    /** The flash a shot into a weak spot throws off. */
    val weakSpot = rgb(0xFFF2A0)

    // --- Projectiles and debris -----------------------------------------------------------------
    val playerBullet = rgb(0x8FF2FF)
    val enemyBullet = rgb(0xFF6B5A)
    val muzzle = rgb(0xFFE8A3)
    val impact = rgb(0xFFD48A)
    val dust = rgb(0x8794AE)

    fun tintFor(tint: ParticleTint): Color = when (tint) {
        ParticleTint.PLAYER -> player.body
        ParticleTint.ENEMY -> rgb(0xE8746B)
        ParticleTint.MUZZLE -> muzzle
        ParticleTint.IMPACT -> impact
        ParticleTint.DUST -> dust
        ParticleTint.WEAK_SPOT -> weakSpot
    }

    // --- Interface -------------------------------------------------------------------------------
    val hudText = rgb(0xE8F1FF)
    val hudDim = rgb(0x7E8CA8)
    val hudAccent = rgb(0x3FB6F0)
    val hudWarning = rgb(0xFF6B5A)
    val hudPanel = rgb(0x0B0E1A)
    val controlTint = rgb(0xC8D8F5)
    val flash = Color.WHITE
}
