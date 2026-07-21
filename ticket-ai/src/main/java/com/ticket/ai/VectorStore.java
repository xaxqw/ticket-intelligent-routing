package com.ticket.ai;

import com.ticket.common.domain.Category;

import java.util.List;

/**
 * 向量存储抽象。两种实现：
 *  - {@link LocalVectorStore}：内存向量（中文 char-bigram + 余弦），零外部依赖，开箱即跑；
 *  - {@link ChromaVectorStore}：对接 Chroma 服务，生产可选。
 * 业务侧只依赖此接口，切换实现不改动分类逻辑。
 */
public interface VectorStore {

    /** 写入/更新一条历史工单向量 */
    void upsert(String id, String text, Category category);

    /** 语义检索，返回 Top-K 相似项及其所属分类与相似度 */
    List<ScoredItem> search(String text, int topK);

    class ScoredItem {
        public final String id;
        public final String text;
        public final Category category;
        public final double score;

        public ScoredItem(String id, String text, Category category, double score) {
            this.id = id;
            this.text = text;
            this.category = category;
            this.score = score;
        }
    }
}
