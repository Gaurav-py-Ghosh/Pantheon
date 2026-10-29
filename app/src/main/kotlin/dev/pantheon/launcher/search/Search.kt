package dev.pantheon.launcher.search

import dev.pantheon.launcher.data.AppEntry
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

enum class Command(val word: String, val hint: String) {
    LS_A("ls -a", "list hidden apps"),
    WIFI("wifi", "wi-fi panel"),
    BT("bt", "bluetooth settings"),
    TORCH("torch", "toggle flashlight"),
    SETTINGS("settings", "android settings"),
    CALC("calc", "calc <expr>"),
    ;

    companion object {
        fun match(query: String): List<Command> {
            if (query.isBlank()) return emptyList()
            return entries.filter { cmd ->
                cmd.word.startsWith(query) ||
                    (cmd == CALC && query.startsWith("calc")) ||
                    (cmd == LS_A && query.startsWith("ls -a"))
            }
        }
    }
}

sealed interface Hit {
    data class App(val entry: AppEntry) : Hit
    data class Cmd(val command: Command) : Hit
    data class Calc(val expr: String, val value: String) : Hit
    data class Web(val query: String) : Hit
}

object Search {
    const val LS_A = "ls -a"

    fun isListHidden(query: String) = query.trim().lowercase().startsWith(LS_A)

    /** [apps] must already include hidden apps when the query starts with `ls -a`. */
    fun run(raw: String, apps: List<AppEntry>, tags: Map<String, String>): List<Hit> {
        val query = raw.trim()
        if (query.isEmpty()) return emptyList()
        val lower = query.lowercase()
        val listHidden = lower.startsWith(LS_A)
        val appQuery = if (listHidden) lower.removePrefix(LS_A).trim() else lower

        val calc = (if (lower.startsWith("calc ")) query.drop(5) else query.takeIf(Calculator::looksLikeMath))
            ?.let { expr -> Calculator.eval(expr)?.let { Hit.Calc(expr.trim(), Calculator.format(it)) } }

        val scored = if (appQuery.isEmpty()) {
            if (listHidden) apps.map { it to 0 } else emptyList()
        } else {
            apps.mapNotNull { e -> score(appQuery, e, tags[e.key].orEmpty())?.let { e to it } }
                .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenBy { it.first.label.lowercase() })
        }
        val (strong, weak) = scored.partition { it.second >= 90 }

        return buildList {
            calc?.let(::add)
            strong.forEach { add(Hit.App(it.first)) }
            Command.match(lower).forEach { add(Hit.Cmd(it)) }
            weak.forEach { add(Hit.App(it.first)) }
            if (!listHidden && calc == null) add(Hit.Web(query))
        }
    }

    private fun score(q: String, entry: AppEntry, tag: String): Int? {
        val label = entry.label.lowercase()
        return when {
            tag == q -> 100
            label == q -> 95
            label.startsWith(q) -> 90
            label.split(' ', '-', '.').any { it.startsWith(q) } -> 80
            tag.startsWith(q) -> 75
            label.contains(q) -> 60
            isSubsequence(q, label) -> 40
            else -> null
        }
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var i = 0
        for (c in s) if (i < q.length && c == q[i]) i++
        return i == q.length
    }
}

object Calculator {
    private val mathChars = Regex("^[\\d\\s.+\\-*/%^()x×÷]+$")

    fun looksLikeMath(s: String) = mathChars.matches(s) && s.any(Char::isDigit) && s.any { it in "+-*/%^x×÷" }

    fun eval(s: String): Double? = runCatching {
        Parser(s.replace(" ", "").replace('x', '*').replace('×', '*').replace('÷', '/')).parse()
    }.getOrNull()?.takeIf { it.isFinite() }

    fun format(d: Double): String =
        if (d == floor(d) && abs(d) < 1e15) d.toLong().toString()
        else String.format(Locale.US, "%.8f", d).trimEnd('0').trimEnd('.')

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            require(i == s.length)
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (i < s.length) {
                when (s[i]) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (i < s.length) {
                when (s[i]) {
                    '*' -> { i++; v *= power() }
                    '/' -> { i++; v /= power() }
                    '%' -> { i++; v %= power() }
                    else -> return v
                }
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double = when {
            i < s.length && s[i] == '-' -> { i++; -unary() }
            i < s.length && s[i] == '+' -> { i++; unary() }
            else -> atom()
        }

        private fun atom(): Double {
            if (s[i] == '(') {
                i++
                val v = expr()
                require(s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }
}
