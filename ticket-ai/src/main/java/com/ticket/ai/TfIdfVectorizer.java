package com.ticket.ai;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TF-IDF 向量化器：在 {@link TextVectorizer} 的词频(TF)基础上叠加
 * 逆文档频率(IDF)，由种子语料离线计算。
 *
 * 设计要点：
 *  - IDF = ln((N+1)/(df+1)) + 1（平滑），避免零频词出现负无穷；
 *  - 向量做 L2 归一化，保证余弦相似度只反映方向（语义分布）而非长度；
 *  - 语料即“历史工单”，随业务增长重算即可，无需训练模型——
 *    这正是企业场景务实且可解释的选型。
 */
public final class TfIdfVectorizer {

    private final Map<String, Double> idf = new HashMap<>();

    public TfIdfVectorizer(Collection<String> corpus) {
        int n = Math.max(1, corpus.size());
        Map<String, Integer> df = new HashMap<>();
        for (String doc : corpus) {
            for (String term : TextVectorizer.tokenSet(doc)) {
                df.merge(term, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            idf.put(e.getKey(), Math.log((n + 1.0) / (e.getValue() + 1.0)) + 1.0);
        }
    }

    /** 文本 -> TF-IDF 向量（L2 归一化） */
    public Map<String, Double> vectorize(String text) {
        Map<String, Double> tf = TextVectorizer.vectorize(text);
        Map<String, Double> out = new HashMap<>();
        double norm = 0.0;
        for (Map.Entry<String, Double> e : tf.entrySet()) {
            double w = e.getValue() * idf.getOrDefault(e.getKey(), 1.0);
            out.put(e.getKey(), w);
            norm += w * w;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (Map.Entry<String, Double> e : out.entrySet()) {
                out.put(e.getKey(), e.getValue() / norm);
            }
        }
        return out;
    }

    public int vocabularySize() {
        return idf.size();
    }
}
