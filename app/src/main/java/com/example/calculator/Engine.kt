package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Exact decimal math (BigDecimal): 0.1 + 0.2 = 0.3, no floating-point noise. Fully offline. */
object Engine {
    private val MC = MathContext(30, RoundingMode.HALF_EVEN)
    private val OUT = MathContext(15, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal(100)

    fun evaluate(expr: String): BigDecimal? = try {
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val p = Parser(expr + ")".repeat(maxOf(open, 0)))
        p.parseExpr().takeIf { p.atEnd() }
    } catch (e: Exception) {
        null // syntax error, divide by zero, etc.
    }

    /** Machine-friendly string that can be fed back into an expression. */
    fun plain(v: BigDecimal): String {
        val r = v.round(OUT)
        return if (r.signum() == 0) "0" else r.stripTrailingZeros().toPlainString().replace('-', '−')
    }

    /** Human-friendly result: grouped digits, scientific for huge/tiny values. */
    fun format(v: BigDecimal): String {
        val r = v.round(OUT)
        if (r.signum() == 0) return "0"
        val a = r.abs()
        return if (a >= BigDecimal("1E15") || a < BigDecimal("1E-9"))
            r.round(MathContext(8)).stripTrailingZeros().toString().replace("E+", "E").replace('-', '−')
        else Fmt.display(plain(r))
    }

    private class Parser(val s: String) {
        var i = 0
        var pct = false // last term was a bare "N%" (so 50+10% means 50+5)

        fun atEnd() = i >= s.length
        fun peek(): Char? = s.getOrNull(i)

        fun parseExpr(): BigDecimal {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; val t = parseTerm(); v = v.add(if (pct) v.multiply(t, MC) else t, MC) }
                    '−' -> { i++; val t = parseTerm(); v = v.subtract(if (pct) v.multiply(t, MC) else t, MC) }
                    else -> return v
                }
            }
        }

        fun parseTerm(): BigDecimal {
            var v = parseUnary()
            while (true) {
                val c = peek()
                when {
                    c == '×' -> { i++; v = v.multiply(parseUnary(), MC); pct = false }
                    c == '÷' -> {
                        i++
                        val d = parseUnary()
                        if (d.signum() == 0) throw ArithmeticException("div0")
                        v = v.divide(d, MC); pct = false
                    }
                    c == '(' || (c != null && (c.isDigit() || c == '.')) -> { v = v.multiply(parseUnary(), MC); pct = false } // implicit ×
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
            while (peek() == '%') { i++; v = v.divide(HUNDRED, MC); p = true }
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
