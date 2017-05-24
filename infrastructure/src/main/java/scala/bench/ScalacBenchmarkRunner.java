package scala.bench;

import org.eclipse.jgit.lib.Repository;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class ScalacBenchmarkRunner {
    private static String corpusVersion(String source) throws IOException {
        Repository repo = GitFactory.openGit();
        if (!repo.getDirectory().exists())
            return null; // For when `git.localdir` is not defined, `corpusVersion` will be `latest`

        String scalaRef = System.getProperty("scalaRef");
        if (scalaRef == null)
            throw new RuntimeException("Please provide -DscalaRef=...");

        switch(source) {
            case "vector":
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
        }

        return null;
    }

    public static Options setCorpusVersion(CommandLineOptions clOpts) throws IOException {
        Collection<String> sources = clOpts.getParameter("source").orElse(Collections.emptyList());
        String source = null;
        if (sources.size() == 1) source = sources.iterator().next();

        String corpusVer = corpusVersion(source);
        if (corpusVer == null) return clOpts;
        else {
            return new OptionsBuilder()
                    .parent(clOpts)
                    .param("corpusVersion", corpusVersion(source))
                    .build();
        }
    }

    public static void main(String[] args) throws Exception {
        new Runner(setCorpusVersion(new CommandLineOptions(args))).run();
    }
}
