package org.sketchware.daygreen.ai

import a.a.a.jC
import a.a.a.wq
import android.util.Log
import android.util.Pair
import com.besome.sketch.beans.ComponentBean
import com.besome.sketch.beans.ProjectFileBean
import org.json.JSONArray
import org.json.JSONObject
import pro.sketchware.utility.FilePathUtil
import pro.sketchware.utility.FileUtil
import java.io.File
import java.util.Locale

object AiProjectHelper {

    private const val TAG = "AiProjectHelper"
    private const val MAX_READ_CHARS = 30000

    data class Result(val success: Boolean, val message: String)

    private val pathUtil = FilePathUtil()

    fun getProjectDataDir(scId: String): File {
        return File(wq.b(scId)) // /.sketchware/data/{scId}
    }

    fun getJavaDir(scId: String): File {
        return File(pathUtil.getPathJava(scId)) // /.sketchware/data/{scId}/files/java
    }

    fun getNativeDir(scId: String): File {
        return File(pathUtil.getPathNative(scId)) // /.sketchware/data/{scId}/files/native
    }

    fun getResourceDir(scId: String): File {
        return File(pathUtil.getPathResource(scId)) // /.sketchware/data/{scId}/files/resource
    }

    fun getAssetsDir(scId: String): File {
        return File(pathUtil.getPathAssets(scId)) // /.sketchware/data/{scId}/files/assets
    }

    fun getPermissionDir(scId: String): File {
        return File(pathUtil.getPathPermission(scId)) // /.sketchware/data/{scId}/permission
    }

    /**
     * Intelligently resolves file paths matching Sketchware's true project architecture.
     * Custom Java/Kotlin files belong in files/java, C/C++ in files/native, layouts in files/resource/layout, etc.
     */
    fun resolveFile(scId: String, relativePath: String): File {
        val clean = relativePath.trim().removePrefix("/").removePrefix("./")
        val lower = clean.lowercase(Locale.ROOT)

        val javaBase = getJavaDir(scId)
        val nativeBase = getNativeDir(scId)
        val resBase = getResourceDir(scId)
        val assetsBase = getAssetsDir(scId)
        val dataBase = getProjectDataDir(scId)

        // 1. Direct checks if file already exists in any valid folder
        val directInJava = File(javaBase, clean)
        if (directInJava.exists()) return directInJava

        val directInNative = File(nativeBase, clean)
        if (directInNative.exists()) return directInNative

        val directInRes = File(resBase, clean)
        if (directInRes.exists()) return directInRes

        val directInAssets = File(assetsBase, clean)
        if (directInAssets.exists()) return directInAssets

        val directInData = File(dataBase, clean)
        if (directInData.exists()) return directInData

        // 2. Intelligent placement by file extension & type
        if (lower.startsWith("files/java/") || lower.startsWith("java/")) {
            val sub = clean.removePrefix("files/java/").removePrefix("java/")
            return File(javaBase, sub)
        }
        if (lower.endsWith(".java") || lower.endsWith(".kt")) {
            return File(javaBase, clean)
        }

        if (lower.startsWith("files/native/") || lower.startsWith("native/") || lower.startsWith("cpp/")) {
            val sub = clean.removePrefix("files/native/").removePrefix("native/").removePrefix("cpp/")
            return File(nativeBase, sub)
        }
        if (lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".h") || lower.endsWith(".hpp")) {
            return File(nativeBase, clean)
        }

        if (lower.startsWith("files/assets/") || lower.startsWith("assets/")) {
            val sub = clean.removePrefix("files/assets/").removePrefix("assets/")
            return File(assetsBase, sub)
        }

        if (lower.startsWith("files/resource/") || lower.startsWith("resource/") || lower.startsWith("res/")) {
            val sub = clean.removePrefix("files/resource/").removePrefix("resource/").removePrefix("res/")
            return File(resBase, sub)
        }
        if (lower.endsWith(".xml")) {
            return File(resBase, if (lower.startsWith("layout/")) clean else "layout/$clean")
        }

