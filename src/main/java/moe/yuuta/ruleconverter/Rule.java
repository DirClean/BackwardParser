package moe.yuuta.ruleconverter;

import com.google.gson.annotations.SerializedName;

public class Rule {
    public static final int MODE_NONE = 0;
    public static final int MODE_CACHE = 1;
    public static final int MODE_LOG = 1 << 1;
    public static final int MODE_AD = 3;
    public static final int MODE_UNINSTALL = 1 << 2;
    public static final int MODE_USER_DATA = 5;
    public static final int MODE_CUSTOM = 6;
    public static final int MODE_DELETED = 7;

    @SerializedName("author")
    private String author;
    @SerializedName("title")
    private String mTitle;
    @SerializedName(value = "pkg", alternate = "package")
    private String mPackage;
    @SerializedName("dir")
    private String mDir;
    // Only for deleted rules
    @SerializedName("path")
    private String path;
    @SerializedName("needUninstall")
    private Boolean mNeedUninstall;
    @SerializedName("isFile")
    private boolean mIsFile;
    @SerializedName("willClean")
    private boolean mWillClean = true;
    @SerializedName("notReplace")
    private boolean mNotReplace = false;
    @SerializedName("mode")
    private int mode = MODE_NONE;
    @SerializedName("carefully_clean")
    private boolean carefullyClean;
    @SerializedName("carefully_replace")
    private boolean carefullyReplace;
    @SerializedName("canary")
    private boolean canary;

    public void setCanary(boolean canary) {
        this.canary = canary;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setNotReplace(boolean mNotReplace) {
        this.mNotReplace = mNotReplace;
    }

    public void setWillClean(boolean mWillClean) {
        this.mWillClean = mWillClean;
    }

    public void setIsFile(boolean mIsFile) {
        this.mIsFile = mIsFile;
    }

    public void setNeedUninstall(Boolean mNeedUninstall) {
        this.mNeedUninstall = mNeedUninstall;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public void setPackage(String mPackage) {
        this.mPackage = mPackage;
    }

    public void setDir(String mDir) {
        this.mDir = mDir;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setCarefullyClean(boolean carefullyClean) {
        this.carefullyClean = carefullyClean;
    }

    public void setCarefullyReplace(boolean carefullyReplace) {
        this.carefullyReplace = carefullyReplace;
    }

    public void setPath(String path) {
        this.path = path;
    }
}