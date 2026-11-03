package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import edu.university.aggregator.cli.AggregatorCli;
import edu.university.aggregator.json.JsonCodec;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InvalidConfigurationTest {
    private final JsonCodec codec = new JsonCodec();

    @Test
    void collectsAllConfigurationErrorsBeforeAnyRequestStarts() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/work", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        Path config = Files.createTempFile("invalid-run", ".json");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/work";
            Files.writeString(config, """
                    {
                      "concurrency_limit": 0,
                      "workers": [
                        {"worker_id":"dup","url":"%s","method":"POST","timeout_ms":0,"extra":true},
                        {"worker_id":"dup","url":"not-a-url","method":"PUT","timeout_ms":"fast"}
                      ]
                    }
                    """.formatted(url));

            Invocation invocation = invoke("aggregate", "--config", config.toString());
            JsonNode errors = codec.readTree(invocation.stderr());
            List<String> fields = errors.path("errors").findValuesAsText("field");

            assertEquals(2, invocation.code());
            assertTrue(invocation.stdout().isBlank());
            assertEquals(List.of(
                    "concurrency_limit", "workers", "workers[0].method", "workers[0].timeout_ms",
                    "workers[0].extra", "workers[1].worker_id", "workers[1].url",
                    "workers[1].method", "workers[1].timeout_ms"), fields);
            assertEquals(0, requests.get());
        } finally {
            Files.deleteIfExists(config);
            server.stop(0);
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