        if (lower == "permission" || lower == "permissions" || lower == "permission/list") {
            return File(getPermissionDir(scId), "list")
        }

        return File(javaBase, clean)
    }

    fun createFile(scId: String, relativePath: String, content: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        if (relativePath.isBlank()) return Result(false, "File path is required.")

        return try {
            val target = resolveFile(scId, relativePath)
            target.parentFile?.mkdirs()
            FileUtil.writeFile(target.absolutePath, content)
            val relDisplay = target.absolutePath.substringAfter(getProjectDataDir(scId).absolutePath).removePrefix("/")
            Result(true, "Created: $relDisplay (${content.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create file: $relativePath", e)
            Result(false, "Failed to create file: ${e.message}")
        }
    }

    fun writeFile(scId: String, relativePath: String, content: String): Result {
        return createFile(scId, relativePath, content)
    }

    fun deleteFile(scId: String, relativePath: String): Result {
        if (scId.isEmpty()) return Result(false, "Project ID is required.")
        val target = resolveFile(scId, relativePath)
        return try {
            if (target.exists()) {
                val ok = target.delete()
                if (ok) Result(true, "Deleted: ${target.name}") else Result(false, "Failed to delete ${target.name}")
            } else {
                Result(false, "File '${target.name}' not found.")
            }
        } catch (e: Exception) {
            Result(false, "Delete error: ${e.message}")
        }
    }

    fun readFile(scId: String, relativePath: String): String {
        if (scId.isEmpty()) return "Project ID is required."
        val target = resolveFile(scId, relativePath)
        if (!target.exists() || !target.isFile) {
            return "File '${target.name}' not found (searched at: ${target.absolutePath})."
        }

        return try {
            val text = FileUtil.readFile(target.absolutePath)
            if (text.length > MAX_READ_CHARS) {
                text.substring(0, MAX_READ_CHARS) + "\n\n... (truncated, ${text.length} chars total)"
            } else {
                text
            }
        } catch (e: Exception) {
            "Failed to read file: ${e.message}"
        }
    }

    fun exploreProjectStructure(scId: String): String {
        if (scId.isEmpty()) return "Project ID is required."

        val root = JSONObject()
        root.put("sc_id", scId)

        // 1. Activities from ProjectFileManager
        val activitiesArray = JSONArray()
        val fileManager = jC.b(scId)
        if (fileManager != null) {
            for (bean in fileManager.b()) {
                val act = JSONObject()
                act.put("activity", bean.javaName)
                act.put("layout", bean.xmlName)
                act.put("type", bean.fileType)
                activitiesArray.put(act)
            }
        }
        root.put("activities", activitiesArray)

        // 2. Custom Java & Kotlin files
        val javaDir = getJavaDir(scId)
        val javaList = JSONArray()
        if (javaDir.exists()) {
            javaDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".java") || it.name.endsWith(".kt")) }.forEach {
                javaList.put(it.absolutePath.removePrefix(javaDir.absolutePath).removePrefix("/"))
            }
        }
        root.put("custom_java_kotlin_files", javaList)

        // 3. Custom Native C/C++ files
        val nativeDir = getNativeDir(scId)
        val nativeList = JSONArray()
        if (nativeDir.exists()) {
            nativeDir.walkTopDown().filter { it.isFile }.forEach {
                nativeList.put(it.absolutePath.removePrefix(nativeDir.absolutePath).removePrefix("/"))
            }
        }
        root.put("native_files", nativeList)

        // 4. Custom Resources
        val resDir = getResourceDir(scId)
        val resList = JSONArray()
        if (resDir.exists()) {
            resDir.walkTopDown().filter { it.isFile }.forEach {
                resList.put(it.absolutePath.removePrefix(resDir.absolutePath).removePrefix("/"))
            }
        }
        root.put("resource_files", resList)

        // 5. Assets
        val assetsDir = getAssetsDir(scId)
        val assetsList = JSONArray()
        if (assetsDir.exists()) {
            assetsDir.walkTopDown().filter { it.isFile }.forEach {
                assetsList.put(it.absolutePath.removePrefix(assetsDir.absolutePath).removePrefix("/"))
            }
        }
        root.put("assets", assetsList)

        // 6. Permissions
        val permFile = File(getPermissionDir(scId), "list")
        val permsList = JSONArray()
        if (permFile.exists()) {
            permFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                permsList.put(it)
            }
        }
        root.put("permissions", permsList)

        return root.toString(2)
    }

    fun searchInProject(scId: String, query: String, maxResults: Int = 20): String {
        if (scId.isEmpty()) return "Project ID is required."
        if (query.isBlank()) return "Search query is empty."

        val dataDir = getProjectDataDir(scId)
        val roots = listOf(dataDir).filter { it.exists() }

        val matches = mutableListOf<String>()
        val lower = query.lowercase(Locale.ROOT)

        for (root in roots) {
            root.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".apk") && !it.name.endsWith(".zip") && !it.name.endsWith(".dex") && !it.name.endsWith(".jar") && !it.name.startsWith(".syncthing") }
                .forEach { file ->
                    if (matches.size >= maxResults) return@forEach
                    try {
                        val lines = file.readLines()
                        lines.forEachIndexed { idx, line ->
                            if (matches.size >= maxResults) return@forEachIndexed
                            if (line.lowercase(Locale.ROOT).contains(lower)) {
                                val rel = file.absolutePath.removePrefix(root.absolutePath)
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
            // 1. Create Layout XML in files/resource/layout/
            val layoutDir = File(getResourceDir(scId), "layout")
            layoutDir.mkdirs()
            val layoutXml = """<?xml version="1.0" encoding="utf-8"?>
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
            FileUtil.writeFile(File(layoutDir, xmlName).absolutePath, layoutXml)

            // 2. Register ProjectFileBean via Sketchware data manager
            val fileBeanManager = jC.b(scId)
            if (fileBeanManager != null) {
                val bean = ProjectFileBean(
                    ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY,
                    cleanName.lowercase(Locale.ROOT),
                    ProjectFileBean.ORIENTATION_PORTRAIT,
                    ProjectFileBean.THEME_NOACTIONBAR,
                    ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED
                )
                fileBeanManager.a(bean)
                fileBeanManager.l()
                fileBeanManager.j()
                jC.a(scId)?.a(fileBeanManager)
            }

            // 3. Create Java skeleton in files/java/
            val javaDir = getJavaDir(scId)
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

            Result(true, "Created Activity $activityName and layout $xmlName.")
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
            val permDir = getPermissionDir(scId)
            permDir.mkdirs()
            val permListFile = File(permDir, "list")
            val current = if (permListFile.exists()) FileUtil.readFile(permListFile.absolutePath) else ""
            if (!current.contains(cleanPerm)) {
                FileUtil.writeFile(permListFile.absolutePath, "$current\n$cleanPerm".trim())
            }

            // Also check mysc AndroidManifest if present
            val manifestFile = File(wq.d(scId), "app/src/main/AndroidManifest.xml")
            if (manifestFile.exists()) {
                val content = FileUtil.readFile(manifestFile.absolutePath)
                if (!content.contains(cleanPerm)) {
                    val permTag = "    <uses-permission android:name=\"$cleanPerm\" />\n"
                    val updated = if (content.contains("<manifest")) {
                        content.replaceFirst(Regex("(<manifest[^>]*>)"), "$1\n$permTag")
                    } else {
                        permTag + content
                    }
                    FileUtil.writeFile(manifestFile.absolutePath, updated)
                }
            }
            Result(true, "Permission $cleanPerm added.")
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
            Result(true, "Added component $typeName ($id) to $javaName.")
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
                Result(true, "Added variable $varType $varName to $javaName.")
            } else {
                Result(false, "Variable list not available for $javaName.")
            }
        } catch (e: Exception) {
            Result(false, "Failed to add variable: ${e.message}")
        }
    }
}
