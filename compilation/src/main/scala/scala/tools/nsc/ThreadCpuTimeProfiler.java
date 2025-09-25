package scala.tools.nsc;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.*;
import org.openjdk.jmh.util.ScoreFormatter;

import java.util.*;
import java.util.concurrent.TimeUnit;

// Assumptions: Threads are not created or destroyed in the steady state
public class ThreadCpuTimeProfiler implements InternalProfiler {

    private ExtendedThreadMxBean threadMx = ExtendedThreadMxBean.proxy;
    private HashMap<Long, TheadSnapshot> before;

    public ThreadCpuTimeProfiler() {
        if (threadMx.isThreadCpuTimeSupported()) {
            threadMx.setThreadCpuTimeEnabled(true);
        } // TODO else signal failure
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        this.before = snapshot();
    }

    private HashMap<Long, TheadSnapshot> snapshot() {
        HashMap<Long, TheadSnapshot> snaps = new HashMap<>();
        for (long id: threadMx.getAllThreadIds() ) {
            if (id != Thread.currentThread().getId()) {
                snaps.put(id, new TheadSnapshot(threadMx.getThreadCpuTime(id), threadMx.getThreadUserTime(id)));
            }
        }
        return snaps;
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        List<NormTimeResult> results = new ArrayList<>();

        HashMap<Long, TheadSnapshot> after = snapshot();
        HashSet<Long> commonThreadIds = new HashSet<>(before.keySet());
        commonThreadIds.retainAll(after.keySet());
        long deltaCpuTime = 0;
        long deltaUserTime = 0;
        for (long id: commonThreadIds) {
            TheadSnapshot beforeThreadSnapshot = before.get(id);
            TheadSnapshot afterThreadSnapshot = after.get(id);
            deltaCpuTime += afterThreadSnapshot.threadCpuTime - beforeThreadSnapshot.threadCpuTime;
            deltaUserTime += afterThreadSnapshot.threadUserCpuTime - beforeThreadSnapshot.threadUserCpuTime;
        }
        long allOps = result.getMetadata().getAllOps();
        if (deltaCpuTime != 0) {
            NormTimeResult normTimeResult = new NormTimeResult("threads.cpu.time.norm", TimeUnit.NANOSECONDS.toMillis(deltaCpuTime), allOps);
            results.add(normTimeResult);
        }
        if (deltaUserTime != 0) {
            NormTimeResult normTimeResult = new NormTimeResult("threads.user.time.norm", TimeUnit.NANOSECONDS.toMillis(deltaUserTime), allOps);
            results.add(normTimeResult);
        }
        // TODO warn about threads created or destroyed during the iteration.
        before = after;
        return results;
    }

    @Override
    public String getDescription() {
        return "CPU time profiler based on ThreadMXBean";
    }

    static class TheadSnapshot {
        private long threadCpuTime;
        private long threadUserCpuTime;
        TheadSnapshot(long threadCpuTime, long threadUserCpuTime) {
            this.threadCpuTime = threadCpuTime;
            this.threadUserCpuTime = threadUserCpuTime;
        }
    }

    // Below are some ugly contortions to avoid the average of averages error you'd get if we just used a
    // `ScalarResult(timeMs / allOps)`. There is probably a much nicer way to do this in JMH.
    static class NormTimeResult extends Result<NormTimeResult> {
        private static final long serialVersionUID = -1262685915873231436L;

        private String prefix;
        private final long scalar;
        private final long ops;

        public NormTimeResult(String prefix, long cycles, long instructions) {
            super(ResultRole.SECONDARY, prefix, of(Double.NaN), "---", AggregationPolicy.AVG);
            this.prefix = prefix;
            this.scalar = cycles;
            this.ops = instructions;
        }

        @Override
        protected Aggregator<NormTimeResult> getThreadAggregator() {
            return new Aggregator<NormTimeResult>() {
                @Override
                public NormTimeResult aggregate(Collection<NormTimeResult> results) {
                    if (results.size() != 1) {
                        throw new UnsupportedOperationException();
                    } else {
                        return results.iterator().next();
                    }
                }
            };
        }

        @Override
        protected Aggregator<NormTimeResult> getIterationAggregator() {
            NormTimeResultAggregator normTimeResultAggregator = new NormTimeResultAggregator(prefix);
            return (Aggregator<NormTimeResult>) (Object) normTimeResultAggregator;
        }

        @Override
        protected Collection<? extends Result> getDerivativeResults() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return String.format("%s %s", ScoreFormatter.format(1.0 * scalar / ops), prefix);
        }

        @Override
        public String extendedInfo() {
            return "";
        }
    }

    static class NormTimeResultAggregator implements Aggregator<Result> {

        private String prefix;

        NormTimeResultAggregator(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Result aggregate(Collection<Result> results) {
            long scalar = 0;
            long ops = 0;
            for (Result r : results) {
                scalar += ((NormTimeResult) r).scalar;
                ops += ((NormTimeResult) r).ops;
            }
            return new ScalarResult(prefix, 1.0 * scalar / ops, "ms/op", AggregationPolicy.AVG);
        }
    }
}
