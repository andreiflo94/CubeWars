package com.heixss.cubewars.presentation

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.heixss.cubewars.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single Android entry point.
 *
 * It owns the coroutine scope the simulation runs in, builds the object graph, and hands libGDX the
 * [CubeWarsGame] renderer. Everything Android-specific stops here.
 */
class GameActivity : AndroidApplication() {

    /**
     * The simulation runs off the main thread on [Dispatchers.Default]; the render thread only ever
     * reads the published state, so neither blocks the other.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = AppContainer(applicationContext, scope)

        // Haptics are driven by observing the state rather than by the renderer, so a slow frame
        // can never fire the same buzz twice.
        scope.launch {
            container.engine.state
                .map { it.frameEvents }
                .filter { it.isNotEmpty() }
                .collect(container.haptics::play)
        }

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            numSamples = 0
            // No depth or stencil buffer: this is a 2D game and the memory is better spent
            // elsewhere.
            depth = 0
            stencil = 0
        }

        initialize(CubeWarsGame(container.engine), config)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
