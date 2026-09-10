package com.heixss.cubewars.presentation.render

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.LevelTheme

/**
 * Everything one arena is dressed in: its tiles and its backdrop.
 *
 * Grouping them means the renderer picks a theme once per frame and then draws without ever asking
 * again which arena it is in.
 */
class ThemeTextures(
    val groundBody: TextureRegion,
    val groundCap: TextureRegion,
    val ledge: TextureRegion,
    val sky: TextureRegion,
    val stars: TextureRegion,
    val hillsFar: TextureRegion,
    val hillsNear: TextureRegion,
)

/**
 * Every sprite in the game, all of them drawn pixel by pixel at start-up by [TextureFactory].
 *
 * There is not a single image file in this project: [TextureFactory] builds `Pixmap`s in memory and
 * uploads them, which is why the whole game ships as code.
 */
class GameTextures(
    /** 1x1 white. Tinted and stretched for bars, overlays and glows. */
    val pixel: TextureRegion,
    val player: TextureRegion,
    val enemies: Map<EnemyArchetype, TextureRegion>,
    val playerBullet: TextureRegion,
    val enemyBullet: TextureRegion,
    /** One art set per arena theme, baked once and picked per frame. */
    val themes: Map<LevelTheme, ThemeTextures>,
    val gate: TextureRegion,
    val controlPad: TextureRegion,
    val iconLeft: TextureRegion,
    val iconRight: TextureRegion,
    val iconJump: TextureRegion,
    val iconFire: TextureRegion,
    val iconAim: TextureRegion,
    val iconPause: TextureRegion,
    private val owned: List<Texture>,
) : Disposable {

    fun enemy(archetype: EnemyArchetype): TextureRegion =
        enemies.getValue(archetype)

    fun theme(theme: LevelTheme): ThemeTextures =
        themes.getValue(theme)

    override fun dispose() {
        owned.forEach(Texture::dispose)
    }
}
