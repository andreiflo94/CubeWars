package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.EnemyPower
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.physics.Ballistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arc a falling bullet takes, and the promise that a cube aiming down it actually hits.
 *
 * Giving every round its own gravity quietly broke "point at the target": these are the tests that
 * say the correction works, and — the part that is easy to get wrong and impossible to notice — that
 * every shooter's gun can still reach as far as its eyes can see.
 */
class BallisticsTest {

    /** Flies a round from [origin] along [direction] and reports the closest it came to [target]. */
    private fun missDistance(
        origin: Vec2,
        direction: Vec2,
        target: Vec2,
        speed: Float,
        gravityScale: Float,
    ): Float {
        var closest = Float.MAX_VALUE
        var time = 0f
        val velocity = direction * speed
        // Sampled far finer than the simulation steps, so the answer measures the arc rather than
        // the resolution it was walked at.
        val resolution = GameConfig.FIXED_TIME_STEP / 16f
        while (time <= GameConfig.BULLET_LIFETIME) {
            val point = Ballistics.positionAt(origin, velocity, gravityScale, time)
            closest = minOf(closest, (point - target).length)
            time += resolution
        }
        return closest
    }

    @Test
    fun `a solved arc actually arrives, in both directions and up or down a tier`() {
        val origin = Vec2(200f, 40f)
        val targets = listOf(
            Vec2(340f, 40f),
            Vec2(60f, 40f),
            // A tier up and a tier down, both well inside what a 220-unit round can throw.
            Vec2(300f, 100f),
            Vec2(90f, 12f),
        )

        for (target in targets) {
            val aim = Ballistics.launchDirection(origin, target, speed = 220f, gravityScale = 0.25f)
            assertNotNull("no arc found to $target", aim)
            val miss = missDistance(origin, aim!!, target, speed = 220f, gravityScale = 0.25f)
            assertTrue("the solved arc missed $target by $miss", miss < 1f)
        }
    }

    @Test
    fun `a round that does not fall flies exactly where it is pointed`() {
        val aim = Ballistics.launchDirection(Vec2(0f, 0f), Vec2(300f, 100f), speed = 200f, gravityScale = 0f)

        assertEquals(Vec2(300f, 100f).normalized(), aim)
    }

    @Test
    fun `the lofted arc climbs higher than the flat one to the same place`() {
        val origin = Vec2(0f, 0f)
        val target = Vec2(150f, 0f)

        val flat = Ballistics.launchDirection(origin, target, 300f, 0.5f, lofted = false)!!
        val lofted = Ballistics.launchDirection(origin, target, 300f, 0.5f, lofted = true)!!

        assertTrue("the lofted solution should leave the barrel steeper", lofted.y > flat.y)
        // And both still land on the target.
        assertTrue(missDistance(origin, flat, target, 300f, 0.5f) < 1f)
        assertTrue(missDistance(origin, lofted, target, 300f, 0.5f) < 1f)
    }

    @Test
    fun `a target beyond the gun's reach yields no arc at all`() {
        // Two units per second against full gravity: this round is not going anywhere.
        assertNull(Ballistics.launchDirection(Vec2(0f, 0f), Vec2(900f, 0f), speed = 2f, gravityScale = 1f))
    }

    @Test
    fun `every shooter can throw a round as far as it can see`() {
        val shooters = EnemyArchetype.entries.filter { it.bulletsPerShot > 0 }
        assertTrue(shooters.isNotEmpty())

        for (archetype in shooters) {
            // Flat maximum range of a ballistic round. An enemy whose sight outruns its gun stands
            // in the open holding a shot it can never take.
            val maxRange = if (archetype.bulletGravityScale <= 0f) {
                Float.MAX_VALUE
            } else {
                archetype.bulletSpeed * archetype.bulletSpeed /
                    (-GameConfig.GRAVITY * archetype.bulletGravityScale)
            }
            assertTrue(
                "${archetype.name} can see ${archetype.sightRange} but can only throw $maxRange",
                maxRange > archetype.sightRange * 1.25f,
            )

            // And the arc it takes at the edge of its sight has to arrive within its lifetime.
            val origin = Vec2(0f, 40f)
            val target = Vec2(archetype.sightRange, 40f)
            val aim = Ballistics.launchDirection(
                origin = origin,
                target = target,
                speed = archetype.bulletSpeed,
                gravityScale = archetype.bulletGravityScale,
                lofted = archetype.has(EnemyPower.ARCING_SHOT),
            )
            assertNotNull("${archetype.name} found no arc to the edge of its own sight", aim)
            val miss = missDistance(origin, aim!!, target, archetype.bulletSpeed, archetype.bulletGravityScale)
            assertTrue(
                "${archetype.name}'s round expires $miss short of the edge of its sight",
                miss < 4f,
            )
        }
    }

    @Test
    fun `leading a moving target aims ahead of it, and never behind`() {
        val origin = Vec2(0f, 0f)
        val target = Vec2(200f, 0f)

        val runningAway = Ballistics.leadTarget(origin, target, Vec2(120f, 0f), speed = 300f)
        val standingStill = Ballistics.leadTarget(origin, target, Vec2.ZERO, speed = 300f)
        val closing = Ballistics.leadTarget(origin, target, Vec2(-120f, 0f), speed = 300f)

        assertTrue(runningAway.x > target.x)
        assertEquals(target, standingStill)
        assertTrue(closing.x < target.x)

        // Two refinements do not fully converge, and are not meant to: the exact answer solves
        // `p = target + velocity * p / speed`, and landing most of the way there costs two
        // multiplications instead of a loop. What matters is that the lead never overshoots it.
        val exact = 200f / (1f - 120f / 300f)
        assertTrue("the lead overshot the exact solution", runningAway.x <= exact + 0.01f)
        assertTrue(
            "the lead fell well short of the exact solution: ${runningAway.x} against $exact",
            runningAway.x - target.x >= (exact - target.x) * 0.75f,
        )
    }
}
