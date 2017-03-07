package scala.bench;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.influxdb.InfluxDB;

import java.io.IOException;

import static scala.bench.Database.connectDb;
import static scala.bench.GitFactory.openGit;

public class GitHistoryUploader {
    public static void main(String[] args) throws IOException, GitAPIException {
        Repository gitRepo = openGit();
        InfluxDB influxDB = connectDb();

        try {
            GitWalkerResult result = GitWalker.walk(gitRepo);
            influxDB.write(result.getBatchPoints());
        } finally {
            influxDB.close();
            gitRepo.close();
        }
    }
}
