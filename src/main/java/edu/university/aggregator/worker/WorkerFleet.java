package edu.university.aggregator.worker;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorkerFleet implements AutoCloseable {
    private final List<WorkerServer> servers;
    private final List<SimulatedWorkerConfiguration> configurations;

    private WorkerFleet(
            List<WorkerServer> servers, List<SimulatedWorkerConfiguration> configurations) {
        this.servers = List.copyOf(servers);
        this.configurations = List.copyOf(configurations);
    }

    public static WorkerFleet start(List<SimulatedWorkerConfiguration> configurations, JsonCodec codec)
            throws Exception {
        List<WorkerServer> started = new ArrayList<>();
        try {
            for (SimulatedWorkerConfiguration configuration : configurations) {
                if (configuration.behavior() == edu.university.aggregator.model.WorkerBehavior.UNAVAILABLE) {
                    continue;
                }
                WorkerServer server = new WorkerServer(configuration, codec);
                server.start();
                started.add(server);
            }
            return new WorkerFleet(started, configurations);
        } catch (Exception exception) {
            closeReverse(started);
            throw exception;
        }
    }

    public RunConfiguration runConfiguration(long timeoutMs) {
        List<WorkerConfiguration> workers = configurations.stream()
                .map(configuration -> {
                    WorkerServer server = servers.stream()
                            .filter(candidate -> candidate.workerId().equals(configuration.workerId()))
                            .findFirst()
                            .orElse(null);
                    String url = server == null
                            ? "http://" + configuration.listenHost() + ":1/work"
                            : server.workUrl();
                    return new WorkerConfiguration(configuration.workerId(), url, "GET", timeoutMs);
                })
                .toList();
        return new RunConfiguration(workers.size(), workers);
    }

    public List<WorkerServer> servers() {
        return servers;
    }

    @Override
    public void close() throws Exception {
        closeReverse(servers);
    }

    private static void closeReverse(List<WorkerServer> servers) throws Exception {
        List<WorkerServer> reverse = new ArrayList<>(servers);
        Collections.reverse(reverse);
        Exception first = null;
        for (WorkerServer server : reverse) {
            try {
                server.close();
            } catch (Exception exception) {
                if (first == null) {
                    first = exception;
                } else {
                    first.addSuppressed(exception);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
