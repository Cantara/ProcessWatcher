package no.cantara.process.util;

import java.nio.file.FileSystems;

/**
 * Created by ora on 10/20/16.
 */
public class FileSystemSupport {

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

    public static boolean isWindows() {
        return getOSString().contains("Windows");
    }

    public static boolean isWindowsFileSystem() {
        return (isWindows() && "WindowsFileSystem".equals(FileSystems.getDefault().getClass().getSimpleName()));
    }

    public static String execCmd(String... cmd) throws java.io.IOException {
        Process proc = new ProcessBuilder(cmd).start();
        try (java.util.Scanner s = new java.util.Scanner(proc.getInputStream()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

}
