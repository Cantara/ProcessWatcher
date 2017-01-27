package no.cantara.process.util;

import org.apache.commons.io.FileUtils;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A bad hack to workaround having this library easily embeded in projects while ensuring that we satisfy
 * the native libraries in the correct paths in the various distributions.
 * <p>
 * We use loading of native libraries directly from the processwatcher github repo to avoid "missing" libraries
 * in the resources of projects using this library
 */
public class Sigar_LD_LIBRARY_PATH_Hack {

    private static final Logger log = LoggerFactory.getLogger(Sigar_LD_LIBRARY_PATH_Hack.class);
    private static final String localLibraryPath = "./nativelibs";


    public static void aplyHack() {
        addLibraryPath(localLibraryPath);
        if (!isSigarLibraryOK()) {
            String filename = getNativeLibFilename();
            log.warn("Attempting to install {} into {}", filename, localLibraryPath);
            downloadAndSaveInLibrary(filename, localLibraryPath);
            classhack();
            addLibraryPath(localLibraryPath);
        }
        getLibraryPath();
    }

    private static boolean downloadAndSaveInLibrary(String filename, String librarypath) {
        // https://github.com/Cantara/ProcessWatcher/raw/master/src/main/resources/nativelibs/
        String gitHubUrlPrefix = "https://github.com/Cantara/ProcessWatcher/raw/master/src/main/resources/nativelibs/";
        try {
            File directory = new File(String.valueOf(librarypath));
            if (!directory.exists()) {
                directory.mkdir();
                // If you require it to make the entire directory path including parents,
                // use directory.mkdirs(); here instead.
            }
            log.info("Attempting to download {} to {}", gitHubUrlPrefix + filename, librarypath + File.separator + filename);
            FileUtils.copyURLToFile(new URL(gitHubUrlPrefix + filename), new File(librarypath + File.separator + filename));
            log.info("Download successfull");
            return true;
        } catch (Exception e) {
            log.error("Unable to download and install native library", e);
            return false;
        }

    }

    public static boolean isSigarLibraryOK() {
        long[] procList = null;
        Sigar sigar = null;
        try {
            sigar = new Sigar();
            procList = sigar.getProcList();
        } catch (UnsatisfiedLinkError e) {
            log.warn("Unable to use sigar native API", e);
            return false;
        } catch (SigarException se) {
            log.warn("Unable to use sigar native API", se);
            return false;
        }
        return true;

    }

    private static String getLibraryPath() {
        try {
            String checkLib = "";
            List<String> dirs = Arrays.asList(System.getProperty("java.library.path").split(":"));
            log.info(System.getProperty("java.library.path"));
            for (Iterator<String> it = dirs.iterator(); it.hasNext(); ) {
                String dir = it.next();
                if (dir.length() < 1) {
                    continue;
                }
                // We choose to use the first entry as it is the one we most likely have write-access to
                return dir;
            }
            return checkLib;
        } catch (Exception e) {
            log.error("Unable to locate the LD_LIBRARY_PATH on this system", e);
        }
        return "";

    }

    /**
     * Adds the specified path to the java library path
     *
     * @param pathToAdd the path to add
     */
    private static void addLibraryPath(String pathToAdd) {
        try {
            System.setProperty("java.library.path", pathToAdd);
            log.info("System.setProperty(\"java.library.path\") : {} ", pathToAdd);

            //set sys_paths to null
            final Field sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
            sysPathsField.setAccessible(true);
            sysPathsField.set(null, null);
        } catch (Exception e) {
            log.error("Unable to add native library path to the system", e);
        }
    }

    private static String getNativeLibFilename() {
        if (FileSystemSupport.isLinux()) {
            return FileSystemSupport.LINUX_LIB;
        } else if (FileSystemSupport.isMacOS()) {
            return FileSystemSupport.MACOS_LIB;
        } else {
            return "Unknown";
        }

    }

    private static void classhack() {
        try {
// this forces JVM to reload "java.library.path" property
            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(null, null);
        } catch (Exception e) {
            // can't happen - the wonders of checked exceptions...
        }

    }

}
