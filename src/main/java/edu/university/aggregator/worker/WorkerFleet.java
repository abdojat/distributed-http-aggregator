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

    private WorkerFleet(List<WorkerServer> servers) {
        this.servers = List.copyOf(servers);
    }

    public static WorkerFleet start(List<SimulatedWorkerConfiguration> configurations, JsonCodec codec)
            throws Exception {
        List<WorkerServer> started = new ArrayList<>();
        try {
            for (SimulatedWorkerConfiguration configuration : configurations) {
                WorkerServer server = new WorkerServer(configuration, codec);
                server.start();
                started.add(server);
            }
            return new WorkerFleet(started);
        } catch (Exception exception) {
            closeReverse(started);
            throw exception;
        }
    }

    public RunConfiguration runConfiguration(long timeoutMs) {
        List<WorkerConfiguration> workers = servers.stream()
                .map(server -> new WorkerConfiguration(server.workerId(), server.workUrl(), "GET", timeoutMs))
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
