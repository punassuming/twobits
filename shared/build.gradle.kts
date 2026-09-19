import io.gitlab.arturbosch.detekt.extensions.DetektExtension

/**
 * Root build file for the composite `shared` build. It exists only to apply static analysis.
 *
 * Until now `shared/` was unlinted: ktlint and detekt are configured in each app's `subprojects {}`
 * block, and an included build is not a subproject of anything, so code moved out of an app into
 * `shared/` silently left the checks behind. That is the wrong direction for the one place three
 * apps depend on — a defect here reaches all three.
 *
 * detekt only, for now. ktlint follows separately: `shared/` accumulated real formatting drift while
 * unchecked, and mixing ~160 formatting fixes into the same commit as the wiring would make both
 * unreviewable.
 */
plugins {
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        // One config for the whole repo, at its root. The three apps each kept a byte-identical
        // copy of this file; a fourth here would have been the same mistake a fourth time, and the
        // pre-commit hook already assumed a single config by running every app's staged files
        // through Scrybe's.
        config.setFrom(rootProject.file("../detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
        parallel = true
    }
}

tasks.register("detekt") {
    description = "Runs detekt across all shared modules."
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:detekt" })
}
