package com.behnamjalali.planb.core.model.rich

/**
 * A formula (Plan-B Pro #23) parsed from a LaTeX-like source. The parser knows the common
 * constructs — `\frac`, `^`, `_`, `\sqrt[n]{…}`, Greek letters, `\sum`/`\prod`/`\int` with
 * limits, `\left( … \right)`, `matrix`/`pmatrix`/`bmatrix`/`vmatrix`, `\text{…}`, functions
 * and common symbols — and never fails: anything it does not know is kept as text.
 */
sealed interface MathNode

data class MathRow(val items: List<MathNode>) : MathNode

data class MathText(val text: String, val kind: MathTextKind = MathTextKind.VARIABLE) : MathNode

enum class MathTextKind { VARIABLE, NUMBER, OPERATOR, FUNCTION, TEXT }

data class MathFraction(val numerator: MathNode, val denominator: MathNode) : MathNode

data class MathScripts(val base: MathNode, val sub: MathNode? = null, val sup: MathNode? = null) : MathNode

data class MathRoot(val radicand: MathNode, val index: MathNode? = null) : MathNode

/** ∑, ∏, ∫ and friends; limits are drawn below/above (sums) or beside (integrals). */
data class MathBigOperator(val symbol: String, val lower: MathNode? = null, val upper: MathNode? = null) : MathNode {
    val limitsBeside: Boolean get() = symbol in INTEGRALS

    companion object {
        val INTEGRALS = setOf("∫", "∬", "∭", "∮")
    }
}

data class MathFenced(val open: String, val close: String, val content: MathNode) : MathNode

data class MathMatrix(val rows: List<List<MathNode>>, val open: String = "", val close: String = "") : MathNode

object MathParser {
    fun parse(source: String): MathNode = Parser(tokenize(source)).parseAll()

    private sealed interface Token {
        data class Command(val name: String) : Token
        data class Char(val c: kotlin.Char) : Token
    }

