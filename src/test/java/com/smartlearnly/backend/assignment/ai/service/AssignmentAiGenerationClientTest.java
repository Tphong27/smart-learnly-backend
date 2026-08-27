package com.smartlearnly.backend.assignment.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.assignment.ai.config.AssignmentAiDraftProperties;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Kiểm thử fallback model riêng cho client Gemini của Assignment AI. */
class AssignmentAiGenerationClientTest {

    /** Khi primary model không khả dụng với Gemini, client phải thử fallback model đã cấu hình. */
    @Test
    void generateShouldFallbackWhenPrimaryModelIsUnavailable() throws IOException {
        List<String> requestedPaths = new ArrayList<>();
        HttpServer server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            if (path.contains("gemini-2.5-flash")) {
                writeJson(exchange, 404, """
                        {"error":{"message":"models/gemini-2.5-flash is not found for API version v1beta, or is not supported for generateContent.","status":"NOT_FOUND"}}
                        """);
                return;
            }
            writeJson(exchange, 200, """
                    {"candidates":[{"content":{"parts":[{"text":"fallback draft"}]}}]}
                    """);
        });

        try {
            AssignmentAiGenerationClient client = client(
                    server,
                    "gemini-2.5-flash",
                    "gemini-3.5-flash"
            );

            String output = client.generate(List.of(Map.of("type", "text", "text", "Create 1 assignment.")));

            assertThat(output).isEqualTo("fallback draft");
            assertThat(requestedPaths).containsExactly(
                    "/v1beta/models/gemini-2.5-flash:generateContent",
                    "/v1beta/models/gemini-3.5-flash:generateContent"
            );
        }
        finally {
            server.stop(0);
        }
    }

    /** Lỗi 400 do payload không hợp lệ không được che bằng fallback model. */
    @Test
    void generateShouldNotFallbackForGenericBadRequest() throws IOException {
        List<String> requestedPaths = new ArrayList<>();
        HttpServer server = startServer(exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            writeJson(exchange, 400, """
                    {"error":{"message":"Request body is invalid.","status":"INVALID_ARGUMENT"}}
                    """);
        });

        try {
            AssignmentAiGenerationClient client = client(
                    server,
                    "gemini-2.5-flash",
                    "gemini-3.5-flash"
            );

            assertThatThrownBy(() -> client.generate(List.of(Map.of("type", "text", "text", "Create 1 assignment."))))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                        assertThat(exception.getMessage()).contains("AI draft provider rejected the request");
                    });
            assertThat(requestedPaths).containsExactly("/v1beta/models/gemini-2.5-flash:generateContent");
        }
        finally {
            server.stop(0);
        }
    }

    /** Tạo client trỏ tới HTTP server nội bộ để test không phụ thuộc mạng hoặc API key thật. */
    private AssignmentAiGenerationClient client(HttpServer server, String model, String fallbackModel) {
        AssignmentAiDraftProperties properties = new AssignmentAiDraftProperties();
        properties.setApiBaseUrl("http://localhost:" + server.getAddress().getPort() + "/v1beta");
        properties.setTimeout(Duration.ofSeconds(2));

        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveAssignmentAiSettings()).thenReturn(new AssignmentAiSettings(
                true,
                "gemini",
                "test-key",
                model,
                fallbackModel,
                2
        ));
        return new AssignmentAiGenerationClient(properties, settingsService);
    }

    /** Khởi động server HTTP nhẹ cho mỗi test case. */
    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", handler::handle);
        server.start();
        return server;
    }

    /** Ghi JSON response ngắn về client thử nghiệm. */
    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Hàm xử lý request tối giản để test luồng HTTP nội bộ. */
    @FunctionalInterface
    private interface HttpHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }
}
