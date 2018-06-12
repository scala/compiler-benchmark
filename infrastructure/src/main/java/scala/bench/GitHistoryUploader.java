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

        try {
            System.out.println("Walking Git history...");
            GitWalkerResult result = GitWalker.walk(gitRepo);
            InfluxDB influxDB = connectDb();
            try {
                System.out.println("Writing results to DB...");
                influxDB.write(result.getBatchPoints());
                System.out.println("Done.");
            } finally {
                influxDB.close();
            }
        } finally {
            gitRepo.close();
        }
    }
}
