/**
 * Root build file for the composite `shared` build. It exists only to aggregate static analysis.
 *
 * Until recently `shared/` was unlinted: ktlint and detekt are configured in each app's
 * `subprojects {}` block, and an included build is not a subproject of anything, so code moved out
 * of an app into `shared/` silently left the checks behind. That is the wrong direction for the one
 * place three apps depend on — a defect here reaches all three.
 *
 * Note what this file does *not* do. Applying detekt from here via `subprojects { apply(...) }` —
 * the way each app does it — fails the whole build with
 * `NoClassDefFoundError: com/android/build/gradle/BaseExtension`. The apps get away with it because
 * their root build file also puts the Android plugin on the root's buildscript classpath, so detekt
 * and AGP share one classloader. Here each module declares its own plugin versions inline, leaving
 * the root classpath without AGP, and detekt's Android source-set integration cannot resolve it.
 * So each module applies and configures detekt itself, next to the AGP declaration it needs.
 */
tasks.register("detekt") {
    description = "Runs detekt across all shared modules."
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:detekt" })
}

tasks.register("ktlintCheck") {
    description = "Runs ktlint checks across all shared modules."
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
}

tasks.register("ktlintFormat") {
    description = "Formats Kotlin sources across all shared modules."
    group = "formatting"
    dependsOn(subprojects.map { "${it.path}:ktlintFormat" })
}
