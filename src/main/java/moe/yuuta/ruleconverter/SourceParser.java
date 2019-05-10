package moe.yuuta.ruleconverter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public class SourceParser {
    private static final String FOLDER_BASE = "rules";

    private List<Rule> rules = new ArrayList<>(200);
    private List<Rule> deleted = new ArrayList<>(20);

    /**
     * 基文件夹，一般是 根目录 /rules
     */
    private volatile File baseFolder;

    public SourceParser(final File sourceFolder) throws IOException {
        enterFolder(sourceFolder, null /* 初始不给值 */);
    }

    public List<Rule> getRules() {
        return rules;
    }

    public List<Rule> getDeleted() {
        return deleted;
    }

    private boolean enterFolder (final File parent, final Name name) throws IOException {
        // 找到基目录再开始
        if (baseFolder != null && parent.isFile() &&
                parent.getAbsolutePath().startsWith(baseFolder.getAbsolutePath())) {
            return addRule(parent, name);
        }
        File[] children = parent.isDirectory() ? parent.listFiles() : null;
        if (children == null)
            return true;
        // 只再基目录找到之后再开始记录，防止记录无关目录
        if (baseFolder == null) {
            // 看一下现在这个是不是基目录
            if (parent.getName().equalsIgnoreCase(FOLDER_BASE)) {
                // 找到了
                baseFolder = parent;
                System.out.println("Base folder: " + baseFolder.getAbsolutePath());
            }
        }
        boolean success = true;
        for (File child : children) {
            if (baseFolder != null &&
                    !child.getAbsolutePath().startsWith(baseFolder.getAbsolutePath())) {
                // Skip it because it is not in the base folder
                continue;
            }
            // Ignore hidden files
            if (child.getName().startsWith(".")) continue;
            System.out.println("Enter: " + child.getAbsolutePath());
            if (!enterFolder(child, null))
                success = false;
        }
        return success;
    }

    private boolean addRule (final File file, Name name) throws IOException {
        if (name == null) {
            name = Name.tryParse(file);
        }
        if (name == null)
            return false;
        final String dir = getRelativePath(file);
        if (dir == null) return false;
        // Build rule
        final JsonObject rawJson = new JsonParser().parse(readFile(file)).getAsJsonObject();
        final Rule rule = new Rule();
        final int mode = rawJson.get("mode").getAsInt();
        rule.setMode(mode);
        rule.setDir(dir);
        rule.setIsFile(name.getType().equals(Name.TYPE_FILE_PREFIX));
        final Optional<String> firstPkg = StreamSupport.stream(Spliterators.spliteratorUnknownSize(rawJson.get("pkg")
                .getAsJsonArray()
                .iterator(),
                Spliterator.ORDERED),
                false)
                .map(JsonElement::getAsString)
                .findFirst();
        firstPkg.ifPresent(rule::setPackage);
        switch (mode) {
            case Rule.MODE_AD:
                rule.setNotReplace(false);
                rule.setCarefullyClean(false);
                rule.setCarefullyReplace(false);
                rule.setWillClean(true);
                break;
            case Rule.MODE_CUSTOM:
                rule.setWillClean(rawJson.get("willClean").getAsBoolean());
                rule.setNeedUninstall(rawJson.get("needUninstall").getAsBoolean());
                rule.setNotReplace(rawJson.get("notReplace").getAsBoolean());
                rule.setCarefullyClean(rawJson.get("carefully_clean").getAsBoolean());
                rule.setCarefullyReplace(rawJson.get("carefully_replace").getAsBoolean());
                break;
            case Rule.MODE_CACHE:
                rule.setWillClean(true);
                rule.setCarefullyReplace(true);
                rule.setCarefullyClean(false);
                rule.setNotReplace(true);
                break;
            case Rule.MODE_DELETED:
                // Although deleted rules won't have as much as arguments as the normal Rule, we still fill them in because I would not like to support them.
                rule.setPath(dir);
                break;
            case Rule.MODE_LOG:
                rule.setNotReplace(false);
                rule.setCarefullyClean(false);
                rule.setCarefullyReplace(false);
                rule.setWillClean(true);
                break;
            case Rule.MODE_NONE:
                rule.setNotReplace(true);
                rule.setCarefullyClean(false);
                rule.setCarefullyReplace(true);
                rule.setWillClean(true);
                break;
            case Rule.MODE_UNINSTALL:
                rule.setNotReplace(true);
                rule.setCarefullyClean(false);
                rule.setCarefullyReplace(true);
                rule.setWillClean(false);
                break;
            case Rule.MODE_USER_DATA:
                rule.setNotReplace(true);
                rule.setCarefullyClean(true);
                rule.setCarefullyReplace(true);
                rule.setWillClean(false);
                break;
        }
        final Optional<String> firstAuthor = StreamSupport.stream(Spliterators.spliteratorUnknownSize(rawJson.get("authors")
                        .getAsJsonArray()
                        .iterator(),
                Spliterator.ORDERED),
                false)
                .map(JsonElement::getAsString)
                .filter(s -> !s.equals("DEVELOPER"))
                .findFirst();
        firstAuthor.ifPresent(rule::setAuthor);
        final JsonObject titleRes = rawJson.get("title").getAsJsonObject();
        // Same as exported value: zh-rCN
        final JsonObject titleSources = titleRes.getAsJsonObject("source");
        final String titleZh = titleSources.has("zh-rCN") ? titleSources.get("zh-rCN").getAsString() : null;
        final String titleDef = titleRes.get("defaultSource").getAsString();
        final String preferredTitle =
                (titleZh != null && !titleZh.trim().equals("")) ? titleZh : titleDef;
        rule.setTitle(preferredTitle);
        // Add to list
        if (mode == Rule.MODE_DELETED) {
            deleted.add(rule);
        } else {
            rules.add(rule);
        }
        return true;
    }

    private String getRelativePath(File file) {
        StringBuilder dirBuilder = new StringBuilder();
        String path = file.getAbsolutePath().substring(baseFolder.getAbsolutePath().length());
        String[] paths = path.split(Pattern.quote(File.separator));
        for (int i = 0; i < (paths.length - 1) /* 最后一个是本文件，忽略 */; i++) {
            String folder = paths[i];
            if (folder == null || folder.trim().equals("")) {
                continue;
            }
            Name n = Name.tryParse(folder, false);
            if (n == null) {
                System.err.println("Unable to parse");
                continue;
            }
            dirBuilder.append(n.getName());
            dirBuilder.append(File.separator);
        }
        // 最后再加上自己的目录
        Name n = Name.tryParse(file.getName(), true);
        if (n == null)
            return null;
        dirBuilder.append(n.getName());
        return dirBuilder.toString();
    }

    private String readFile (final File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String sCurrentLine;
        while ((sCurrentLine = reader.readLine()) != null) {
            builder.append(sCurrentLine);
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * Includes the parts of the name
     * Rule: [Type].[Name].json
     * Folder: FOLDER.[Name]
     */
    public static class Name {
        private static final String TAG = "Name";

        public static final String TYPE_FILE_PREFIX = "FILE";
        public static final String TYPE_FOLDER_PREFIX = "FOLDER";

        /**
         * 这个文件是一个规则 Json
         */
        static final int FILE_TYPE_RULE = 0;
        /**
         * 这个文件夹是一个文件夹
         */
        static final int FILE_TYPE_FOLDER = 1;

        private final int fileType;
        private final String type;
        private final String name;

        private Name(int fileType, String type, String name) {
            this.fileType = fileType;
            this.type = type;
            this.name = name;
        }

        public static Name create (File file) throws IllegalArgumentException {
            return parse(file.getName(), file.isFile());
        }

        public static Name parse (String fileName, boolean isFile) throws IllegalArgumentException {
            int fileType;
            final String nameOriginal = fileName.toUpperCase();
            if (isFile) {
                if (!nameOriginal.endsWith(".JSON")) {
                    throw new IllegalArgumentException("Not a json: " + fileName);
                }
                fileType = FILE_TYPE_RULE;
            } else {
                if (!nameOriginal.startsWith(TYPE_FOLDER_PREFIX)) {
                    throw new IllegalArgumentException("Not starts with 'FOLDER': " + fileName);
                }
                fileType = FILE_TYPE_FOLDER;
            }

            // Parse name
            final String[] names = nameOriginal.split("\\.");
            if ((isFile && names.length < 3) ||
                    (!isFile && names.length < 2)) {
                throw new IllegalArgumentException("Invalid file name: " + fileName);
            }

            String type;
            switch (names[0]) {
                case TYPE_FILE_PREFIX:
                    type = TYPE_FILE_PREFIX;
                    break;
                case TYPE_FOLDER_PREFIX:
                    type = TYPE_FOLDER_PREFIX;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected type: " + names[0] + " (" + fileName + ")");
            }

            String name;
            // The things before name part.
            // Value: [Type].[Flavor].
            StringBuilder beforeBuilder = new StringBuilder()
                    .append(names[0])
                    .append('.');
            String strBeforeName = beforeBuilder.toString();
            // The things after name part.
            // For folder, it hasn't any parts after the name
            // For rule json, it has '.json'.
            String strAfterName = fileType == FILE_TYPE_RULE ?
                    ".json" : "";
            // Use 'substring' to remove 'before' and 'after'
            name = nameOriginal.substring(strBeforeName.length()); // [Name].json or [Name]
            name = name.substring(0, name.length() - strAfterName.length()); // [Name]

            return new Name(fileType, type, name);
        }

        public static Name tryParse (String fileName, boolean isFile) {
            try {
                return parse(fileName, isFile);
            } catch (IllegalArgumentException e) {
                System.err.println("An error was thrown when parsing: " + e.getMessage());
                return null;
            }
        }

        public static Name tryParse (File file) {
            try {
                return create(file);
            } catch (IllegalArgumentException e) {
                System.err.println("An error was thrown when parsing: " + e.getMessage());
                return null;
            }
        }

        public int getFileType() {
            return fileType;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Name{" +
                    "fileType=" + fileType +
                    ", type='" + type + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
