package moe.yuuta.ruleconverter

import com.google.gson.annotations.SerializedName

data class Rule(
    @field:SerializedName("author")
    val author: String?,
    @field:SerializedName("title")
    val title: String,
    @field:SerializedName(value = "pkg", alternate = arrayOf("package"))
    val `package`: String?,
    @field:SerializedName("dir")
    val dir: String,
    // Only for deleted rules
    @field:SerializedName("path")
    val path: String?,
    @field:SerializedName("needUninstall")
    val needUninstall: Boolean,
    @field:SerializedName("isFile")
    val isFile: Boolean,
    @field:SerializedName("willClean")
    var willClean: Boolean = true,
    @field:SerializedName("notReplace")
    var notReplace: Boolean = false,
    @field:SerializedName("mode")
    var mode: Int = MODE_NONE,
    @field:SerializedName("carefully_clean")
    val carefullyClean: Boolean,
    @field:SerializedName("carefully_replace")
    val carefullyReplace: Boolean,
    @field:SerializedName("canary")
    val canary: Boolean
) {
    companion object {
        const val MODE_NONE = 0
        const val MODE_CACHE = 1
        const val MODE_LOG = 2
        const val MODE_AD = 3
        const val MODE_UNINSTALL = 4
        const val MODE_USER_DATA = 5
        const val MODE_CUSTOM = 6
        const val MODE_DELETED = 7
    }
}