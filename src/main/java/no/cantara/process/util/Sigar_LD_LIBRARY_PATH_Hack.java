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

    public static void aplyHack() {
        if (!isSigarLibraryOK()) {
            String filename = getNativeLibFilename();
            String libraryPath = getLibraryPath();
            log.warn("Attempting to install {} into {}", filename, libraryPath);
            downloadAndSaveInLibrary(filename, libraryPath);
            classhack();
        }
    }

    private static boolean downloadAndSaveInLibrary(String filename, String librarypath) {
        // https://github.com/Cantara/ProcessWatcher/raw/master/src/main/resources/nativelibs/
        String gitHubUrlPrefix = "https://github.com/Cantara/ProcessWatcher/raw/master/src/main/resources/nativelibs/";
        try {
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
                return dir;
            }
            return checkLib;
        } catch (Exception e) {
            log.error("Unable to locate the LD_LIBRARY_PATH on this system", e);
        }
        return "";

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

    private static void classhack(){
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
