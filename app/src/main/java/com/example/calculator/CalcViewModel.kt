package com.example.calculator

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.math.BigDecimal

data class HistoryItem(val expr: String, val value: String)

class CalcViewModel(app: Application) : AndroidViewModel(app) {
    var expr by mutableStateOf("")
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var evaluated by mutableStateOf(false)
        private set
    val history = mutableStateListOf<HistoryItem>()

    private var lastValue: BigDecimal? = null
    private val prefs = app.getSharedPreferences("calc", 0) // local storage only

    init {
        prefs.getString("history", "")!!.lines().forEach { l ->
            val p = l.split('\t')
            if (p.size == 2) history.add(HistoryItem(p[0], p[1]))
        }
    }

    val isError get() = result == "Error"

    /** Live answer shown under the expression while typing (Google / Samsung style). */
    val preview: String
        get() {
            if (evaluated) return ""
            val t = expr.trimEnd('+', '−', '×', '÷', '(')
            if (!hasOp(t)) return ""
            return Engine.evaluate(t)?.let(Engine::format) ?: ""
        }

    private fun hasOp(s: String) = s.drop(1).any { it in "+−×÷%" }

    /** @return false when the key press was rejected (invalid input / error). */
    fun input(k: String): Boolean {
        if (k == "AC") { expr = ""; result = null; evaluated = false; lastValue = null; return true }
        if (k == "=") return equalsKey()

        if (evaluated) {
            val v = lastValue
            when {
                k == "⌫" -> { evaluated = false; result = null }
                k in OPS || k == "%" -> { expr = v?.let(Engine::plain) ?: ""; evaluated = false; result = null }
                else -> { expr = ""; evaluated = false; result = null }
            }
        }
        if (k != "⌫" && expr.length >= 50) return false
        val last = expr.lastOrNull()

        return when {
            k == "⌫" -> if (expr.isEmpty()) false else { expr = expr.dropLast(1); true }
            k in DIGITS -> {
                val cur = expr.takeLastWhile { it.isDigit() || it == '.' }
                when {
                    cur.count { it.isDigit() } >= 15 -> false
                    cur == "0" && k == "0" -> false
                    cur == "0" -> { expr = expr.dropLast(1) + k; true }
                    else -> { expr += k; true }
                }
            }
            k == "." -> {
                val cur = expr.takeLastWhile { it.isDigit() || it == '.' }
                when {
                    cur.contains('.') -> false
                    cur.isEmpty() -> { expr += "0."; true }
                    else -> { expr += "."; true }
                }
            }
            k in OPS -> when {
                expr.isEmpty() || last == '(' -> if (k == "−") { expr += k; true } else false
                last != null && last in OPS_CH -> {
                    if (expr.length == 1 || (expr.length >= 2 && expr[expr.length - 2] == '(')) false
                    else { expr = expr.dropLast(1) + k; true }
                }
                else -> { expr += k; true }
            }
            k == "%" -> if (last != null && (last.isDigit() || last == ')' || last == '%')) { expr += "%"; true } else false
            k == "()" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                expr += if (open > 0 && last != null && (last.isDigit() || last == ')' || last == '%')) ")" else "("
                true
            }
            else -> false
        }
    }

    private fun equalsKey(): Boolean {
        if (evaluated || expr.isEmpty()) return false
        val clean = expr.trimEnd('+', '−', '×', '÷', '(')
        if (clean.isEmpty()) return false
        if (!hasOp(clean)) return true // nothing to calculate
        val v = Engine.evaluate(clean)
        evaluated = true
        if (v == null) { result = "Error"; lastValue = null; return false }
        expr = clean
        result = Engine.format(v)
        lastValue = v
        history.add(0, HistoryItem(clean, Engine.plain(v)))
        while (history.size > 50) history.removeAt(history.lastIndex)
        prefs.edit().putString("history", history.joinToString("\n") { "${it.expr}\t${it.value}" }).apply()
        return true
    }

    fun recall(item: HistoryItem) { expr = item.value; result = null; evaluated = false }

    fun clearHistory() { history.clear(); prefs.edit().remove("history").apply() }

    private companion object {
        val DIGITS = "0123456789".map { it.toString() }.toSet()
        val OPS = setOf("+", "−", "×", "÷")
        const val OPS_CH = "+−×÷"
    }
}
