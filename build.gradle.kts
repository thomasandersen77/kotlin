import org.gradle.api.Project
import java.util.*
import java.io.File
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

buildscript {
    val repos = listOf(
            System.getProperty("bootstrap.kotlin.repo"),
            "https://repo.gradle.org/gradle/repo",
            "https://plugins.gradle.org/m2",
            "http://repository.jetbrains.com/utils/").filterNotNull()

    extra["kotlin_version"] = bootstrapKotlinVersion
    extra["kotlinVersion"] = bootstrapKotlinVersion
    extra["kotlin_language_version"] = "1.1"
    extra["repos"] = repos

    extra["versions.shadow"] = "2.0.1"

    repositories {
        for (repo in repos) {
            maven { setUrl(repo) }
        }
    }

    dependencies {
        classpath("com.gradle.publish:plugin-publish-plugin:0.9.7")
        classpath(kotlinDep("gradle-plugin"))
    }
}

plugins {
    `build-scan`
}

buildScan {
    setLicenseAgreementUrl("https://gradle.com/terms-of-service")
    setLicenseAgree("yes")
}

val configuredJdks: List<JdkId> =
        getConfiguredJdks().also {
            it.forEach {
                logger.info("Using ${it.majorVersion} home: ${it.homeDir}")
            }
        }

val buildNumber = "1.1-SNAPSHOT"
extra["build.number"] = buildNumber

extra["kotlin_root"] = rootDir

val bootstrapCompileCfg = configurations.create("bootstrapCompile")
val scriptCompileCfg = configurations.create("scriptCompile").extendsFrom(bootstrapCompileCfg)
val scriptRuntimeCfg = configurations.create("scriptRuntime").extendsFrom(scriptCompileCfg)

repositories {
    for (repo in (rootProject.extra["repos"] as List<String>)) {
        maven { setUrl(repo) }
    }
}

dependencies {
    bootstrapCompileCfg(kotlinDep("compiler-embeddable"))
}

val commonBuildDir = File(rootDir, "build")
val distDir = "$rootDir/dist"
val distKotlinHomeDir = "$distDir/kotlinc"
val distLibDir = "$distKotlinHomeDir/lib"
val ideaPluginDir = "$distDir/artifacts/Kotlin"
val ideaUltimatePluginDir = "$distDir/artifacts/KotlinUltimate"

extra["distDir"] = distDir
extra["distKotlinHomeDir"] = distKotlinHomeDir
extra["distLibDir"] = project.file(distLibDir)
extra["libsDir"] = project.file(distLibDir)
extra["ideaPluginDir"] = project.file(ideaPluginDir)
extra["ideaUltimatePluginDir"] = project.file(ideaUltimatePluginDir)
extra["isSonatypeRelease"] = false

Properties().apply {
    load(File(rootDir, "resources", "kotlinManifest.properties").reader())
    forEach {
        val key = it.key
        if (key != null && key is String)
            extra[key] = it.value
    }
}

extra["JDK_16"] = jdkPath("1.6")
extra["JDK_17"] = jdkPath("1.7")
extra["JDK_18"] = jdkPath("1.8")

extra["versions.protobuf-java"] = "2.6.1"
extra["versions.javax.inject"] = "1"
extra["versions.jsr305"] = "1.3.9"
extra["versions.cli-parser"] = "1.1.2"
extra["versions.jansi"] = "1.11"
extra["versions.jline"] = "3.3.1"
extra["versions.junit"] = "4.12"
extra["versions.javaslang"] = "2.0.6"
extra["versions.ant"] = "1.8.2"

extra["ideaCoreSdkJars"] = arrayOf("annotations", "asm-all", "guava", "intellij-core", "jdom", "jna", "log4j", "picocontainer",
                                   "snappy-in-java", "streamex", "trove4j", "xpp3-1.1.4-min", "xstream")

