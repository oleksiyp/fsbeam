package fsbeam;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Gatherer implements Runnable {
    private final Map<String, Path> files = new HashMap<String, Path>();
    private final ScheduledExecutorService executor;
    {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    private ScheduledFuture<?> future;
    private Iterator<String> iterator;
    private final long waitTime;

    public Gatherer(long waitTime) {
        this.waitTime = waitTime;
    }

    public void addFile(String zipEntryName, Path path, boolean addFlag) {
        boolean hasFiles;
        synchronized (files) {
            if (addFlag) {
                files.put(zipEntryName, path);
            } else {
                files.remove(zipEntryName);
                System.out.println(files);
            }
            hasFiles = !files.isEmpty();
            iterator = null;
        }
        if (future != null) {
            future.cancel(true);
        }
        if (hasFiles) {
            future = executor.schedule(this,
                                       waitTime,
                                       TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        try {
            pack();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pack() throws IOException {
        synchronized (files) {
            iterator = files.keySet().iterator();
        }
        boolean done = false;
        String name = System.currentTimeMillis() + ".zip";
        Path zip = FileSystems.getDefault().getPath(name);
        try (OutputStream out = Files.newOutputStream(zip);
                    ZipOutputStream zipOut = new ZipOutputStream(out)) {
            while (!Thread.interrupted()) {
                String entryName;
                Path path;
                synchronized (files) {
                    if (iterator == null) {
                        break;
                    }
                    if (!iterator.hasNext()) {
                        files.clear();
                        done = true;
                        break;
                    }
                    entryName = iterator.next();
                    path = files.get(entryName);
                }

                if (!Files.isRegularFile(path)) {
                    continue;
                }

                ZipEntry zipEntry = new ZipEntry(entryName);
                zipOut.putNextEntry(zipEntry);
                Files.copy(path, zipOut);
                zipOut.closeEntry();
                System.out.println(entryName);
            }
        } finally {
            if (!done) {
                Files.delete(zip);
            }
        }
        if (done) {
            System.out.println(name);
        }
    }
}
