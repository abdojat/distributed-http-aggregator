package edu.university.aggregator.worker;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerBehavior;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

public final class WorkerServer implements AutoCloseable {
    static {
        System.setProperty("slf4j.internal.verbosity",
                System.getProperty("slf4j.internal.verbosity", "ERROR"));
    }

    private final SimulatedWorkerConfiguration configuration;
    private final JsonCodec codec;
    private final Server server;
    private final ServerConnector connector;
    private final ScheduledExecutorService scheduler;
    private final Set<ScheduledFuture<?>> scheduledResponses = ConcurrentHashMap.newKeySet();

    public WorkerServer(SimulatedWorkerConfiguration configuration, JsonCodec codec) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.server = new Server();
        this.connector = new ServerConnector(server);
        connector.setHost(configuration.listenHost());
        connector.setPort(configuration.listenPort());
        server.addConnector(connector);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "worker-delay-" + configuration.workerId());
            thread.setDaemon(true);
            return thread;
        });
        server.setHandler(new WorkerHandler());
    }

    public void start() throws Exception {
        server.start();
    }

    public int port() {
        int port = connector.getLocalPort();
        if (port <= 0) {
            throw new IllegalStateException("worker server is not started");
        }
        return port;
    }

    public String workUrl() {
        return "http://" + configuration.listenHost() + ":" + port() + "/work";
    }

    public String healthUrl() {
        return "http://" + configuration.listenHost() + ":" + port() + "/health";
    }

    public String workerId() {
        return configuration.workerId();
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    @Override
    public void close() throws Exception {
        scheduledResponses.forEach(task -> task.cancel(false));
        scheduledResponses.clear();
        scheduler.shutdownNow();
        if (!server.isStopped()) {
            server.stop();
        }
        server.destroy();
    }

    private final class WorkerHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"GET".equals(request.getMethod())) {
                Response.writeError(request, response, callback, 405);
                return true;
            }
            String path = Request.getPathInContext(request);
            if ("/health".equals(path)) {
                var body = JsonNodeFactory.instance.objectNode()
                        .put("worker_id", configuration.workerId())
                        .put("status", "ready");
                writeJson(response, callback, body);
                return true;
            }
            if ("/work".equals(path)) {
                ScheduledFuture<?> scheduled = scheduler.schedule(
                        () -> writeWorkResponse(response, callback),
                        configuration.delayMs(), TimeUnit.MILLISECONDS);
                scheduledResponses.add(scheduled);
                return true;
            }
            Response.writeError(request, response, callback, 404);
            return true;
        }

        private void writeWorkResponse(Response response, Callback callback) {
            try {
                if (configuration.behavior() == WorkerBehavior.HTTP_500) {
                    response.setStatus(500);
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE,
                            "application/json; charset=utf-8");
                    Content.Sink.write(response, true,
                            "{\"error\":\"simulated worker failure\"}", callback);
                } else if (configuration.behavior() == WorkerBehavior.MALFORMED) {
                    response.setStatus(200);
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE,
                            "application/json; charset=utf-8");
                    Content.Sink.write(response, true, "{\"worker_id\":", callback);
                } else {
                    var body = JsonNodeFactory.instance.objectNode()
                            .put("worker_id", configuration.workerId())
                            .set("data", configuration.data());
                    writeJson(response, callback, body);
                }
            } catch (Exception exception) {
                callback.failed(exception);
            }
        }

        private void writeJson(Response response, Callback callback, Object body) {
            try {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json; charset=utf-8");
                Content.Sink.write(response, true, codec.write(body), callback);
            } catch (Exception exception) {
                callback.failed(exception);
            }
        }
    }
}
