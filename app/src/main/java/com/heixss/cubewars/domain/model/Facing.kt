package com.heixss.cubewars.domain.model

/** Which way a cube is looking, and therefore which way it shoots. */
enum class Facing(val sign: Float) {
    LEFT(-1f),
    RIGHT(1f);

    fun flipped(): Facing = if (this == LEFT) RIGHT else LEFT
}
