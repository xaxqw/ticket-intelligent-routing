package com.ticket.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 分派相关配置。默认走本地内存向量库；生产可切换 Chroma。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** local（默认） | chroma */
    private String vectorStoreType = "local";

    /** few-shot 召回数量 */
    private int topK = 5;

    /** 置信度阈值：低于此值的分派判定为“不确定”，转人工复核（人在回路） */
    private double reviewThreshold = 0.55;

    /** Chroma 服务地址（type=chroma 时生效） */
    private String chromaUrl = "http://localhost:8000";

    /** Chroma 集合名 */
    private String chromaCollection = "ticket_history";

    /** 种子历史工单资源路径 */
    private String seedResource = "classpath:ai/historical-tickets.json";

    /**
     * 远程稠密 embedding 配置（可插拔向量化的“真实语义”选项）。
     * 默认关闭；开启后向量化器切换为 {@link RemoteEmbeddingVectorizer}，
     * 调用 OpenAI 兼容的 /v1/embeddings 接口，无需改分类器即可升级为语义 embedding。
     */
    @Data
    public static class Embedding {
        private boolean enabled = false;
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "text-embedding-3-small";
        private int dimensions = 1536;
        private int cacheSize = 2000;
    }

    private Embedding embedding = new Embedding();
}
