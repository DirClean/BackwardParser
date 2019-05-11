package moe.yuuta.ruleconverter

import com.google.gson.JsonElement
import com.google.gson.JsonParser

import java.io.File
import java.io.IOException
import java.util.*
import java.util.regex.Pattern
import java.util.stream.StreamSupport

class SourceParser(sourceFolder: File, private val canary: Boolean) {
    companion object {
        private const val FOLDER_BASE = "rules"
    }

    private val rules = mutableListOf<Rule>()
    private val deleted = mutableListOf<Rule>()

    /**
     * 基文件夹，一般是 根目录 /rules
     */
    private lateinit var baseFolder: File

    init {
        enterFolder(sourceFolder, null /* 初始不给值 */)
    }

    fun getRules(): List<Rule> = rules

    fun getDeleted(): List<Rule> = deleted

    @Throws(IOException::class)
    private fun enterFolder (parent: File, name: Name?): Boolean {
        // 找到基目录再开始
        if ((::baseFolder.isInitialized) && parent.isFile &&
                parent.absolutePath.startsWith(baseFolder.absolutePath)) {
            System.out.println("Inserting $parent")
            return addRule(parent, name)
        }
        val children = if (parent.isDirectory) parent.listFiles() else null
        if (children == null)
            return true
        // 只再基目录找到之后再开始记录，防止记录无关目录
        if (!::baseFolder.isInitialized) {
            // 看一下现在这个是不是基目录
            if (parent.name.equals(FOLDER_BASE, true)) {
                // 找到了
                baseFolder = parent
                System.out.println("Base folder: " + baseFolder.absolutePath)
            }
        }
        var success = true
        for (child in children) {
            if (::baseFolder.isInitialized &&
                    !child.absolutePath.startsWith(baseFolder.absolutePath)) {
                // Skip it because it is not in the base folder
                continue
            }
            // Ignore hidden files
            if (child.name.startsWith(".")) continue
            System.out.println("Enter: " + child.absolutePath)
            if (!enterFolder(child, null))
                success = false
        }
        return success
    }

    @Throws(IOException::class)
    private fun addRule (file: File, n: Name?): Boolean {
        var name = n
        if (name == null) {
            name = Name.tryParse(file)
        }
        if (name == null) {
            System.err.println("Cannot parse name for $file")
            return false
        }
        val dir = getRelativePath(file)
        if (dir == null) {
            System.err.println("Cannot get path for $file")
            return false
        }
        // Build rule
        val rawJson = JsonParser().parse(readFile(file)).asJsonObject
        val mode = rawJson.get("mode").asInt
        val isFile = name.type.equals(Name.TYPE_FILE_PREFIX)
        val firstPkg = StreamSupport.stream(Spliterators.spliteratorUnknownSize(rawJson.get("pkg")
                .asJsonArray
                .iterator(),
                Spliterator.ORDERED),
                false)
                .map(JsonElement::getAsString)
                .findFirst()
        val `package` = if (firstPkg.isPresent) firstPkg.get() else null
        val notReplace = when (mode) {
            Rule.MODE_AD -> false
            Rule.MODE_CUSTOM -> rawJson.get("notReplace").asBoolean
            Rule.MODE_CACHE -> true
            Rule.MODE_DELETED -> true
            Rule.MODE_LOG -> false
            Rule.MODE_NONE -> true
            Rule.MODE_UNINSTALL -> true
            Rule.MODE_USER_DATA -> true
            else -> true
        }
        val carefullyClean = when (mode) {
            Rule.MODE_AD -> false
            Rule.MODE_CACHE -> false
            Rule.MODE_LOG -> false
            Rule.MODE_NONE -> false
            Rule.MODE_UNINSTALL -> false
            Rule.MODE_CUSTOM -> rawJson.get("carefully_clean").asBoolean
            Rule.MODE_USER_DATA -> true
            Rule.MODE_DELETED -> false
            else -> true
        }
        val carefullyReplace = when (mode) {
            Rule.MODE_AD -> false
            Rule.MODE_CACHE -> true
            Rule.MODE_LOG -> false
            Rule.MODE_NONE -> true
            Rule.MODE_UNINSTALL -> true
            Rule.MODE_USER_DATA -> true
            Rule.MODE_CUSTOM -> rawJson.get("carefully_replace").asBoolean
            Rule.MODE_DELETED -> false
            else -> true
        }
        val willClean = when (mode) {
            Rule.MODE_AD -> true
            Rule.MODE_CACHE -> true
            Rule.MODE_LOG -> true
            Rule.MODE_NONE -> true
            Rule.MODE_UNINSTALL -> false
            Rule.MODE_USER_DATA -> false
            Rule.MODE_CUSTOM -> rawJson.get("willClean").asBoolean
            Rule.MODE_DELETED -> true
            else -> false
        }
        val needUninstall = when (mode) {
            Rule.MODE_NONE -> false
            Rule.MODE_CACHE -> false
            Rule.MODE_LOG -> false
            Rule.MODE_AD -> false
            Rule.MODE_UNINSTALL -> true
            Rule.MODE_USER_DATA -> false
            Rule.MODE_DELETED -> false
            Rule.MODE_CUSTOM -> rawJson.get("needUninstall").asBoolean
            else -> false
        }
        val path = if (mode == Rule.MODE_UNINSTALL) dir else null
        
        val firstAuthor = StreamSupport.stream(Spliterators.spliteratorUnknownSize(rawJson.get("authors")
                        .asJsonArray
                        .iterator(),
                Spliterator.ORDERED),
                false)
                .map(JsonElement::getAsString)
                .filter { 
                    return@filter it != "DEVELOPER"
                }
                .findFirst()
        val author = if (firstAuthor.isPresent) firstAuthor.get() else null
        val titleRes = rawJson.get("title").asJsonObject
        // Same as exported value: zh-rCN
        val titleSources = titleRes.getAsJsonObject("source")
        val titleZh = if (titleSources.has("zh-rCN")) titleSources.get("zh-rCN").asString else null
        val titleDef = titleRes.get("defaultSource").asString
        val preferredTitle =
                if (titleZh != null && !titleZh.trim().equals("")) titleZh else titleDef
        
        // Create the object
        val rule = 
            Rule(
                author, 
                preferredTitle, 
                `package`, 
                dir, 
                path, 
                needUninstall, 
                isFile, 
                willClean, 
                notReplace, 
                mode, 
                carefullyClean, 
                carefullyReplace, 
                canary
            )
        // Add to list
        if (mode == Rule.MODE_DELETED) {
            deleted.add(rule)
        } else {
            rules.add(rule)
        }
        return true
    }

