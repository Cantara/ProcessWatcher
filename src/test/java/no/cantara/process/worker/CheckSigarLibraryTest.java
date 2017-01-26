package no.cantara.process.worker;

import no.cantara.process.util.FileSystemSupport;
import no.cantara.process.util.Sigar_LD_LIBRARY_PATH_Hack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * Created by oranheim on 24/01/2017.
 */
public class CheckSigarLibraryTest {

    private static final Logger log = LoggerFactory.getLogger(CheckSigarLibraryTest.class);
    String checkLib = null;

    @Test
    public void testIfSigarOnLibraryPathExists() throws Exception {
        if (FileSystemSupport.isLinux()) {
            checkLib = FileSystemSupport.LINUX_LIB;
        } else if (FileSystemSupport.isMacOS()) {
            checkLib = FileSystemSupport.MACOS_LIB;
        } else {
            return;
        }

        List<String> dirs = Arrays.asList(System.getProperty("java.library.path").split(":"));
        System.out.println(System.getProperty("java.library.path"));
        boolean ok = false;
        for(Iterator<String> it = dirs.iterator(); it.hasNext(); ) {
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

        assertTrue(ok);
    }

    @Test
    public void testHack() {
        Sigar_LD_LIBRARY_PATH_Hack.aplyHack();
        assertTrue(Sigar_LD_LIBRARY_PATH_Hack.isSigarLibraryOK());
    }
}
