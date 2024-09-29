package io.pravega.example.statesynchronizer;

import io.pravega.client.ClientConfig;
import io.pravega.client.SynchronizerClientFactory;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.admin.StreamManager;
import org.apache.commons.cli.*;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class StateSynchronizerBenchmark {

    private static final String DEFAULT_SCOPE = "example";
    private static final String DEFAULT_CONFIG_NAME = "someConfig";
    private static final URI DEFAULT_CONTROLLER_URI = URI.create("tcp://127.0.0.1:9090");
    private static final int DEFAULT_NUM_THREADS = 2;
    private static final int DEFAULT_UPDATES_PER_SECOND = 1;
    private static final int DEFAULT_BENCHMARK_DURATION = 10;

    public static void main(String[] args) throws IOException, InterruptedException {
        Options options = getOptions();
        CommandLine cmd = null;
        try {
            cmd = parseCommandLineArgs(options, args);
        } catch (ParseException e) {
            System.out.format("%s.%n", e.getMessage());
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("StateSynchronizerBenchmark", options);
            System.exit(1);
        }

        // Setup Pravega connection and stream
        final String scope = cmd.getOptionValue("scope") == null ? DEFAULT_SCOPE : cmd.getOptionValue("scope");
        final String streamName = cmd.getOptionValue("name") == null ? DEFAULT_CONFIG_NAME : cmd.getOptionValue("name");
        final URI controllerURI = cmd.getOptionValue("uri") == null ?
                DEFAULT_CONTROLLER_URI : URI.create(cmd.getOptionValue("uri"));
        // Benchmark parameters
        final int numThreads = cmd.getOptionValue("threads") == null ?
                DEFAULT_NUM_THREADS : Integer.valueOf(cmd.getOptionValue("threads")); // Number of threads
        final int updatesPerSecond = cmd.getOptionValue("throughput") == null ?
                DEFAULT_UPDATES_PER_SECOND : Integer.valueOf(cmd.getOptionValue("throughput")); // Update rate per second
        final int benchmarkDurationInSeconds = cmd.getOptionValue("duration") == null ?
                DEFAULT_BENCHMARK_DURATION : Integer.valueOf(cmd.getOptionValue("duration"));; // Duration for benchmark

        // Create scope and stream
        StreamManager streamManager = StreamManager.create(controllerURI);
        streamManager.createScope(scope);
        streamManager.createStream(scope, streamName, StreamConfiguration.builder().build());

        // Create client and synchronizer
        SynchronizerClientFactory clientFactory = SynchronizerClientFactory.withScope(scope,
                ClientConfig.builder().controllerURI(controllerURI).build());
        SharedConfig<String, String> config = new SharedConfig<>(clientFactory, streamManager, scope, "test");

        // Create log files
        System.err.println("EXPERIMENT WITH " + numThreads + " THREADS AND " + updatesPerSecond + " UPDATES/SECOND.");
        TimeUnit.MILLISECONDS.sleep(1000);
        String writeLatencyFileName = "nt_" + numThreads + "_us_" + updatesPerSecond + "_write_latencies.csv";
        String readLatencyFileName = "nt_" + numThreads + "_us_" + updatesPerSecond + "_read_latencies.csv";
        String conflictsFileName = "nt_" + numThreads + "_us_" + updatesPerSecond + "_conflicts.csv";

        // Create executors and thread conflict counters.
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger[] threadConflicts = new AtomicInteger[numThreads]; // Per-thread conflict tracking
        for (int i = 0; i < numThreads; i++) {
            threadConflicts[i] = new AtomicInteger(0);
        }

        // CSV Writers for latencies and conflicts
        try (BufferedWriter latencyWriter = new BufferedWriter(new FileWriter(writeLatencyFileName));
             BufferedWriter latencyReader = new BufferedWriter(new FileWriter(readLatencyFileName));
             BufferedWriter conflictWriter = new BufferedWriter(new FileWriter(conflictsFileName))) {

            // Write headers to the CSV files
            latencyWriter.write("ThreadId,WriteLatency(ms)\n");
            latencyReader.write("ThreadId,ReadLatency(ms)\n");
            conflictWriter.write("ThreadId,Conflicts\n");

            // Initialize shared counter
            String key = "counter";
            config.putProperty(key, "0");

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // Add some initial random wait to the start of process execution
                        int iniWait = new Random().nextInt(500);
                        System.err.println(Thread.currentThread() + " waiting before start for " + iniWait);
                        TimeUnit.MILLISECONDS.sleep(iniWait);

                        // Run workload
                        for (int j = 0; j < updatesPerSecond * benchmarkDurationInSeconds; j++) {
                            try {
                                // Get the current value for the counter and log the time.
                                long startTime = System.nanoTime();
                                int currentValue = Integer.valueOf(config.getProperty(key));
                                long endTime = System.nanoTime();
                                long latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                                latencyReader.write(threadId + "," + latency + "\n");

                                // Perform state update conditional to the old value and log the write latency.
                                startTime = System.nanoTime();
                                boolean replaced = config.replaceProperty(key, String.valueOf(currentValue), String.valueOf(currentValue + 1));
                                endTime = System.nanoTime();
                                latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                                latencyWriter.write(threadId + "," + latency + "\n");
                                if (!replaced) {
                                    threadConflicts[threadId].incrementAndGet(); // Track conflicts for the thread
                                }
                            } catch (Exception e) {
                                System.err.println(e);
                            }
                            // Sleep to match the update rate
                            TimeUnit.MILLISECONDS.sleep(1000 / updatesPerSecond);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // Shutdown the executor after benchmark completion
            executor.shutdown();
            try {
                if (!executor.awaitTermination(benchmarkDurationInSeconds, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }

            // Write the conflicts per thread into the conflict CSV file
            for (int i = 0; i < numThreads; i++) {
                conflictWriter.write(i + "," + threadConflicts[i].get() + "\n");
            }
        }


        // Clean up
        clientFactory.close();
        streamManager.close();
    }

    private static Options getOptions() {
        final Options options = new Options();
        options.addOption("s", "scope", true, "The scope (namespace) of the Shared Config.");
        options.addOption("n", "stream", true, "The name of the Shared Config.");
        options.addOption("u", "uri", true, "The URI to the Pravega controller in the form tcp://host:port");
        options.addOption("t", "threads", true, "Number of threads writing to Pravega on the shared counter.");
        options.addOption("tp", "throughput", true, "Updates per second per thread.");
        options.addOption("d", "duration", true, "Duration of the benchmark in seconds.");
        return options;
    }

    private static CommandLine parseCommandLineArgs(Options options, String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        return cmd;
    }
}
