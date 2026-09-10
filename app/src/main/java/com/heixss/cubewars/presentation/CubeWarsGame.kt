package com.heixss.cubewars.presentation

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.heixss.cubewars.domain.engine.GameEngine
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.presentation.input.GameInputProcessor
import com.heixss.cubewars.presentation.render.GameTextures
import com.heixss.cubewars.presentation.render.HudRenderer
import com.heixss.cubewars.presentation.render.Palette
import com.heixss.cubewars.presentation.render.TextureFactory
import com.heixss.cubewars.presentation.render.WorldRenderer

/**
 * The libGDX side of the app.
 *
 * Note what this class does *not* do: it never advances the game. The simulation runs on its own
 * coroutine at a fixed step, and `render` simply reads the latest published [GameState] and draws
 * it. Rendering and simulation are therefore fully decoupled — a dropped frame costs a frame of
 * animation, not a frame of physics.
 */
class CubeWarsGame(
    private val engine: GameEngine,
) : ApplicationListener {

    private lateinit var batch: SpriteBatch
    private lateinit var textures: GameTextures
    private lateinit var worldRenderer: WorldRenderer
    private lateinit var hudRenderer: HudRenderer
    private lateinit var inputProcessor: GameInputProcessor

    override fun create() {
        batch = SpriteBatch()
        textures = TextureFactory.create()
        worldRenderer = WorldRenderer(textures, batch)
        hudRenderer = HudRenderer(textures, batch)

        inputProcessor = GameInputProcessor(hudRenderer.viewport, engine::dispatch)
        Gdx.input.inputProcessor = inputProcessor

        engine.start()
    }

    override fun render() {
        val state = engine.state.value
        inputProcessor.gameplayEnabled = state.status == GameStatus.RUNNING

        // Clear to the current arena's own sky, so the letterbox bars either side of the viewport
        // belong to the place the player is standing in rather than to the first level.
        val sky = Palette.arena(state.level.theme).skyTop
        Gdx.gl.glClearColor(sky.r, sky.g, sky.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        worldRenderer.render(state)
        hudRenderer.render(state, inputProcessor.pressedButtons())
    }

    override fun resize(width: Int, height: Int) {
        worldRenderer.resize(width, height)
        hudRenderer.resize(width, height)
    }

    override fun pause() {
        // Losing focus mid-jump should not leave the button stuck down.
        if (this::inputProcessor.isInitialized) inputProcessor.releaseAll()
        engine.pause()
    }

    override fun resume() {
        engine.resume()
    }

    override fun dispose() {
        if (this::hudRenderer.isInitialized) hudRenderer.dispose()
        if (this::textures.isInitialized) textures.dispose()
        if (this::batch.isInitialized) batch.dispose()
    }
}
