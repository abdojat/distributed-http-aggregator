package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.aggregator.cli.AggregatorCli;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.scenario.ScenarioCatalog;
import edu.university.aggregator.worker.WorkerFleet;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CliTest {
    private final JsonCodec codec = new JsonCodec();

    @Test
    void listsImplementedScenariosAsJsonOnStdout() throws Exception {
        Invocation invocation = invoke("list-scenarios");
        assertEquals(0, invocation.code());
        JsonNode json = codec.readTree(invocation.stdout());
        assertEquals("normal", json.get(0).path("name").asText());
        assertEquals("simultaneous", json.get(1).path("name").asText());
        assertEquals("", invocation.stderr());
    }

    @Test
    void demoWritesContractReportToStdoutOrSelectedFile() throws Exception {
        Invocation stdout = invoke("demo", "--scenario", "normal");
        assertEquals(0, stdout.code());
        assertEquals(3, codec.readTree(stdout.stdout()).path("worker_outcomes").size());
        assertEquals("", stdout.stderr());

        Path output = Files.createTempFile("aggregator-report", ".json");
        try {
            Invocation file = invoke("demo", "--scenario", "simultaneous", "--output", output.toString());
            assertEquals(0, file.code());
            assertEquals("", file.stdout());
            assertEquals(5, codec.readTree(Files.readString(output)).path("worker_outcomes").size());
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void allFailDemoReturnsValidReportAndSuccessExitCode() throws Exception {
            Invocation invocation = invoke("demo", "--scenario", "all-fail");
            assertEquals(0, invocation.code());
            JsonNode report = codec.readTree(invocation.stdout());
            assertEquals(5, report.path("worker_outcomes").size());
            assertEquals(0, report.path("aggregated_data").size());
            assertTrue(report.path("worker_outcomes").findValuesAsText("status")
                    .stream().noneMatch("success"::equals));
        }

    @Test
    void aggregateLoadsJsonAndContactsRunningWorkers() throws Exception {
            var scenario = ScenarioCatalog.require("normal");
            try (var fleet = WorkerFleet.start(scenario.workers(), codec)) {
                Path config = Files.createTempFile("run-normal", ".json");
                try {
                    Files.writeString(config, codec.write(fleet.runConfiguration(scenario.timeoutMs())));
                    Invocation invocation = invoke("aggregate", "--config", config.toString());
                    assertEquals(0, invocation.code());
                    assertEquals(3, codec.readTree(invocation.stdout()).path("worker_outcomes").size());
                    assertEquals("", invocation.stderr());
                } finally {
                    Files.deleteIfExists(config);
                }
            }
        }

    @Test
    void aggregateRejectsInvalidJsonBeforeWritingOutput() throws Exception {
            Path config = Files.createTempFile("invalid-run", ".json");
            try {
                Files.writeString(config, "{\"workers\": []}");
                Invocation invocation = invoke("aggregate", "--config", config.toString());
                assertEquals(2, invocation.code());
                assertTrue(invocation.stdout().isBlank());
                assertTrue(codec.readTree(invocation.stderr()).path("errors").size() > 0);
            } finally {
                Files.deleteIfExists(config);
        }
    }

    @Test
    @Timeout(5)
    void standaloneWorkerPrintsReadinessAndCleansUpOnInterruption() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> new AggregatorCli().run(new String[]{
                    "worker", "--id", "standalone", "--host", "127.0.0.1", "--port", "0",
                    "--delay-ms", "0", "--behavior", "success", "--data-json", "{\"value\":1}"},
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8)));
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (stdout.size() == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            JsonNode ready = codec.readTree(stdout.toString(StandardCharsets.UTF_8).trim());
            assertEquals("standalone", ready.path("worker_id").asText());
            assertTrue(ready.path("url").asText().startsWith("http://127.0.0.1:"));
            future.cancel(true);
        }
    }

    private Invocation invoke(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = new AggregatorCli().run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Invocation(code, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int code, String stdout, String stderr) {
    }
}
