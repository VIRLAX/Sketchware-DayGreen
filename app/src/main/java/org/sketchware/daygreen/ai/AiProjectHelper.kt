package org.sketchware.daygreen.ai

import a.a.a.jC
import a.a.a.wq
import android.util.Log
import android.util.Pair
import com.besome.sketch.beans.ComponentBean
import com.besome.sketch.beans.ProjectFileBean
import pro.sketchware.utility.FileUtil
import java.io.File
import java.util.Locale

object AiProjectHelper {

    private const val TAG = "AiProjectHelper"
    private const val MAX_READ_CHARS = 25000

    data class Result(val success: Boolean, val message: String)

    fun getProjectDir(scId: String): File {
        return File(wq.d(scId))
    }

    fun getDataDir(scId: String): File {
        return File(wq.b(scId))
    }

    fun resolveFile(scId: String, relativePath: String): File {
        val clean = relativePath.trim().removePrefix("/").removePrefix("./")
        val base = getProjectDir(scId)
        val file = File(base, clean)
        if (file.exists()) return file

        val inApp = File(base, "app/src/main/$clean")
        if (inApp.exists()) return inApp

        val inJava = File(base, "app/src/main/java/$clean")
        if (inJava.exists()) return inJava

        val inData = File(getDataDir(scId), clean)
        if (inData.exists()) return inData

        return file
    }

    fun createFile(scId: String, relativePath: String, content: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        if (relativePath.isBlank()) return Result(false, "File path is required.")

        return try {
            val target = resolveFile(scId, relativePath)
            target.parentFile?.mkdirs()
            FileUtil.writeFile(target.absolutePath, content)
            Result(true, "File created successfully at ${target.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create file: $relativePath", e)
            Result(false, "Failed to create file: ${e.message}")
        }
    }

    fun readFile(scId: String, relativePath: String): String {
        if (scId.isEmpty()) return "Project ID is required."
        val target = resolveFile(scId, relativePath)
        if (!target.exists() || !target.isFile) {
            return "File '${target.name}' not found in project (searched at ${target.absolutePath})."
        }

        return try {
            val text = FileUtil.readFile(target.absolutePath)
            if (text.length > MAX_READ_CHARS) {
                text.substring(0, MAX_READ_CHARS) + "\n\n... (truncated, file too large)"
            } else {
                text
            }
        } catch (e: Exception) {
            "Failed to read file: ${e.message}"
        }
    }

    fun writeFile(scId: String, relativePath: String, content: String): Result {
        return createFile(scId, relativePath, content)
    }

