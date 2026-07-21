package com.ticket.ai;

import com.ticket.common.domain.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 内存向量库：把历史工单向量化后缓存在 JVM，检索时做余弦相似度排序。
 * 零外部依赖、启动即用，是 MVP 的默认实现。历史工单规模不大（几千~几万），
 * 线性扫描完全够用；真要上量再换 Chroma / Milvus 即可（接口一致）。
 *
 * 向量化器可插拔：默认用 {@link TextVectorizer}（纯 TF），生产推荐注入
 * {@link TfIdfVectorizer}（TF-IDF，更突出类别特征词），upsert 与 search
 * 必须用同一向量器，保证余弦空间一致。
 */
public class LocalVectorStore implements VectorStore {

    private static class Entry {
        final String id;
        final String text;
        final Category category;
        final Map<String, Double> vec;

        Entry(String id, String text, Category category, Map<String, Double> vec) {
            this.id = id;
            this.text = text;
            this.category = category;
            this.vec = vec;
        }
    }

    private final Map<String, Entry> index = new ConcurrentHashMap<>();
    private final Function<String, Map<String, Double>> vectorizer;

    /** 默认：纯词频(TF)向量 */
    public LocalVectorStore() {
        this(TextVectorizer::vectorize);
    }

    /** 注入自定义向量器（如 TF-IDF），upsert/search 保持一致 */
    public LocalVectorStore(Function<String, Map<String, Double>> vectorizer) {
        this.vectorizer = vectorizer;
    }

    @Override
    public void upsert(String id, String text, Category category) {
        index.put(id, new Entry(id, text, category, vectorizer.apply(text)));
    }

    @Override
    public List<ScoredItem> search(String text, int topK) {
        Map<String, Double> q = vectorizer.apply(text);
        List<ScoredItem> scored = new ArrayList<>();
        for (Entry e : index.values()) {
            double score = TextVectorizer.cosine(q, e.vec);
            scored.add(new ScoredItem(e.id, e.text, e.category, score));
        }
        scored.sort((x, y) -> Double.compare(y.score, x.score));
        return scored.subList(0, Math.min(topK, scored.size()));
    }
}