extra["compilerModules"] = arrayOf(":compiler:util",
                                   ":compiler:container",
                                   ":compiler:conditional-preprocessor",
                                   ":compiler:resolution",
                                   ":compiler:serialization",
                                   ":compiler:frontend",
                                   ":compiler:frontend.java",
                                   ":compiler:frontend.script",
                                   ":compiler:cli-common",
                                   ":compiler:daemon-common",
                                   ":compiler:daemon",
                                   ":compiler:ir.tree",
                                   ":compiler:ir.psi2ir",
                                   ":compiler:backend-common",
                                   ":compiler:backend",
                                   ":compiler:plugin-api",
                                   ":compiler:light-classes",
                                   ":compiler:cli",
                                   ":compiler:incremental-compilation-impl",
                                   ":js:js.ast",
                                   ":js:js.serializer",
                                   ":js:js.parser",
                                   ":js:js.frontend",
                                   ":js:js.translator",
                                   ":js:js.dce",
                                   ":compiler",
                                   ":kotlin-build-common",
                                   ":core:util.runtime",
                                   ":core")

allprojects {
    group = "org.jetbrains.kotlin"
    version = buildNumber
}

apply {
    from("libraries/commonConfiguration.gradle")
    from("libraries/gradlePluginsConfiguration.gradle")
    from("libraries/configureGradleTools.gradle")

    if (extra["isSonatypeRelease"] as? Boolean == true) {
        logger.info("Applying configuration for sonatype release")
        from("libraries/prepareSonatypeStaging.gradle")
    }
}

val importedAntTasksPrefix = "imported-ant-update-"

// TODO: check the reasons of import conflict with xerces
//ant.importBuild("$rootDir/update_dependencies.xml") { antTaskName -> importedAntTasksPrefix + antTaskName }

tasks.matching { task ->
    task.name.startsWith(importedAntTasksPrefix)
}.forEach {
    it.group = "Imported ant"
}

//task("update-dependencies") {
//    dependsOn(tasks.getByName(importedAntTasksPrefix + "update"))
//}

fun Project.allprojectsRecursive(body: Project.() -> Unit) {
    this.body()
    this.subprojects { allprojectsRecursive(body) }
}

val defaultJvmTarget = "1.8"
val defaultJavaHome = jdkPath(defaultJvmTarget!!)

allprojects {

    jvmTarget = defaultJvmTarget
    javaHome = defaultJavaHome

    // There are problems with common build dir:
    //  - some tests (in particular js and binary-compatibility-validator depend on the fixed (default) location
    //  - idea siims unable to exclude common builddir from indexing
    // therefore it is disabled by default
//    buildDir = File(commonBuildDir, project.name)

    repositories {
        for (repo in (rootProject.extra["repos"] as List<String>)) {
            maven { setUrl(repo) }
        }
        mavenCentral()
        jcenter()
    }
    configureJvmProject(javaHome!!, jvmTarget!!)

    tasks.withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs = listOf("-Xallow-kotlin-package")
    }

    tasks.withType<Kotlin2JsCompile> {
        kotlinOptions.freeCompilerArgs = listOf("-Xallow-kotlin-package")
    }

    tasks.withType<Javadoc> {
        enabled = false
    }

    task<Jar>("javadocJar") {
        classifier = "javadoc"
    }

    tasks.withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    afterEvaluate {
        logger.info("configuring project $name to compile to the target jvm version $jvmTarget using jdk: $javaHome")
        if (javaHome != defaultJavaHome || jvmTarget != defaultJvmTarget) {
            configureJvmProject(javaHome!!, jvmTarget!!)
        }
    }
}

task<Copy>("dist") {
    into(distDir)
    from(files("compiler/cli/bin")) { into("kotlinc/bin") }
    from(files("license")) { into("kotlinc/license") }
}

val compilerCopyTask = task<Copy>("idea-plugin-copy-compiler") {
    dependsOnTaskIfExistsRec("dist")
    into(ideaPluginDir)
    from(distDir) { include("kotlinc/**") }
}

task<Copy>("dist-plugin") {
    dependsOn(compilerCopyTask)
    dependsOnTaskIfExistsRec("idea-plugin")
    shouldRunAfter(":prepare:kotlin-plugin:idea-plugin")
    into("$ideaPluginDir/lib")
}

