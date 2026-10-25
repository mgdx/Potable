// Root file: declares the plugins without applying them; each module applies
// the ones it needs. `base` gives the root a `check` task, so that
// `./gradlew check` also runs the RTL and translation guards below.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

/**
 * Fails when a source file uses a physical direction (left/right) where a
 * relative one (start/end) is required, so that every screen mirrors in RTL
 * languages (SPEC §11.2). Android lint's own RTL checks only cover XML layouts
 * and are warnings; Compose code is not covered at all.
 */
abstract class CheckRtlTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val root = rootDirectory.get().asFile
        val violations = mutableListOf<String>()
        kotlinSources.files.sorted().forEach { file ->
            kotlinViolations(file.readText()).forEach { (line, what) ->
                violations += "${file.relativeTo(root).path}:$line: $what"
            }
        }
        resourceFiles.files.sorted().forEach { file ->
            lineViolations(file.readText(), xmlRules).forEach { (line, what) ->
                violations += "${file.relativeTo(root).path}:$line: $what"
            }
        }
        val output = report.get().asFile
        output.parentFile.mkdirs()
        output.writeText(violations.joinToString("\n", postfix = "\n"))
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkRtl: ${violations.size} physical direction(s) found; use start/end " +
                    "instead (SPEC §11.2):\n" + violations.joinToString("\n") { "  $it" },
            )
        }
    }

    private val kotlinRules = listOf(
        Regex("""\babsolutePadding\b""") to "absolutePadding: use padding(start/end)",
        Regex("""\bAbsoluteAlignment\b""") to "AbsoluteAlignment: use Alignment",
        Regex("""\bAlignment\.\w*(Left|Right)\b""") to "Alignment.*Left/Right: use Start/End",
        Regex("""\bTextAlign\.(Left|Right)\b""") to "TextAlign.Left/Right: use Start/End",
        Regex("""\bArrangement\.Absolute\b""") to "Arrangement.Absolute: use Arrangement",
    )

    private val xmlRules = listOf(
        Regex("""\blayout_margin(Left|Right)\b""") to "layout_marginLeft/Right: use Start/End",
        Regex("""\bpadding(Left|Right)\b""") to "paddingLeft/Right: use Start/End",
        Regex("""gravity\s*=\s*"[^"]*\b(left|right)\b""") to "gravity left/right: use start/end",
    )

    private val directionalCall = Regex("""\b(padding|offset)\s*\(""")
    private val namedSide = Regex("""\b(left|right)\s*=(?!=)""")

    private fun lineViolations(text: String, rules: List<Pair<Regex, String>>) =
        rules.flatMap { (regex, what) ->
            regex.findAll(text).map { lineOf(text, it.range.first) to what }
        }

    private fun kotlinViolations(text: String): List<Pair<Int, String>> {
        val found = lineViolations(text, kotlinRules).toMutableList()
        // A named `left =`/`right =` argument at the top level of a
        // padding(...) or offset(...) call, which may span several lines.
        directionalCall.findAll(text).forEach { call ->
            var depth = 1
            var index = call.range.last + 1
            val topLevel = StringBuilder()
            while (index < text.length && depth > 0) {
                val c = text[index]
                if (c == '(') depth++
                if (c == ')') depth--
                topLevel.append(if (depth == 1 || (depth == 0 && c == ')')) c else ' ')
                index++
            }
            namedSide.findAll(topLevel).forEach { arg ->
                found += lineOf(text, call.range.last + 1 + arg.range.first) to
                    "${call.groupValues[1]}(${arg.groupValues[1]} = ...): use start/end"
            }
        }
        return found.sortedBy { it.first }
    }

    private fun lineOf(text: String, offset: Int) = text.substring(0, offset).count { it == '\n' } + 1
}

val checkRtl by tasks.registering(CheckRtlTask::class) {
    group = "verification"
    description = "Fails on left/right where start/end is required (SPEC §11.2)."
    rootDirectory.set(layout.projectDirectory)
    kotlinSources.from(
        fileTree(layout.projectDirectory) {
            include("*/src/main/**/*.kt")
            exclude("**/build/**")
        },
    )
    resourceFiles.from(
        fileTree(layout.projectDirectory) {
            include("*/src/*/res/**/*.xml")
            exclude("**/build/**")
        },
    )
    report.set(layout.buildDirectory.file("reports/checkRtl.txt"))
}

/**
 * Fails when a translation misses a string, has one English does not, or
 * changes a format parameter (SPEC §11, §16 criterion 10). The checks are
 * those of `tools/check_translations` (Python 3, standard library), which
 * prints one line per problem; see tools/README.md.
 */
val checkTranslations by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the French translation against the English strings (SPEC §11)."
    val root = layout.projectDirectory.asFile
    workingDir = root
    commandLine("python3", "tools/check_translations", "--root", root.absolutePath)
    inputs.file("tools/check_translations")
    inputs.files(
        fileTree(layout.projectDirectory) {
            include("app/src/main/res/values*/strings.xml")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named("check") {
    dependsOn(checkRtl, checkTranslations)
}
