package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact decimal math (BigDecimal): 0.1 + 0.2 = 0.3, no floating-point noise. Fully offline.
 *
 * No limits: + − × and % are always exact, at any size. The only time a result can't be exact is a
 * division that never terminates (1 ÷ 3); that is computed to [DIVISION_DIGITS] significant digits
 * internally and flagged as [Calc.inexact] so it is shown rounded but chains without drift.
 */
object Engine {
    private const val DIVISION_DIGITS = 50
    private val DIV = MathContext(DIVISION_DIGITS, RoundingMode.HALF_EVEN)
    private val SHOWN = MathContext(15, RoundingMode.HALF_UP)

    class Calc(val value: BigDecimal, val inexact: Boolean)

    fun evaluate(expr: String): Calc? = try {
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val p = Parser(expr + ")".repeat(maxOf(open, 0)))
        val v = p.parseExpr()
        if (p.atEnd()) Calc(v, p.inexact) else null
    } catch (e: Exception) {
        null // syntax error, divide by zero, etc.
    } catch (e: StackOverflowError) {
        null // absurdly deep nesting
    } catch (e: OutOfMemoryError) {
        null // number too large for this device's memory
    }

    /** Machine-friendly string that can be fed back into an expression (full precision). */
    fun plain(v: BigDecimal): String =
        if (v.signum() == 0) "0" else v.stripTrailingZeros().toPlainString().replace('-', '−')

    /** Human-friendly result: grouped digits. Exact results are shown in full, never rounded. */
    fun format(c: Calc): String {
        if (c.value.signum() == 0) return "0"
        if (!c.inexact) return Fmt.display(plain(c.value))
        // Non-terminating division: show 15 significant digits (scientific for huge/tiny values).
        val r = c.value.round(SHOWN)
        if (r.signum() == 0) return "0"
        val a = r.abs()
        return if (a >= BigDecimal("1E15") || a < BigDecimal("1E-9"))
            r.round(MathContext(8)).stripTrailingZeros().toString().replace("E+", "E").replace('-', '−')
        else Fmt.display(plain(r))
    }

    private class Parser(val s: String) {
        var i = 0
        var pct = false // last term was a bare "N%" (so 50+10% means 50+5)
        var inexact = false // a non-terminating division was rounded somewhere

        fun atEnd() = i >= s.length
        fun peek(): Char? = s.getOrNull(i)

        fun parseExpr(): BigDecimal {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; val t = parseTerm(); v = v.add(if (pct) v.multiply(t) else t) }
                    '−' -> { i++; val t = parseTerm(); v = v.subtract(if (pct) v.multiply(t) else t) }
                    else -> return v
                }
            }
        }

        fun divide(a: BigDecimal, b: BigDecimal): BigDecimal = try {
            a.divide(b) // exact whenever the quotient terminates (1 ÷ 8 = 0.125)
        } catch (e: ArithmeticException) {
            inexact = true
            a.divide(b, DIV)
        }

        fun parseTerm(): BigDecimal {
            var v = parseUnary()
            while (true) {
                val c = peek()
                when {
                    c == '×' -> { i++; v = v.multiply(parseUnary()); pct = false }
                    c == '÷' -> {
                        i++
                        val d = parseUnary()
                        if (d.signum() == 0) throw ArithmeticException("div0")
                        v = divide(v, d); pct = false
                    }
                    c == '(' || (c != null && (c.isDigit() || c == '.')) -> { v = v.multiply(parseUnary()); pct = false } // implicit ×
                    else -> return v
                }
            }
        }

        fun parseUnary(): BigDecimal = when (peek()) {
            '−' -> { i++; parseUnary().negate() }
            '+' -> { i++; parseUnary() }
            else -> parsePostfix()
        }

        fun parsePostfix(): BigDecimal {
            var v = parsePrimary()
            var p = false
            while (peek() == '%') { i++; v = v.movePointLeft(2); p = true }
            pct = p
            return v
        }

        fun parsePrimary(): BigDecimal {
            if (peek() == '(') {
                i++
                val v = parseExpr()
                check(peek() == ')'); i++
                return v
            }
            val st = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            check(i > st)
            return BigDecimal(s.substring(st, i))
        }
    }
}

object Fmt {
    private val NUM = Regex("\\d+(\\.\\d*)?")

    /** Adds thousands separators to every number in [s]. */
    fun display(s: String): String = NUM.replace(s) { m ->
        val int = m.value.substringBefore('.')
        val rest = if (m.value.contains('.')) "." + m.value.substringAfter('.') else ""
        int.reversed().chunked(3).joinToString(",").reversed() + rest
    }
}
