package com.ticket.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 极简文本向量化：中文按“字符 bigram”切词，英文/数字按词切分，
 * 生成词项(TF)向量。零外部依赖、本地可跑，对短文本工单相似度足够。
 *
 * 之所以用字符 bigram：中文无空格分词，bigram 在零依赖下捕捉局部语义共现。
 * 本类只负责“分词 + TF”，IDF 加权由 {@link TfIdfVectorizer} 叠加，从而把
 * 纯词频升级为 TF-IDF，抑制“的/了”等高频无区分度词、突出类别特征词。
 */
public final class TextVectorizer {

    private TextVectorizer() {
    }

    /** 原始词项序列（含重复，用于统计词频 TF） */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String t = text.toLowerCase().replaceAll("\\s+", " ").trim();

        for (String token : t.split(" ")) {
            if (token.matches("[a-z0-9]+") && token.length() > 1) {
                out.add(token);
            }
        }
        StringBuilder cjk = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                cjk.append(c);
            }
        }
        for (int i = 0; i < cjk.length() - 1; i++) {
            out.add(cjk.substring(i, i + 2));
        }
        for (int i = 0; i < cjk.length(); i++) {
            out.add("c:" + cjk.charAt(i));
        }
        return out;
    }

    /** 去重词项集合（用于计算文档频率 DF） */
    public static Set<String> tokenSet(String text) {
        return new LinkedHashSet<>(tokens(text));
    }

    /** 词频(TF)向量 */
    public static Map<String, Double> vectorize(String text) {
        Map<String, Double> vec = new HashMap<>();
        for (String tok : tokens(text)) {
            vec.merge(tok, 1.0, Double::sum);
        }
        return vec;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double bv = b.get(e.getKey());
            if (bv != null) dot += e.getValue() * bv;
            na += e.getValue() * e.getValue();
        }
        for (double v : b.values()) nb += v * v;
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
