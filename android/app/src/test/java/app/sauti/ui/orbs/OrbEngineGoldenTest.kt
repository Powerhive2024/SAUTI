package app.sauti.ui.orbs

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Parity with the upstream web engine: every dot and line of every (state × size × t) golden
 * case from thinking-orbs' spec/orbs-golden.json must match within the spec tolerance (1e-4).
 */
class OrbEngineGoldenTest {
    private class Case(val state: OrbState, val size: Int, val t: Double, val dotCount: Int, val lineCount: Int,
                       val dots: DoubleArray, val lines: DoubleArray)

    private fun load(): List<Case> {
        val text = javaClass.classLoader!!.getResource("orbs-golden.txt")!!.readText()
        val out = ArrayList<Case>()
        val it = text.lineSequence().filter { l -> l.isNotBlank() && !l.startsWith("#") }.iterator()
        while (it.hasNext()) {
            val h = it.next().split(" ")
            check(h[0] == "CASE")
            val d = it.next().removePrefix("D").trim().let { s -> if (s.isEmpty()) DoubleArray(0) else s.split(" ").map(String::toDouble).toDoubleArray() }
            val l = it.next().removePrefix("L").trim().let { s -> if (s.isEmpty()) DoubleArray(0) else s.split(" ").map(String::toDouble).toDoubleArray() }
            out += Case(OrbState.valueOf(h[1].uppercase()), h[2].toInt(), h[3].toDouble(), h[4].toInt(), h[5].toInt(), d, l)
        }
        return out
    }

    @Test fun allGoldenCasesMatch() {
        val cases = load()
        assertEquals(72, cases.size)
        val tol = 1e-4
        val failures = ArrayList<String>()
        for (c in cases) {
            val f = resolveOrb(c.state, c.size).frame(c.t)
            val tag = "${c.state}-${c.size}@${c.t}"
            if (f.dots.size != c.dotCount || f.lines.size != c.lineCount) {
                failures += "$tag count dots ${f.dots.size}/${c.dotCount} lines ${f.lines.size}/${c.lineCount}"
                continue
            }
            var worst = 0.0
            f.dots.forEachIndexed { i, d ->
                val e = doubleArrayOf(d.x, d.y, d.z, d.r, d.white, d.a)
                for (k in 0 until 6) worst = maxOf(worst, abs(e[k] - c.dots[i * 6 + k]))
            }
            f.lines.forEachIndexed { i, l ->
                val e = doubleArrayOf(l.x1, l.y1, l.x2, l.y2, l.white, l.a, l.w)
                for (k in 0 until 7) worst = maxOf(worst, abs(e[k] - c.lines[i * 7 + k]))
            }
            if (worst > tol) failures += "$tag max abs error $worst"
        }
        if (failures.isNotEmpty()) fail("${failures.size}/${cases.size} cases differ:\n" + failures.joinToString("\n"))
    }
}
