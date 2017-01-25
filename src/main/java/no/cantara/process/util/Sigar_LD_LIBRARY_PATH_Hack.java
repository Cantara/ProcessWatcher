package no.cantara.process.util;

import org.apache.commons.io.FileUtils;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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


    private static boolean downloadAndSaveInLibrary(String filename, String librarypath) {
        try {
            FileUtils.copyURLToFile(new URL("" + filename), new File(librarypath + filename));
            return true;
        } catch (Exception e) {
            return false;
        }

    }
    private static boolean isSigarLibraryOK() {
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
            boolean ok = false;
            for (Iterator<String> it = dirs.iterator(); it.hasNext(); ) {
                String dir = it.next();
                if (dir.length() < 1) {
                    continue;
                }
                Path path = Paths.get(dir);
                log.trace("Looking for '{}' in Path: {}", checkLib, path.toAbsolutePath());
                if (!Files.exists(path)) continue;
                Path found = Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.endsWith(Paths.get(checkLib))) {
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                log.trace("Found: {}", found.toString());
                ok = (!".".equals(found.toString()));
                if (ok) break;
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

}
