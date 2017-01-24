package no.cantara.process.watcher;

import no.cantara.process.FileSystemSupport;
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
    private static final String MACOS_LIB = "libsigar-universal64-macosx.dylib";

    @Test
    public void testIfSigarOnMacOSLibraryExists() throws Exception {
        if (!FileSystemSupport.isMacOS()) return;

        List<String> dirs = Arrays.asList(System.getProperty("java.library.path").split(":"));
        boolean ok = false;
        for(Iterator<String> it = dirs.iterator(); it.hasNext(); ) {
            String dir = it.next();
            Path path = Paths.get(dir);
            log.trace("Looking for '{}' in Path: {}", MACOS_LIB, path.toAbsolutePath());
            if (!Files.exists(path)) continue;
            Path found = Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.endsWith(Paths.get(MACOS_LIB))) {
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            log.trace("Match: {}", found.toString());
            ok = (!".".equals(found.toString()));
            if (ok) break;
        }

        assertTrue(ok);
    }
}