    fun searchInProject(scId: String, query: String, maxResults: Int = 20): String {
        if (scId.isEmpty()) return "Project ID is required."
        if (query.isBlank()) return "Search query is empty."

        val projectDir = getProjectDir(scId)
        val dataDir = getDataDir(scId)
        val roots = listOf(projectDir, dataDir).filter { it.exists() }

        val matches = mutableListOf<String>()
        val lower = query.lowercase(Locale.ROOT)

        for (root in roots) {
            root.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".apk") && !it.name.endsWith(".zip") && !it.name.endsWith(".dex") && !it.name.endsWith(".jar") }
                .forEach { file ->
                    if (matches.size >= maxResults) return@forEach
                    try {
                        val lines = file.readLines()
                        lines.forEachIndexed { idx, line ->
                            if (matches.size >= maxResults) return@forEachIndexed
                            if (line.lowercase(Locale.ROOT).contains(lower)) {
                                val rel = file.absolutePath.removePrefix(root.parentFile?.absolutePath ?: "")
                                matches.add("$rel:${idx + 1}: ${line.trim()}")
                            }
                        }
                    } catch (_: Exception) {}
                }
        }

        return if (matches.isEmpty()) {
            "No occurrences found for '$query' in project."
        } else {
            "Found ${matches.size} match(es):\n" + matches.joinToString("\n")
        }
    }

    fun createActivity(scId: String, rawName: String, title: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        val cleanName = rawName.trim().removeSuffix(".java").removeSuffix(".kt").removeSuffix("Activity")
        val activityName = cleanName.replaceFirstChar { it.uppercase() } + "Activity"
        val xmlName = cleanName.lowercase(Locale.ROOT) + ".xml"

        return try {
            // 1. Create Layout XML
            AiLayoutHelper.applyLayoutXml(
                scId,
                xmlName,
                """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="16dp">

    <TextView
        android:id="@+id/tv_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="${if (title.isNotEmpty()) title else cleanName}"
        android:textSize="20sp"
        android:textStyle="bold" />
</LinearLayout>"""
            )

            // 2. Register ProjectFileBean via Sketchware data manager
            val fileBeanManager = jC.b(scId)
            if (fileBeanManager != null) {
                val bean = ProjectFileBean(
                    ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY,
                    cleanName.lowercase(Locale.ROOT),
                    ProjectFileBean.ORIENTATION_PORTRAIT,
                    ProjectFileBean.THEME_NO_ACTIONBAR,
                    ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED
                )
                fileBeanManager.a(bean)
                fileBeanManager.l()
                fileBeanManager.j()
                jC.a(scId)?.a(fileBeanManager)
            }

            // 3. Create Java skeleton
            val javaDir = File(getProjectDir(scId), "app/src/main/java")
            javaDir.mkdirs()
            val code = """package com.my.newproject;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class $activityName extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${cleanName.lowercase(Locale.ROOT)});
        initialize();
    }

    private void initialize() {
        // AI Generated Activity Initialization
    }
}
"""
            FileUtil.writeFile(File(javaDir, "$activityName.java").absolutePath, code)

            Result(true, "Activity $activityName & layout $xmlName created successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create activity $activityName", e)
            Result(false, "Failed to create activity: ${e.message}")
        }
    }

    fun addPermission(scId: String, permissionName: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        val cleanPerm = permissionName.trim().let {
            if (it.startsWith("android.permission.")) it else "android.permission.$it"
        }

        return try {
            val manifestFile = File(getProjectDir(scId), "app/src/main/AndroidManifest.xml")
            if (manifestFile.exists()) {
                val content = FileUtil.readFile(manifestFile.absolutePath)
                if (content.contains(cleanPerm)) {
                    return Result(true, "Permission $cleanPerm is already present.")
                }
                val permTag = "    <uses-permission android:name=\"$cleanPerm\" />\n"
                val updated = if (content.contains("<manifest")) {
                    content.replaceFirst(Regex("(<manifest[^>]*>)"), "$1\n$permTag")
                } else {
                    permTag + content
                }
                FileUtil.writeFile(manifestFile.absolutePath, updated)
                Result(true, "Permission $cleanPerm added to AndroidManifest.xml successfully.")
            } else {
                val permDir = File(getDataDir(scId), "permission")
                permDir.mkdirs()
                val permListFile = File(permDir, "list")
                val current = if (permListFile.exists()) FileUtil.readFile(permListFile.absolutePath) else ""
                if (!current.contains(cleanPerm)) {
                    FileUtil.writeFile(permListFile.absolutePath, "$current\n$cleanPerm".trim())
                }
                Result(true, "Permission $cleanPerm added to project permissions.")
            }
        } catch (e: Exception) {
            Result(false, "Failed to add permission: ${e.message}")
        }
    }

    fun addComponent(scId: String, javaName: String, typeName: String, id: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        return try {
            val projectData = jC.a(scId) ?: return Result(false, "Project data is not loaded.")
            val typeInt = when (typeName.lowercase(Locale.ROOT)) {
                "intent" -> ComponentBean.COMPONENT_TYPE_INTENT
                "sharedpref", "sharedpreferences", "sp" -> ComponentBean.COMPONENT_TYPE_SHAREDPREF
                "timer", "timertask" -> ComponentBean.COMPONENT_TYPE_TIMERTASK
                "dialog" -> ComponentBean.COMPONENT_TYPE_DIALOG
                "vibrator" -> ComponentBean.COMPONENT_TYPE_VIBRATOR
                "calendar" -> ComponentBean.COMPONENT_TYPE_CALENDAR
                "mediaplayer" -> ComponentBean.COMPONENT_TYPE_MEDIAPLAYER
                "soundpool" -> ComponentBean.COMPONENT_TYPE_SOUNDPOOL
                "requestnetwork" -> ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK
                "firebase", "firebasedb" -> ComponentBean.COMPONENT_TYPE_FIREBASE
                "firebaseauth" -> ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH
                "shizuku" -> ComponentBean.COMPONENT_TYPE_SHIZUKU
                else -> 1
            }
            val bean = ComponentBean(typeInt, id)
            projectData.a(javaName, bean)
            Result(true, "Component $typeName ($id) added to $javaName.")
        } catch (e: Exception) {
            Result(false, "Failed to add component: ${e.message}")
        }
    }

    fun addVariable(scId: String, javaName: String, varType: String, varName: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        return try {
            val projectData = jC.a(scId) ?: return Result(false, "Project data is not loaded.")
            val typeInt = when (varType.lowercase(Locale.ROOT)) {
                "boolean", "bool" -> 0
                "integer", "int", "number" -> 1
                "string" -> 2
                "map" -> 3
                else -> 2
            }
            val vars = projectData.k(javaName)
            if (vars != null) {
                vars.add(Pair(typeInt, varName))
                Result(true, "Variable $varType $varName added to $javaName.")
            } else {
                Result(false, "Variable list not available for $javaName.")
            }
        } catch (e: Exception) {
            Result(false, "Failed to add variable: ${e.message}")
        }
    }
}
