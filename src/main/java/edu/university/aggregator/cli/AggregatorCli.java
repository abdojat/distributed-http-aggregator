package edu.university.aggregator.cli;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.config.RunConfigurationValidator;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.ValidationError;
import edu.university.aggregator.model.ValidationErrorReport;
import edu.university.aggregator.model.WorkerBehavior;
import edu.university.aggregator.scenario.ScenarioCatalog;
import edu.university.aggregator.scenario.ScenarioDefinition;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import edu.university.aggregator.worker.WorkerServer;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class AggregatorCli {
    private final JsonCodec codec = new JsonCodec();

    public int run(String[] args, PrintStream stdout, PrintStream stderr) {
        try {
            if (args.length == 0) {
                throw new IllegalArgumentException("A command is required");
            }
            return switch (args[0]) {
                case "list-scenarios" -> listScenarios(stdout);
                case "demo" -> demo(slice(args), stdout, stderr);
                case "aggregate" -> aggregate(slice(args), stdout, stderr);
                case "worker" -> worker(slice(args), stdout);
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            };
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 130;
        } catch (Exception exception) {
            stderr.println(exception.getMessage());
            return 1;
        }
    }

    private int listScenarios(PrintStream stdout) throws Exception {
        List<Map<String, String>> records = ScenarioCatalog.all().stream()
                .map(scenario -> Map.of("name", scenario.name(), "description", scenario.description()))
                .toList();
        stdout.println(codec.write(records));
        return 0;
    }

    private int demo(String[] args, PrintStream stdout, PrintStream stderr) throws Exception {
        Map<String, String> options = options(args);
        ScenarioDefinition scenario = ScenarioCatalog.require(require(options, "--scenario"));
        try (WorkerFleet fleet = WorkerFleet.start(scenario.workers(), codec);
             AggregationService service = AggregationService.create(scenario.workers().size(), codec)) {
            var report = service.aggregate(fleet.runConfiguration(scenario.timeoutMs())).join();
            String json = codec.writeAggregationReport(report);
            writeDiagnostics(report, stderr);
            String output = options.get("--output");
            if (output == null) {
                stdout.println(json);
            } else {
                Files.writeString(Path.of(output), json + System.lineSeparator(), StandardCharsets.UTF_8);
            }
        }
        return 0;
    }

    private int aggregate(String[] args, PrintStream stdout, PrintStream stderr) throws Exception {
        Map<String, String> options = options(args);
        Path configPath = Path.of(require(options, "--config"));
        String json;
        try {
            json = Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            stderr.println(exception.getMessage());
            return 1;
        }

        JsonNode root;
        try {
            root = codec.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return validationFailure(
                    List.of(new ValidationError("$", "invalid_json", "configuration is not valid JSON")),
                    stderr);
        }

        List<ValidationError> errors = new RunConfigurationValidator().validate(root);
        if (!errors.isEmpty()) {
            return validationFailure(errors, stderr);
        }

        RunConfiguration configuration;
        try {
            configuration = codec.readRunConfiguration(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
            return validationFailure(
                    List.of(new ValidationError("$", "invalid_configuration",
                            "configuration does not match the run contract")),
                    stderr);
        }

        try (AggregationService service = AggregationService.create(configuration.concurrencyLimit(), codec)) {
            var report = service.aggregate(configuration).join();
            String output = codec.writeAggregationReport(report);
            writeOutput(output, options.get("--output"), stdout);
            writeDiagnostics(report, stderr);
        }
        return 0;
    }

    private int validationFailure(List<ValidationError> errors, PrintStream stderr) throws Exception {
        stderr.println(codec.writeValidationErrors(new ValidationErrorReport(errors)));
        return 2;
    }

    private static void writeOutput(String output, String path, PrintStream stdout) throws Exception {
        if (path == null) {
            stdout.println(output);
        } else {
            Files.writeString(Path.of(path), output + System.lineSeparator(), StandardCharsets.UTF_8);
        }
    }

    private void writeDiagnostics(
            edu.university.aggregator.model.AggregationReport report, PrintStream stderr) {
        report.workerOutcomes().stream()
                .filter(outcome -> outcome.status() != edu.university.aggregator.model.WorkerStatus.SUCCESS)
                .forEach(outcome -> stderr.printf(
                        "worker=%s status=%s duration_ms=%.3f failure_code=%s reason=%s%n",
                        outcome.workerId(), outcome.status().value(), outcome.durationMs(),
                        outcome.failureCode().value(), outcome.failureReason()));
    }

    private int worker(String[] args, PrintStream stdout) throws Exception {
        Map<String, String> options = options(args);
        String id = require(options, "--id");
        String host = require(options, "--host");
        int port = Integer.parseInt(require(options, "--port"));
        long delayMs = Long.parseLong(require(options, "--delay-ms"));
        WorkerBehavior behavior = WorkerBehavior.fromValue(require(options, "--behavior"));
        JsonNode data = options.containsKey("--data-json")
                ? codec.readTree(options.get("--data-json"))
                : codec.readTree("null");
        try (WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                id, behavior, delayMs, data, host, port), codec)) {
            server.start();
            Map<String, Object> ready = new LinkedHashMap<>();
            ready.put("worker_id", id);
            ready.put("url", server.workUrl());
            ready.put("status", "ready");
            stdout.println(codec.write(ready));
            new CountDownLatch(1).await();
        }
        return 0;
    }

    private static Map<String, String> options(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Options must be name/value pairs");
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            options.put(args[index], args[index + 1]);
        }
        return options;
    }

    private static String require(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing option " + name);
        }
        return value;
    }

    private static String[] slice(String[] args) {
        return java.util.Arrays.copyOfRange(args, 1, args.length);
    }
}
