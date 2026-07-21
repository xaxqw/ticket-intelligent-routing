package com.ticket.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程稠密向量化器：调用 OpenAI 兼容的 {@code /v1/embeddings} 接口
 * （兼容 OpenAI / Azure / 阿里云百炼 DashScope 等，自建服务暴露同形态端点亦可），
 * 将文本映射为固定维度的稠密语义向量。
 *
 * <p>设计要点（与分类器解耦的关键）：
 * <ul>
 *   <li>输出 {@code Map<String,Double>}，key 为维度下标 {@code "0".."dim-1"}，value 为分量，
 *       并做 L2 归一化——所以能和 {@link LocalVectorStore} 的余弦检索无缝配合；</li>
 *   <li>这正是“可插拔向量化”的体现：无需改动 {@link TicketClassifier}，
 *       仅替换向量器即可从本地 TF-IDF 升级为真实语义 embedding（BGE / OpenAI / 本地模型均可）；</li>
 *   <li>按文本 LRU 缓存，避免重复调用；首次调用需联网，故默认关闭，生产在配置中开启。</li>
 * </ul>
 */
public class RemoteEmbeddingVectorizer implements java.util.function.Function<String, Map<String, Double>> {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingVectorizer.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final int cacheSize;
    private final Map<String, Map<String, Double>> cache = new ConcurrentHashMap<>();

    public RemoteEmbeddingVectorizer(String baseUrl, String apiKey, String model, int dimensions, int cacheSize) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.cacheSize = Math.max(1, cacheSize);
    }

    @Override
    public Map<String, Double> apply(String text) {
        Map<String, Double> cached = cache.get(text);
        if (cached != null) return cached;
        Map<String, Double> vec = fetch(text);
        if (cache.size() < cacheSize) cache.put(text, vec);
        return vec;
    }

    private Map<String, Double> fetch(String text) {
        try {
            URL url = new URL(baseUrl + "/embeddings");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            String payload = mapper.writeValueAsString(
                    new EmbedRequest(model, text, dimensions > 0 ? dimensions : null));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            JsonNode root = mapper.readTree(conn.getInputStream());
            if (code != 200) {
                throw new IllegalStateException("embedding API http=" + code + ": " + root.toString());
            }
            JsonNode data = root.path("data").path(0).path("embedding");
            if (!data.isArray() || data.size() == 0) {
                throw new IllegalStateException("unexpected embedding response: " + root);
            }
            Map<String, Double> vec = new LinkedHashMap<>();
            double norm = 0.0;
            for (int i = 0; i < data.size(); i++) {
                double v = data.get(i).asDouble();
                vec.put(String.valueOf(i), v);
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (Map.Entry<String, Double> e : vec.entrySet()) {
                    vec.put(e.getKey(), e.getValue() / norm);
                }
            }
            return vec;
        } catch (Exception e) {
            log.error("远程向量化失败（请检查 ai.embedding 配置或网络连通性）: {}", e.getMessage());
            throw new RuntimeException("embedding vectorize failed: " + e.getMessage(), e);
        }
    }

    /** Jackson 序列化用的请求体（input 为文本，dimensions 可选） */
    private static class EmbedRequest {
        public String model;
        public String input;
        public Integer dimensions;

        EmbedRequest(String model, String input, Integer dimensions) {
            this.model = model;
            this.input = input;
            this.dimensions = dimensions;
        }
    }

    /** 用于接口/日志展示当前向量化器身份 */
    public String describe() {
        return "remote-embedding[" + model + "@" + baseUrl + ",dim=" + dimensions + "]";
    }
}
