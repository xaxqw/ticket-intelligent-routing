package com.ticket.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 分派自动装配：
 *  - 依据 ai.vector-store.type 选择 Local / Chroma 实现；
 *  - 读取种子历史工单并灌入向量库；
 *  - 产出可直接注入业务层的 {@link TicketClassifier}（已 seeded）。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiAutoConfiguration.class);

    @Autowired
    private AiProperties props;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    @ConditionalOnMissingBean
    public VectorStore vectorStore() throws Exception {
        AiProperties.Embedding emb = props.getEmbedding();
        // 可插拔向量化：开启远程 embedding 且配置了密钥时，切换为真实语义向量；
        // 否则默认本地 TF-IDF（离线、零依赖、启动即用）。
        if (emb != null && emb.isEnabled()
                && emb.getApiKey() != null && !emb.getApiKey().isEmpty()) {
            RemoteEmbeddingVectorizer remote = new RemoteEmbeddingVectorizer(
                    emb.getBaseUrl(), emb.getApiKey(), emb.getModel(),
                    emb.getDimensions(), emb.getCacheSize());
            log.info("AI 向量化器: 启用远程稠密 embedding -> {}", remote.describe());
            return new LocalVectorStore(remote);
        }
        // 默认实现：用种子语料离线计算 IDF，注入 TF-IDF 向量器
        List<HistoricalTicket> seed = loadSeed();
        TfIdfVectorizer tfidf = new TfIdfVectorizer(
                seed.stream().map(HistoricalTicket::getText).collect(Collectors.toList()));
        log.info("AI 向量化器: 启用本地 TF-IDF（词表 {} 词，零外部依赖）", tfidf.vocabularySize());
        return new LocalVectorStore(tfidf::vectorize);
    }

    /** 暴露当前生效的向量化器描述，供接口/前端/日志展示（证明“可插拔”可见可控） */
    @Bean
    @ConditionalOnMissingBean(name = "aiVectorizerDesc")
    public String aiVectorizerDesc() {
        AiProperties.Embedding emb = props.getEmbedding();
        if (emb != null && emb.isEnabled()
                && emb.getApiKey() != null && !emb.getApiKey().isEmpty()) {
            return "remote-embedding[" + emb.getModel() + "@" + emb.getBaseUrl() + "]";
        }
        return "local-tfidf";
    }

    @Bean
    @ConditionalOnMissingBean
    public TicketClassifier ticketClassifier(VectorStore vectorStore) throws Exception {
        TicketClassifier classifier = new TicketClassifier(vectorStore, props.getTopK());
        classifier.setReviewThreshold(props.getReviewThreshold());
        classifier.seed(loadSeed());
        return classifier;
    }

    private List<HistoricalTicket> loadSeed() throws Exception {
        Resource resource = resourceLoader.getResource(props.getSeedResource());
        try (InputStream in = resource.getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<List<HistoricalTicket>>() {
            });
        }
    }
}
