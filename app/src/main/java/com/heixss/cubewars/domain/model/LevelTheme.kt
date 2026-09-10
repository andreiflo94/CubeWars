package com.heixss.cubewars.domain.model

/**
 * The mood an arena is dressed in.
 *
 * The domain names the theme and stops there — it has no idea what colour a foundry is. `Palette`
 * turns each of these into a sky, a skyline and a set of tiles, and `TextureFactory` bakes one strip
 * of art per theme at start-up. That is what lets ten arenas look like ten places without a single
 * image file or a rule that knows about pixels.
 */
enum class LevelTheme {
    FOUNDRY,
    SCRAPYARD,
    GANTRY,
    CINDER,
    FROST,
    TOXIC,
    CIRCUIT,
    VOID,
    DUSK,
    SPIRE,
}
