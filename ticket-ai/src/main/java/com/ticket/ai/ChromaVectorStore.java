package com.ticket.ai;

import com.ticket.common.domain.Category;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 可选实现：对接 Chroma 向量数据库（生产扩展位）。
 * 通过 Chroma REST API 完成 add / query，embedding 由可插拔的
 * {@link EmbeddingFunction} 提供（默认用 hashing 稠密向量，零外部模型即可自洽运行）。
 *
 * 启用方式：application.yml 中 ai.vector-store.type=chroma 且配置 ai.chroma.url。
 * 不上 Chroma 时不会实例化，不影响默认内存实现。
 */
public class ChromaVectorStore implements VectorStore {

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;
    private final String collection;
    private final EmbeddingFunction embed;

    public ChromaVectorStore(String baseUrl, String collection, EmbeddingFunction embed) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.collection = collection;
        this.embed = embed != null ? embed : HashingEmbedding::embed;
    }

    @Override
    public void upsert(String id, String text, Category category) {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", Collections.singletonList(id));
        body.put("embeddings", Collections.singletonList(embed.apply(text)));
        body.put("documents", Collections.singletonList(text));
        body.put("metadatas", Collections.singletonList(Collections.singletonMap("category", category.name())));
        post("/collections/" + collection + "/add", body);
    }

    @Override
    public List<ScoredItem> search(String text, int topK) {
        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", Collections.singletonList(embed.apply(text)));
        body.put("n_results", topK);
        Map<?, ?> resp = rest.postForObject(baseUrl + "/api/v1/collections/" + collection + "/query",
                jsonEntity(body), Map.class);
        if (resp == null) return Collections.emptyList();
        List<String> ids = (List<String>) ((List<?>) resp.get("ids")).get(0);
        List<String> docs = (List<String>) ((List<?>) resp.get("documents")).get(0);
        List<Map<String, Object>> metas = (List<Map<String, Object>>) ((List<?>) resp.get("metadatas")).get(0);
        List<Double> dists = (List<Double>) ((List<?>) resp.get("distances")).get(0);
        List<ScoredItem> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String catName = metas.get(i) == null ? null : (String) metas.get(i).get("category");
            Category cat = catName == null ? null : Category.valueOf(catName);
            double sim = 1.0 - (dists.get(i) == null ? 1.0 : dists.get(i));
            out.add(new ScoredItem(ids.get(i), docs.get(i), cat, sim));
        }
        return out;
    }

    private void post(String path, Object body) {
        rest.postForObject(baseUrl + "/api/v1" + path, jsonEntity(body), Object.class);
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    /** 文本 -> 稠密向量（固定维度） */
    public interface EmbeddingFunction {
        double[] apply(String text);
    }

    /** 默认 embedding：256 维 hashing trick，无需任何外部模型 */
    public static class HashingEmbedding implements EmbeddingFunction {
        private static final int DIM = 256;

        public static double[] embed(String text) {
            double[] v = new double[DIM];
            for (String tok : text.toLowerCase().split("\\s+|(?<=.)")) {
                int h = tok.hashCode();
                int idx = Math.floorMod(h, DIM);
                v[idx] += 1.0;
            }
            double norm = 0;
            for (double x : v) norm += x * x;
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < DIM; i++) v[i] /= norm;
            return v;
        }

        @Override
        public double[] apply(String text) {
            return embed(text);
        }
    }
}
