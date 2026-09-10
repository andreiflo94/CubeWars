package com.heixss.cubewars.domain.model

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 2D vector. World units are "virtual pixels" (see `GameConfig`). */
data class Vec2(val x: Float, val y: Float) {

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

    fun withX(x: Float): Vec2 = Vec2(x, y)

    fun withY(y: Float): Vec2 = Vec2(x, y)

    val length: Float get() = sqrt(x * x + y * y)

    fun normalized(): Vec2 {
        val len = length
        return if (len <= 0.0001f) ZERO else Vec2(x / len, y / len)
    }

    /** This vector turned counter-clockwise by [radians]. */
    fun rotated(radians: Float): Vec2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}
