package no.cantara.process.util;

import java.nio.file.FileSystems;

/**
 * Created by ora on 10/20/16.
 */
public class FileSystemSupport {
    public static final String MACOS_LIB = "libsigar-universal64-macosx.dylib";
    public static final String LINUX_LIB = "libsigar-amd64-linux.so";

    public static String getOSString() {
        return System.getProperties().get("os.name") + " " + System.getProperties().get("os.version");
    }

    public static boolean isLinux() {
        return (getOSString().contains("Linux"));
    }

    public static boolean isLinuxFileSystem() {
        return (isLinux() && "LinuxFileSystem".equals(FileSystems.getDefault().getClass().getSimpleName()));
    }

    public static boolean isMacOS() {
        return ((getOSString().contains("MacOS")) || (getOSString().contains("OS X")));
    }

    public static boolean isMacOSFileSystem() {
        return (isMacOS() && "MacOSXFileSystem".equals(FileSystems.getDefault().getClass().getSimpleName()));
    }

    public static boolean hasJDKEventDrivenFileSystem() {
        return ((isLinuxFileSystem() || isWindowsFileSystem()) && !isMacOSFileSystem());
    }

    public static boolean isWindows() {
        return getOSString().contains("Windows");
    }

    public static boolean isWindowsFileSystem() {
        return (isWindows() && "WindowsFileSystem".equals(FileSystems.getDefault().getClass().getSimpleName()));
    }

    public static String execCmd(String cmd) throws java.io.IOException {
        Process proc = Runtime.getRuntime().exec(cmd);
        java.io.InputStream is = proc.getInputStream();
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        String val = "";
        if (s.hasNext()) {
            val = s.next();
        } else {
            val = "";
        }
        return val;
    }

}
