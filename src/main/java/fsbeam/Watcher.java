package fsbeam;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class Watcher {
    private final WatchService watcher;
    private final Set<Path> pathes;
    private final HashMap<WatchKey, Path> keys;
    private final Gatherer gatherer;
    private final Path baseDir;

    public Watcher(Path baseDir, Gatherer gatherer, WatchService watcher) {
        this.baseDir = baseDir;
        this.gatherer = gatherer;
        this.watcher = watcher;
        pathes = new HashSet<Path>();
        keys = new HashMap<WatchKey,Path>();
    }

    private void registerTree(final Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                WatchKey key = dir.register(watcher,
                                            ENTRY_CREATE,
                                            ENTRY_MODIFY,
                                            ENTRY_DELETE);
                Path prev = keys.put(key, path);
                if (prev != null) {
                    pathes.remove(prev);
                }
                pathes.add((Path) key.watchable());

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void watch() throws IOException, InterruptedException {
        waitAndRegisterBaseDir();

        while (!Thread.interrupted()) {
            WatchKey key = watcher.take();
            Path dir = (Path)key.watchable();

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    continue;
                }

                Path filename = (Path) event.context();
                Path fullPath = dir.resolve(filename);
                if (kind == ENTRY_CREATE) {
                    if (Files.isDirectory(fullPath, NOFOLLOW_LINKS)) {
                        registerTree(fullPath);
                    }
                }

                String entryName = baseDir.relativize(fullPath).toString();
                gatherer.addFile(entryName, fullPath, kind != ENTRY_DELETE);
            }
            if (!key.reset()) {
                Path path = keys.remove(key);
                pathes.remove(path);
                if (keys.isEmpty()) {
                    waitAndRegisterBaseDir();
                }
            }
        }
    }

    private void waitAndRegisterBaseDir() throws IOException, InterruptedException {
        Path path = baseDir;
        if (!Files.isDirectory(path)) {
            System.out.println("'" + path + "' is not a directory. Waiting for this to be true");
            while (!Files.isDirectory(path)) {
                Thread.sleep(FsBeamTool.WAIT_INTERVAL);
            }
        }
        boolean out = false;
        while (keys.isEmpty()) {
            registerTree(path);
            if (keys.isEmpty()) {
                if (!out) {
                    System.out.println("Couldn't watch any directories at '" + path + "'. Waiting");
                }
                out = true;
                Thread.sleep(FsBeamTool.WAIT_INTERVAL);
            }
        }
    }
}
