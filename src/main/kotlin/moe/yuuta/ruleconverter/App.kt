package moe.yuuta.ruleconverter

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import java.io.FileWriter

class RuleConverter {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 3) {
                System.err.println("Usage: RuleConverter <Source folder> <canary true | false> <output folder>")
                System.exit(1)
                return
            }
            val source = File(args[0])
            val canary = args[1].toBoolean()
            val outputFolder = File(args[2])
            System.out.println("Source: $source Out: $outputFolder (Canary: $canary)")
            if (!lintArgs(source, outputFolder)) {
                return
            }
            outputFolder.mkdir();
            val parser = SourceParser(source)
            val rules = parser.rules
            rules.stream()
                .forEach {
                    it.setCanary(canary)
                }
            val rulesJson = Gson().toJsonTree(rules)
            writeAndClose(rulesJson, FileWriter(deleteIfExists(File(outputFolder, "rules.json"))))
            writeAndClose(wrap(rulesJson), FileWriter(deleteIfExists(File(outputFolder, "rules_wrapped.json"))))

            val deletedJson = Gson().toJsonTree(parser.deleted)
            writeAndClose(deletedJson, FileWriter(deleteIfExists(File(outputFolder, "deleted.json"))))
            writeAndClose(wrap(deletedJson), FileWriter(deleteIfExists(File(outputFolder, "deleted_wrapped.json"))))
        }

        private fun lintArgs(source: File, outputFolder: File): Boolean {
            if (!source.exists()) {
                System.err.println("Source folder is not exists")
                System.exit(2)
                return false
            }
            if (source.isFile) {
                System.err.println("Source folder is a file")
                System.exit(3)
                return false
            }
            if (outputFolder.isFile) {
                System.err.println("Output folder is a file")
                System.exit(3)
                return false
            }
            return true
        }

        private fun writeAndClose(src: Any, writer: FileWriter) {
            Gson().toJson(src, writer)
            writer.close()
        }

        private fun wrap(data: JsonElement): JsonObject {
            val objWithWrapper = JsonObject()
            objWithWrapper.addProperty("code", 0)
            objWithWrapper.add("data", data)
            return objWithWrapper
        }

        private fun deleteIfExists(file: File): File {
            if (file.isDirectory) throw IllegalArgumentException("The file cannot be a directory")
            if (file.exists()) file.delete()
            return file
        }
    }
}