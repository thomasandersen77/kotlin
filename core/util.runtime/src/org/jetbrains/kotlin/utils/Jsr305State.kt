/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.utils

enum class ReportLevel(val description: String) {
    IGNORE("ignore"),
    WARN("warn"),
    STRICT("strict"),
    ;

    companion object {
        fun findByDescription(description: String?): ReportLevel? = values().firstOrNull { it.description == description }
    }

    fun isWarning(): Boolean = this == ReportLevel.WARN
    fun isIgnored(): Boolean = this == ReportLevel.IGNORE
}

data class Jsr305State(
        val global: ReportLevel,
        val migration: ReportLevel?,
        val user: Map<String, ReportLevel>
) {
    val description: Array<String> by lazy {
        val result = mutableListOf<String>()
        result.add(global.description)

        migration?.let { result.add("under-migration:${it.description}") }

        user.forEach {
            result.add("@${it.key}:${it.value.description}")
        }

        result.toTypedArray()
    }

    fun isIgnored(): Boolean = this == IGNORE_ALL

    companion object {
        @JvmField
        val DEFAULT: Jsr305State = Jsr305State(ReportLevel.WARN, null, emptyMap())

        val IGNORE_ALL: Jsr305State = Jsr305State(ReportLevel.IGNORE, ReportLevel.IGNORE, emptyMap())
        val STRICT: Jsr305State = Jsr305State(ReportLevel.STRICT, ReportLevel.STRICT, emptyMap())

        fun fromArgs(args: Array<String>?): Jsr305State {
            var global: ReportLevel = ReportLevel.WARN
            var migration: ReportLevel? = null
            val userDefined = mutableMapOf<String, ReportLevel>()

            args?.forEach { item ->
                when {
                    item.startsWith("@") -> {
                        val (name, rawState) = item.substring(1).split(":").takeIf { it.size == 2 } ?: return@forEach
                        val state = ReportLevel.findByDescription(rawState) ?: return@forEach
                        userDefined[name] = state
                    }
                    item.startsWith("under-migration") -> {
                        val (_, rawState) = item.split(":").takeIf { it.size == 2 } ?: return@forEach
                        val state = ReportLevel.findByDescription(rawState) ?: return@forEach
                        migration = state
                    }
                    else -> {
                        global = ReportLevel.findByDescription(item) ?: return@forEach
                    }
                }
            }

            return Jsr305State(global, migration, userDefined)
        }

    }
}

