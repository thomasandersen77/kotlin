/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.annotation

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.codegen.CodegenTestFiles
import org.jetbrains.kotlin.codegen.CodegenTestUtil
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.test.*
import java.io.File
import java.io.StringWriter

abstract class AbstractAnnotationProcessorBoxTest : KotlinTestWithEnvironmentManagement() {
    fun doTest(path: String) {
        val testDir = File(path)

        fun filesByExtension(ext: String) = testDir
                .listFiles { file -> file.isFile && file.extension.equals(ext, ignoreCase = true) }
                .sortedBy { it.name }

        val testName = getTestName(true)
        val ktFiles = filesByExtension("kt")
        val supportInheritedAnnotations = testName.contains("inherited", ignoreCase = true)
        val supportStubs = testName.contains("stubs", ignoreCase = true)

        val javaFiles = filesByExtension("java")
        val javaSourceRoot =
                if (javaFiles.isNotEmpty()) {
                    KotlinTestUtils.tmpDir("java-files").also { javaSourceRoot ->
                        javaFiles.forEach { javaFile -> javaFile.copyTo(File(javaSourceRoot, javaFile.name)) }
                    }
                }
                else null

        val configuration = KotlinTestUtils.newConfiguration(
                ConfigurationKind.ALL, TestJdkKind.MOCK_JDK, listOf(KotlinTestUtils.getAnnotationsJar()), listOfNotNull(javaSourceRoot)
        )
        val environment = KotlinCoreEnvironment.createForTests(testRootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val project = environment.project

        val collectorExtension = AnnotationCollectorExtensionForTests(supportInheritedAnnotations)
        ClassBuilderInterceptorExtension.registerExtension(project, collectorExtension)

        if (supportStubs) {
            val stubsDir = KotlinTestUtils.tmpDir("class-stubs")
            val stubProducerExtension = StubProducerExtension(stubsDir, MessageCollector.NONE, false)
            AnalysisHandlerExtension.registerExtension(project, stubProducerExtension)
        }

        val testFiles = ktFiles.map { KotlinTestUtils.createFile(it.name, KotlinTestUtils.doLoadFile(it), environment.project) }

        CodegenTestUtil.generateFiles(environment, CodegenTestFiles.create(testFiles))

        val actualAnnotations = collectorExtension.stringWriter.toString()
        val expectedAnnotationsFile = File(path + "annotations.txt")

        KotlinTestUtils.assertEqualsToFile(expectedAnnotationsFile, actualAnnotations)
    }

    private class AnnotationCollectorExtensionForTests(
            supportInheritedAnnotations: Boolean
    ) : AnnotationCollectorExtensionBase(supportInheritedAnnotations) {
        val stringWriter = StringWriter()

        override fun getWriter(diagnostic: DiagnosticSink) = stringWriter
        override fun closeWriter() {}

        override val annotationFilterList = listOf<String>()
    }
}