    private fun getRelativePath(file: File): String? {
        val dirBuilder = StringBuilder()
        val path = file.absolutePath.substring(baseFolder.absolutePath.length)
        val paths = path.split(Pattern.quote(File.separator))
        for (i in 0 until (paths.size - 1) /* 最后一个是本文件，忽略 */) {
            val folder = paths[i]
            if (folder.trim() == "") {
                continue
            }
            val n = Name.tryParse(folder, false)
            if (n == null) {
                System.err.println("Unable to parse")
                continue
            }
            dirBuilder.append(n.name)
            dirBuilder.append(File.separator)
        }
        // 最后再加上自己的目录
        val n = Name.tryParse(file.name, true) ?: return null
        dirBuilder.append(n.name)
        return dirBuilder.toString()
    }

    @Throws(IOException::class)
    private fun readFile (file: File): String = file.readText(Charsets.UTF_8)

    /**
     * Includes the parts of the name
     * Rule: [Type].[Name].json
     * Folder: FOLDER.[Name]
     */
    data class Name(
        val fileType: Int,
        val type: String,
        val name: String
    ) {
        companion object {
            const val TYPE_FILE_PREFIX = "FILE"
            const val TYPE_FOLDER_PREFIX = "FOLDER"

            /**
             * 这个文件是一个规则 Json
             */
            internal const val FILE_TYPE_RULE = 0
            /**
             * 这个文件夹是一个文件夹
             */
            internal const val FILE_TYPE_FOLDER = 1

            @Throws(IllegalArgumentException::class)
            fun create (file: File): Name {
                return parse(file.name, file.isFile)
            }

            @Throws(IllegalArgumentException::class)
            fun parse (fileName: String, isFile: Boolean): Name {
                val fileType: Int
                val nameOriginal = fileName.toUpperCase()
                if (isFile) {
                    if (!nameOriginal.endsWith(".JSON")) {
                        throw IllegalArgumentException("Not a json: " + fileName)
                    }
                    fileType = FILE_TYPE_RULE
                } else {
                    if (!nameOriginal.startsWith(TYPE_FOLDER_PREFIX)) {
                        throw IllegalArgumentException("Not starts with 'FOLDER': " + fileName)
                    }
                    fileType = FILE_TYPE_FOLDER
                }

                // Parse name
                val names = nameOriginal.split(Pattern.compile("\\."))
                if ((isFile && names.size < 3) ||
                    (!isFile && names.size < 2)) {
                    throw IllegalArgumentException("Invalid file name: " + fileName)
                }

                val type = when (names[0]) {
                    TYPE_FILE_PREFIX -> TYPE_FILE_PREFIX
                    TYPE_FOLDER_PREFIX -> TYPE_FOLDER_PREFIX
                    else ->
                    throw IllegalArgumentException("Unexpected type: " + names[0] + " (" + fileName + ")")
                }

                var name: String
                // The things before name part.
                // Value: [Type].[Flavor].
                val beforeBuilder = StringBuilder()
                    .append(names[0])
                    .append('.')
                val strBeforeName = beforeBuilder.toString()
                // The things after name part.
                // For folder, it hasn't any parts after the name
                // For rule json, it has '.json'.
                val strAfterName = if (fileType == FILE_TYPE_RULE) ".json" else ""
                // Use 'substring' to remove 'before' and 'after'
                name = nameOriginal.substring(strBeforeName.length) // [Name].json or [Name]
                name = name.substring(0, name.length - strAfterName.length) // [Name]

                return Name(fileType, type, name)
            }

            fun tryParse (fileName: String, isFile: Boolean): Name? {
                return try {
                    parse(fileName, isFile)
                } catch (e: IllegalArgumentException) {
                    System.err.println("An error was thrown when parsing: " + e.message)
                    null
                }
            }

            fun tryParse (file: File): Name? {
                return try {
                    create(file)
                } catch (e: IllegalArgumentException) {
                    System.err.println("An error was thrown when parsing: " + e.message)
                    null
                }
            }
        }
    }
}
