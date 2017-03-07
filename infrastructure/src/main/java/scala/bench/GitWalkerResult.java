package scala.bench;

import org.eclipse.jgit.lib.Repository;
import org.influxdb.dto.BatchPoints;

import java.util.Map;

public class GitWalkerResult {

    public BatchPoints getBatchPoints() {
        return batchPoints;
    }

    public Map<String, String> getBranchesMap() {
        return branchesMap;
    }

    public BatchPoints batchPoints;
    private Map<String, String> branchesMap;
    private Repository repo;

    public GitWalkerResult(BatchPoints batchPoints, Map<String, String> branchesMap, Repository repo) {

        this.batchPoints = batchPoints;
        this.branchesMap = branchesMap;
        this.repo = repo;
    }
    public String branchOfRef(String scalaVersion) {
        return branchesMap.get(GitWalker.resolve(scalaVersion, repo).getName());
    }
}
