package scala.bench;

import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.*;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GitWalker {
    public static GitWalkerResult walk(Repository repo) {
        Map<String, String> branchesMap = new HashMap<>();

        // forkPoint parameter generated manually with:
        // git log --topo-order --first-parent --oneline 2.13.x --not 2.12.x | tail -1
        // TODO find this with JGit.
        BatchPoints batchPoints = BatchPoints
                .database("scala_benchmark")
                .retentionPolicy("autogen")
                .consistency(InfluxDB.ConsistencyLevel.ALL)
                .build();
        createPoints("2.13.x", "fc1aea6712", batchPoints, repo, branchesMap);
        createPoints("2.12.x", "132a0587ab", batchPoints, repo, branchesMap);
        createPoints("v2.12.0", "05016d9035", batchPoints, repo, branchesMap);
        createPoints("2.11.x", "7ac15a1210", batchPoints, repo, branchesMap);
        createPoints("2.10.x", "cc672b023e", batchPoints, repo, branchesMap);
        createPoints("2.9.x", "33e1dac4e4", batchPoints, repo, branchesMap);
        return new GitWalkerResult(batchPoints, branchesMap, repo);
    }

    public void upload(BatchPoints batchPoints) {

    }

    private static BatchPoints createPoints(String branch, String forkPoint, BatchPoints batchPoints, Repository repo, Map<String, String> branchesMap) {
        try {
            ObjectId resolvedBranch = resolve(branch, repo);
            ObjectId resolvedForkPoint = resolve(forkPoint, repo);

            RevWalk walk = new RevWalk(repo);
            RevCommit revCommit = walk.parseCommit(resolvedBranch);
            boolean done = false;
            while (!done) {
                Escaper escaper = HtmlEscapers.htmlEscaper();
                String commiterName = revCommit.getCommitterIdent().getName();

                // workaround (?) https://github.com/influxdata/influxdb-java/issues/269
                String sanitizedMessage = revCommit.getFullMessage().replace("\\", "\\\\");
                String annotationHtml = String.format(
                        "<a href='https://github.com/scala/scala/commit/%s'>%s</a><p>%s<p><pre>%s</pre>",
                        revCommit.name(),
                        revCommit.name().substring(0, 10),
                        escaper.escape(commiterName),
                        escaper.escape(StringUtils.abbreviate(sanitizedMessage, 2048))
                );
                Point.Builder pointBuilder = Point.measurement("commit")
                        .time(revCommit.getCommitTime(), TimeUnit.SECONDS)
                        .tag("branch", branch)
                        .addField("sha", revCommit.name())
                        .addField("shortsha", revCommit.name().substring(0, 10))
                        .addField("user", commiterName)
                        .addField("message", sanitizedMessage)
                        .addField("annotationHtml", annotationHtml);
                List<String> tags = tagsOfCommit(walk, revCommit, repo);
                if (!tags.isEmpty()) {
                    pointBuilder.addField("tag", tags.get(0));
                }
                Point point = pointBuilder.build();

                branchesMap.put(revCommit.name(), branch);
                batchPoints.point(point);

                if (resolvedForkPoint.getName().equals(revCommit.getName())) {
                    done = true;
                } else {
                    revCommit = walk.parseCommit(revCommit.getParent(0));
                }
            }
        } catch (IOException | GitAPIException t) {
            throw new RuntimeException(t);
        }
        return batchPoints;
    }

    public static ObjectId resolve(String branch, Repository repo)  {
        RevWalk walk = new RevWalk(repo);
        try {
            ObjectId id = repo.resolve("origin/" + branch);
            if (id == null) {
                id = repo.resolve("scala/" + branch); // name of the remote on jenkins, TODO add to config
                if (id == null) {
                    ObjectId resolve = repo.resolve(branch);
                    if (resolve == null) {
                        throw new IllegalArgumentException("Cannot resolve: " + branch + " from " + repo);
                    }
                    return walk.parseCommit(resolve);
                } else {
                    return id;
                }
            } else {
                return id;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            walk.dispose();
        }
    }

    public static List<String> tagsOfCommit(RevWalk walk, RevCommit revCommit, Repository repo) throws IOException, GitAPIException {
        List<Ref> tagList = new Git(repo).tagList().call();
        List<String> tags = new ArrayList<String>();
        for (Ref tag : tagList) {
            RevObject object = walk.parseAny(tag.getObjectId());
            if (object instanceof RevTag) {
                if (((RevTag) object).getObject().equals(revCommit)) {
                    tags.add(((RevTag) object).getTagName());
                }
            } else if (object instanceof RevCommit) {
                if (object.equals(revCommit)) {
                    tags.add(tag.getName());
                }

            } else {
                // invalid
            }
        }
        return tags;
    }
}
