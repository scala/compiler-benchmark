package scala.bench;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.nio.file.Paths;

public class GitFactory {
    public static Repository openGit() {
        Config conf = ConfigFactory.load();
        String gitLocalDir = conf.getString("git.localdir");
        try {
            return new FileRepositoryBuilder().setGitDir(Paths.get(gitLocalDir).resolve(".git").toFile())
                    .readEnvironment() // Do we need this?
                    .findGitDir()
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
