package scala.bench;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.format.OutputFormat;

import java.io.IOException;
import java.util.Collection;

public class DelegatingOutputFormat implements OutputFormat {
    private OutputFormat delegate;

    public DelegatingOutputFormat(OutputFormat delegate) {
        this.delegate = delegate;
    }

    @Override
    public void iteration(BenchmarkParams benchParams, IterationParams params, int iteration) {
        delegate.iteration(benchParams, params, iteration);
    }

    @Override
    public void iterationResult(BenchmarkParams benchParams, IterationParams params, int iteration, IterationResult data) {
        delegate.iterationResult(benchParams, params, iteration, data);
    }

    @Override
    public void startBenchmark(BenchmarkParams benchParams) {
        delegate.startBenchmark(benchParams);
    }

    @Override
    public void endBenchmark(BenchmarkResult result) {
        delegate.endBenchmark(result);
    }

    @Override
    public void startRun() {
        delegate.startRun();
    }

    @Override
    public void endRun(Collection<RunResult> result) {
        delegate.endRun(result);
    }

    @Override
    public void print(String s) {
        delegate.print(s);
    }

    @Override
    public void println(String s) {
        delegate.println(s);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void verbosePrintln(String s) {
        delegate.verbosePrintln(s);
    }

    @Override
    public void write(int b) {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }
}
