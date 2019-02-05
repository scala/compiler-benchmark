package scala.bench;

import org.eclipse.jgit.lib.Repository;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class ScalacBenchmarkRunner {
    private static String corpusVersion(String source) throws IOException {
        Repository repo = GitFactory.openGit();
        if (!repo.getDirectory().exists())
            return null; // For when `git.localdir` is not defined, `corpusVersion` will be `latest`

        String scalaRef = System.getProperty("scalaRef");
        if (scalaRef == null)
            throw new RuntimeException("Please provide -DscalaRef=...");

        // TODO: git fetch --tags
        try {

            switch (source) {
                case "vector":
                    if (GitWalker.isAncestor("6ff3fac", scalaRef, repo)) // 2.13 collections were merged in 6ff3fac
                        return "e3df10a";
                    if (GitWalker.isAncestor("v2.11.0", scalaRef, repo))
                        return "fb04376"; // compiles with 2.11.0, but not with 2.10.6
                    break;

                case "scalap":
                    if (GitWalker.isAncestor("v2.10.1", scalaRef, repo))
                        return "a8c43dc"; // compiles with 2.10.1, but not with 2.9.3
                    break;

                case "better-files":
                    if (GitWalker.isAncestor("v2.10.2", scalaRef, repo))
                        return "a45f905"; // compiles with 2.10.2, but not with 2.10.1
                    break;

                case "scala":
                    if (GitWalker.isAncestor("df29ebb", scalaRef, repo))
                        return "df29ebb";
                    if (GitWalker.isAncestor("v2.11.5", scalaRef, repo))
                        return "21d12e9";
                    break;
            }
        } catch (IllegalArgumentException iae){
            // ignore, we might be on a dotty commit, just pick latest
        }

        return null;
    }

    public static Options setParameters(CommandLineOptions clOpts) throws IOException {
        ChainedOptionsBuilder b = new OptionsBuilder()
                .parent(clOpts)
                .param("scalaVersion", System.getProperty("scalaVersion"));

        Collection<String> sources = clOpts.getParameter("source").orElse(Collections.emptyList());
        String source = null;
        if (sources.size() == 1) source = sources.iterator().next();
        if (source != null) {
            String corpusVer = corpusVersion(source);
            if (corpusVer != null)
              b.param("corpusVersion", corpusVersion(source));
        }
        return b.build();
    }

    public static void main(String[] args) throws Exception {
        CommandLineOptions opts = new CommandLineOptions(args);
        // Support `-h/-l/lp/lprof/lrf`.
        if (opts.shouldHelp() || opts.shouldList() || opts.shouldListProfilers() || opts.shouldListResultFormats() || opts.shouldListWithParams()) {
            org.openjdk.jmh.Main.main(args);
            return;
        }
        if (opts.verbosity().orElse(VerboseMode.NORMAL) == VerboseMode.EXTRA) {
            printCommandLine(args);
        }
        new Runner(setParameters(opts)).run();
    }

    private static void printCommandLine(String[] args) {
        StringBuilder commandLine = new StringBuilder();
        String inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().collect(Collectors.joining(" "));
        commandLine.append(inputArgs).append(" ");
        commandLine.append("-classpath ").append(System.getProperty("java.class.path")).append(" ");
        commandLine.append(ScalacBenchmarkRunner.class.getName()).append(" ");
        for (String arg : args) {
            commandLine.append(arg).append(" ");
        }
        System.out.println(commandLine);
    }
}