    private fun tokenize(source: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '\\' && i + 1 < source.length) {
                val start = i + 1
                if (source[start].isLetter()) {
                    var end = start
                    while (end < source.length && source[end].isLetter() && source[end].code < 128) end++
                    if (end == start) end = start + 1
                    out += Token.Command(source.substring(start, end))
                    i = end
                } else {
                    out += Token.Command(source[start].toString())
                    i = start + 1
                }
                continue
            }
            out += Token.Char(c)
            i++
        }
        return out
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parseAll(): MathNode {
            val items = mutableListOf<MathNode>()
            while (pos < tokens.size) {
                items += parseRow(stopAtBrace = false).items
                // A stray closing brace or delimiter: keep it as text and go on.
                if (pos < tokens.size) items += MathText(describe(tokens[pos++]), MathTextKind.OPERATOR)
            }
            return simplify(MathRow(items))
        }

        private fun peek(): Token? = tokens.getOrNull(pos)

        private fun isStop(t: Token, stopAtBrace: Boolean, inMatrix: Boolean): Boolean = when (t) {
            is Token.Char -> (stopAtBrace && t.c == '}') || (inMatrix && t.c == '&')
            is Token.Command -> t.name == "right" || t.name == "end" || (inMatrix && t.name == "\\")
        }

        fun parseRow(stopAtBrace: Boolean, inMatrix: Boolean = false): MathRow {
            val items = mutableListOf<MathNode>()
            while (true) {
                val t = peek() ?: break
                if (isStop(t, stopAtBrace, inMatrix)) break
                if (t is Token.Char && t.c == '}' && !stopAtBrace) break
                if (t is Token.Char && t.c.isWhitespace()) { pos++; continue }
                if (t is Token.Command && t.name == "\\") { pos++; continue }
                val atom = parseAtom() ?: continue
                items += withScripts(atom)
            }
            return MathRow(items)
        }

        private fun withScripts(atom: MathNode): MathNode {
            var sub: MathNode? = null
            var sup: MathNode? = null
            while (true) {
                skipSpaces()
                val t = peek() as? Token.Char ?: break
                when (t.c) {
                    '^' -> { pos++; sup = parseArg() }
                    '_' -> { pos++; sub = parseArg() }
                    '\'' -> { pos++; sup = MathText("′", MathTextKind.OPERATOR) }
                    else -> break
                }
            }
            if (sub == null && sup == null) return atom
            return if (atom is MathBigOperator) atom.copy(lower = sub ?: atom.lower, upper = sup ?: atom.upper) else MathScripts(atom, sub, sup)
        }

        private fun skipSpaces() {
            while ((peek() as? Token.Char)?.c?.isWhitespace() == true) pos++
        }

        /** A braced group or a single atom (without scripts). */
        private fun parseArg(): MathNode {
            skipSpaces()
            val t = peek() ?: return MathRow(emptyList())
            if (t is Token.Char && t.c == '{') {
                pos++
                val row = parseRow(stopAtBrace = true)
                if ((peek() as? Token.Char)?.c == '}') pos++
                return simplify(row)
            }
            return parseAtom() ?: MathRow(emptyList())
        }

        private fun rawGroup(): String {
            skipSpaces()
            if ((peek() as? Token.Char)?.c != '{') return ""
            pos++
            val sb = StringBuilder()
            var depth = 1
            while (pos < tokens.size) {
                val t = tokens[pos++]
                if (t is Token.Char && t.c == '{') depth++
                if (t is Token.Char && t.c == '}' && --depth == 0) break
                sb.append(if (t is Token.Char) t.c else "\\" + (t as Token.Command).name)
            }
            return sb.toString()
        }

        private fun parseAtom(): MathNode? {
            val t = peek() ?: return null
            pos++
            return when (t) {
                is Token.Char -> when {
                    t.c == '{' -> simplify(parseRow(stopAtBrace = true).also { if ((peek() as? Token.Char)?.c == '}') pos++ })
                    t.c.isDigit() || t.c == '.' -> {
                        val sb = StringBuilder().append(t.c)
                        while ((peek() as? Token.Char)?.c?.let { it.isDigit() || it == '.' } == true) sb.append((tokens[pos++] as Token.Char).c)
                        MathText(sb.toString(), MathTextKind.NUMBER)
                    }
                    t.c.isLetter() -> MathText(t.c.toString(), MathTextKind.VARIABLE)
                    t.c == '^' || t.c == '_' -> withScripts(MathText("", MathTextKind.TEXT).also { pos-- })
                    else -> MathText(OPERATOR_CHARS[t.c] ?: t.c.toString(), MathTextKind.OPERATOR)
                }
                is Token.Command -> command(t.name)
            }
        }

        private fun command(name: String): MathNode? = when (name) {
            "frac", "dfrac", "tfrac", "cfrac" -> MathFraction(parseArg(), parseArg())
            "binom" -> MathFenced("(", ")", MathMatrix(listOf(listOf(parseArg()), listOf(parseArg()))))
            "sqrt" -> {
                skipSpaces()
                val index = if ((peek() as? Token.Char)?.c == '[') {
                    pos++
                    val items = mutableListOf<MathNode>()
                    while (pos < tokens.size && (peek() as? Token.Char)?.c != ']') parseAtom()?.let { items += withScripts(it) }
                    if (pos < tokens.size) pos++
                    simplify(MathRow(items))
                } else {
                    null
                }
                MathRoot(parseArg(), index)
            }
            "text", "textrm", "mathrm", "operatorname", "mbox", "textbf", "mathbf", "mathit" -> MathText(rawGroup(), MathTextKind.TEXT)
            "left" -> {
                val open = delimiter()
                val content = parseRow(stopAtBrace = false)
                var close = ""
                if ((peek() as? Token.Command)?.name == "right") {
                    pos++
                    close = delimiter()
                }
                MathFenced(open, close, simplify(content))
            }
            "right" -> null
            "begin" -> matrix(rawGroup())
            "end" -> { rawGroup(); null }
            ",", ";", ":", "quad", "qquad", " " -> MathText(" ", MathTextKind.TEXT)
            "!" -> null
            "{", "}", "%", "$", "#", "&", "_" -> MathText(name, MathTextKind.OPERATOR)
            "|" -> MathText("‖", MathTextKind.OPERATOR)
            else -> GREEK[name]?.let { MathText(it, MathTextKind.VARIABLE) }
                ?: BIG_OPERATORS[name]?.let { MathBigOperator(it) }
                ?: SYMBOLS[name]?.let { MathText(it, MathTextKind.OPERATOR) }
                ?: name.takeIf { it in FUNCTIONS }?.let { MathText(it, MathTextKind.FUNCTION) }
                ?: MathText(name, MathTextKind.TEXT)
        }

        private fun delimiter(): String {
            skipSpaces()
            return when (val t = tokens.getOrNull(pos++)) {
                is Token.Char -> if (t.c == '.') "" else t.c.toString()
                is Token.Command -> when (t.name) {
                    "{" -> "{"
                    "}" -> "}"
                    "|" -> "‖"
                    "langle" -> "⟨"
                    "rangle" -> "⟩"
                    "lfloor" -> "⌊"
                    "rfloor" -> "⌋"
                    "lceil" -> "⌈"
                    "rceil" -> "⌉"
                    else -> ""
                }
                null -> ""
            }
        }

        private fun matrix(env: String): MathNode {
            val rows = mutableListOf<List<MathNode>>()
            var row = mutableListOf<MathNode>()
            while (pos < tokens.size) {
                row += simplify(parseRow(stopAtBrace = false, inMatrix = true))
                when (val t = peek()) {
                    is Token.Char -> if (t.c == '&') pos++ else if (t.c == '}') pos++ else break
                    is Token.Command -> when (t.name) {
                        "\\" -> { pos++; rows += row; row = mutableListOf() }
                        "end" -> { pos++; rawGroup(); break }
                        else -> { pos++; break }
                    }
                    null -> break
                }
                if (rows.size >= MAX_MATRIX || row.size >= MAX_MATRIX) break
            }
            if (row.any { !(it is MathRow && it.items.isEmpty()) }) rows += row
            val (open, close) = when (env.trim()) {
                "pmatrix" -> "(" to ")"
                "bmatrix" -> "[" to "]"
                "Bmatrix" -> "{" to "}"
                "vmatrix" -> "|" to "|"
                "Vmatrix" -> "‖" to "‖"
                "cases" -> "{" to ""
                else -> "" to ""
            }
            return MathMatrix(rows, open, close)
        }

        private fun describe(t: Token): String = when (t) {
            is Token.Char -> t.c.toString()
            is Token.Command -> "\\" + t.name
        }
    }

    private fun simplify(node: MathNode): MathNode = if (node is MathRow && node.items.size == 1) node.items[0] else node

    private const val MAX_MATRIX = 20

    private val OPERATOR_CHARS = mapOf('-' to "−", '*' to "∗")

    private val GREEK = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ",
        "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ",
        "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )

    private val BIG_OPERATORS = mapOf(
        "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "bigcup" to "⋃", "bigcap" to "⋂",
    )

    private val SYMBOLS = mapOf(
        "infty" to "∞", "pm" to "±", "mp" to "∓", "times" to "×", "cdot" to "·", "div" to "÷", "ast" to "∗",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈",
        "equiv" to "≡", "sim" to "∼", "propto" to "∝", "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "leftrightarrow" to "↔", "Leftrightarrow" to "⇔", "implies" to "⇒",
        "partial" to "∂", "nabla" to "∇", "forall" to "∀", "exists" to "∃", "in" to "∈", "notin" to "∉",
        "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
        "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱", "angle" to "∠",
        "degree" to "°", "circ" to "∘", "perp" to "⊥", "parallel" to "∥", "neg" to "¬", "land" to "∧", "lor" to "∨",
        "langle" to "⟨", "rangle" to "⟩", "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ",
        "mid" to "|", "prime" to "′", "star" to "⋆", "bullet" to "∙", "lbrace" to "{", "rbrace" to "}",
    )

    private val FUNCTIONS = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
        "log", "ln", "lg", "exp", "lim", "max", "min", "sup", "inf", "det", "gcd", "deg", "dim", "arg", "mod", "Pr",
    )
}

