package moe.yuuta.ruleconverter

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import java.io.FileWriter

class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 4) {
                System.err.println("Usage: RuleConverter <Source folder> <canary true | false> <output folder> <branch>")
                System.exit(1)
                return
            }
            val source = File(args[0])
            val canary = args[1].toBoolean()
            val outputFolder = File(args[2])
            val branch = args[3]
            System.out.println("Source: $source Out: $outputFolder (Canary: $canary, branch: $branch)")
            if (!lintArgs(source, outputFolder)) {
                return
            }
            outputFolder.mkdir()
            val parser = SourceParser(source, canary)
            val rulesJson = Gson().toJsonTree(parser.getRules())
            writeAndClose(rulesJson, FileWriter(deleteIfExists(File(outputFolder, "rules_$branch.json"))))
            writeAndClose(wrap(rulesJson), FileWriter(deleteIfExists(File(outputFolder, "rules_wrapped_$branch.json"))))
            writeAndClose(wrap(parser.getRules().size), FileWriter(deleteIfExists(File(outputFolder, "rules_size_$branch.json"))))

            val deletedJson = Gson().toJsonTree(parser.getDeleted())
            System.out.println(parser.getDeleted())
            writeAndClose(deletedJson, FileWriter(deleteIfExists(File(outputFolder, "deleted_$branch.json"))))
            writeAndClose(wrap(deletedJson), FileWriter(deleteIfExists(File(outputFolder, "deleted_wrapped_$branch.json"))))
            writeAndClose(wrap(parser.getDeleted().size), FileWriter(deleteIfExists(File(outputFolder, "deleted_size_$branch.json"))))
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
            if (!outputFolder.exists()) {
                outputFolder.mkdir()
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

        private fun wrap(number: Number): JsonObject {
            val objWithWrapper = JsonObject()
            objWithWrapper.addProperty("code", 0)
            objWithWrapper.addProperty("data", number)
            return objWithWrapper
        }

        private fun deleteIfExists(file: File): File {
            if (file.isDirectory) throw IllegalArgumentException("The file cannot be a directory")
            if (file.exists()) file.delete()
            return file
        }
    }
}