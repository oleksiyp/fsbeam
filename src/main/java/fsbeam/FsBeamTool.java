package fsbeam;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchService;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class FsBeamTool {
    public static final long WAIT_INTERVAL = 1000L;
    @Argument(index=0, required=true)
    public Path baseDir;

    @Argument(index=1)
    public String command;

    Gatherer gatherer;

    @Option(name = "waitTime")
    private int waitTime = 3000;
    private Watcher watcher;

    public static void main(String[] args) {
        CmdLineParser.registerHandler(Path.class, PathOptionHandler.class);
        CmdLineParser parser = null;
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            FsBeamTool tool = new FsBeamTool();
            parser = new CmdLineParser(tool);
            parser.parseArgument(args);
            tool.init(watchService);
            tool.run();
        } catch (CmdLineException e) {
            System.err.println(e);
            parser.printUsage(System.err);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void init(WatchService watchService) {
        gatherer = new Gatherer(waitTime);
        watcher = new Watcher(baseDir, gatherer, watchService);
    }


    public void run() throws Exception {
        watcher.watch();
    }

    public static class PathOptionHandler extends OptionHandler<Path> {

        public PathOptionHandler(CmdLineParser parser,
                                 OptionDef option,
                                 Setter<? super Path> setter) {
            super(parser, option, setter);
        }

        @Override public int parseArguments(Parameters params) throws CmdLineException {
            setter.addValue(FileSystems.getDefault().getPath(params.getParameter(0)));
            return 0;
        }

        @Override public String getDefaultMetaVariable() {
            return "PATH";
        }
    }

}