/** Words for reading a formula aloud (TalkBack); the UI passes localized ones. */
data class MathWords(
    val over: String = "over",
    val power: String = "to the power",
    val sub: String = "sub",
    val squareRoot: String = "square root of",
    val root: String = "root",
    val of: String = "of",
    val from: String = "from",
    val to: String = "to",
    val matrix: String = "matrix",
    val row: String = "row",
)

object MathSpeech {
    fun describe(node: MathNode, words: MathWords = MathWords()): String = speak(node, words).replace(Regex("\\s+"), " ").trim()

    private fun speak(node: MathNode, w: MathWords): String = when (node) {
        is MathRow -> node.items.joinToString(" ") { speak(it, w) }
        is MathText -> node.text
        is MathFraction -> "${speak(node.numerator, w)} ${w.over} ${speak(node.denominator, w)}"
        is MathScripts -> buildString {
            append(speak(node.base, w))
            node.sub?.let { append(' ').append(w.sub).append(' ').append(speak(it, w)) }
            node.sup?.let { append(' ').append(w.power).append(' ').append(speak(it, w)) }
        }
        is MathRoot -> node.index?.let { "${speak(it, w)} ${w.root} ${w.of} ${speak(node.radicand, w)}" } ?: "${w.squareRoot} ${speak(node.radicand, w)}"
        is MathBigOperator -> buildString {
            append(node.symbol)
            node.lower?.let { append(' ').append(w.from).append(' ').append(speak(it, w)) }
            node.upper?.let { append(' ').append(w.to).append(' ').append(speak(it, w)) }
        }
        is MathFenced -> "${node.open} ${speak(node.content, w)} ${node.close}"
        is MathMatrix -> "${w.matrix}: " + node.rows.joinToString("; ") { r -> r.joinToString(", ") { speak(it, w) } }
    }
}