tasks {
    "clean" {
        doLast {
            delete("$buildDir/repo")
            delete(distDir)
        }
    }

    "core-libs-tests" {
        dependsOn(":kotlin-test:kotlin-test-common:test",
                  ":kotlin-test:kotlin-test-jvm:test",
                  ":kotlin-test:kotlin-test-junit:test",
                  ":kotlin-stdlib:test",
                  ":kotlin-stdlib-jre7:test",
                  ":kotlin-stdlib-jre8:test",
                  ":tools:binary-compatibility-validator:test",
                  ":kotlin-reflect:test")
    }
    "core-libs-check" { dependsOn("core-libs-tests") }

    "compiler-tests" {
        afterEvaluate {
            dependsOn("dist")
        }
        dependsOn(":compiler:test",
                  ":compiler:container:test",
                  ":compiler:tests-java8:test")
    }
    "compiler-check" { dependsOn("compiler-tests") }

    "js-tests" {
        dependsOn("dist")
        dependsOn(
                ":kotlin-test:kotlin-test-js:test",
                ":kotlin-stdlib-js:test",
                ":js:js.tests:test")
    }
    "js-check" { dependsOn("js-tests") }

    "jps-tests" {
        dependsOn("dist")
        dependsOn(":jps-plugin:test")
    }
    "check" { dependsOn("test") }

    "idea-plugin-main-tests" {
        dependsOn("dist")
        dependsOn(":idea:test")
    }
    "idea-plugin-main-check" { dependsOn("idea-plugin-main-tests") }

    "idea-plugin-additional-tests" {
        dependsOn("dist")
        dependsOn(":idea:idea-gradle:test",
                  ":idea:idea-maven:test",
                  ":j2k:test",
                  ":eval4j:test")
    }
    "idea-plugin-additional-check" { dependsOn("idea-plugin-additional-tests") }

    "idea-plugin-tests" {
        dependsOn("dist")
        dependsOn("idea-plugin-main-tests",
                  "idea-plugin-additional-tests")
    }
    "idea-plugin-check" { dependsOn("idea-plugin-tests") }

    "android-tests" {
        dependsOn("dist")
        dependsOn(":plugins:android-extensions-ide:test",
                  ":idea:idea-android:test",
                  ":kotlin-annotation-processing:test",
                  ":compiler:android-tests:test")
    }
    "android-check" { dependsOn("android-tests") }

    "plugins-tests" {
        dependsOn("dist")
        dependsOn(":plugins:plugins-tests:test",
                  ":kotlin-annotation-processing:test",
                  ":kotlin-source-sections-compiler-plugin:test",
                  ":plugins:uast-kotlin:test",
                  ":kotlin-annotation-processing-gradle:test")
    }
    "plugins-check" { dependsOn("plugins-tests") }

    "scripting-tests" {
        dependsOn("dist")
        dependsOn(":kotlin-script-util:test",
                  ":examples:kotlin-jsr223-local-example:test",
                  ":examples:kotlin-jsr223-daemon-local-eval-example:test")
    }
    "scripting-check" { dependsOn("scripting-tests") }

    "other-tests" {
        dependsOn("dist")
        dependsOn(":kotlin-build-common:test",
                  ":generators:test")
    }
    "other-check" { dependsOn("other-tests") }

    "test" {
        doLast {
            throw GradleException("Don't use directly, use aggregate tasks *-check instead")
        }
    }
    "check" { dependsOn("test") }
}

fun jdkPath(version: String): String {
    val jdkName = "JDK_${version.replace(".", "")}"
    val jdkMajorVersion = JdkMajorVersion.valueOf(jdkName)
    return configuredJdks.find { it.majorVersion == jdkMajorVersion }?.homeDir?.canonicalPath
        ?: throw GradleException ("Please set environment variable $jdkName to point to JDK $version installation")
}

fun Project.configureJvmProject(javaHome: String, javaVersion: String) {

    tasks.withType<JavaCompile> {
        options.forkOptions.javaHome = file(javaHome)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jdkHome = javaHome
        kotlinOptions.jvmTarget = javaVersion
        doFirst {
            System.setProperty("kotlin.colors.enabled", "false")
        }
    }

    tasks.withType<Test> {
        executable = File(javaHome, "bin/java").canonicalPath
    }
}
