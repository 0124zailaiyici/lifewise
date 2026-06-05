package com.lifewise.service.impl;

import com.lifewise.entity.KnowledgeBase;
import com.lifewise.repository.KnowledgeBaseRepository;
import com.lifewise.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /** Jaccard 相似度阈值：≥ 0.55 认为相似 */
    private static final double SIMILARITY_THRESHOLD = 0.55;

    /** 最小绝对重叠字符数 */
    private static final int MIN_OVERLAP_CHARS = 3;

    /** 常见疑问前缀——提问时的开头虚词 */
    private static final String[] QUESTION_PREFIXES = {
        "怎么", "如何", "怎样", "怎么样", "请问", "请教", "求教", "求助"
    };

    /** 常见疑问后缀——提问时的结尾虚词 */
    private static final String[] QUESTION_SUFFIXES = {
        "怎么做", "如何做", "怎么弄", "怎么办", "怎么处理", "怎么解决",
        "怎么选", "怎么挑", "怎么判断", "怎么看", "怎么吃", "怎么用"
    };

    @Override
    public String findAnswer(String question, String scene) {
        List<KnowledgeBase> list;
        if (scene != null && !scene.isEmpty()) {
            list = knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
        } else {
            list = knowledgeBaseRepository.findAll();
        }

        String normalized = normalizeQuestion(question);
        if (normalized.isEmpty()) return null;

        // 提取核心内容（去掉"怎么做"等前缀/后缀）
        String core = extractCore(normalized);
        if (core.isEmpty()) return null;

        KnowledgeBase best = null;
        double bestScore = 0;

        for (KnowledgeBase kb : list) {
            if (kb.getQuestion() == null) continue;

            String kbNormalized = normalizeQuestion(kb.getQuestion());
            if (kbNormalized.isEmpty()) continue;

            String kbCore = extractCore(kbNormalized);
            if (kbCore.isEmpty()) continue;

            // 1) 精确匹配（归一化后完全相同）
            if (kbNormalized.equals(normalized)) {
                log.info("知识库精确命中: question={}", kb.getQuestion());
                return kb.getAnswer();
            }

            // 2) 核心内容完全相同（如 "西红柿炒鸡蛋怎么做" vs "如何做西红柿炒鸡蛋"）
            if (kbCore.equals(core)) {
                log.info("知识库核心命中: question={} ≈ core={}", kb.getQuestion(), core);
                return kb.getAnswer();
            }

            // 3) 核心内容 Jaccard 相似度
            double sim = jaccardSimilarity(kbCore, core);
            if (sim >= SIMILARITY_THRESHOLD) {
                // 同时要求绝对重叠字符数
                int overlap = charOverlap(kbCore, core);
                if (overlap >= MIN_OVERLAP_CHARS) {
                    if (sim > bestScore) {
                        bestScore = sim;
                        best = kb;
                    }
                }
            }
        }

        if (best != null) {
            log.info("知识库相似命中: question={}, sim={}", best.getQuestion(), String.format("%.2f", bestScore));
            return best.getAnswer();
        }

        return null;
    }

    @Override
    public void saveAnswer(String question, String answer, String scene) {
        try {
            if (question == null || question.trim().isEmpty()) return;
            if (answer == null || answer.trim().isEmpty()) return;

            // 去重检查：归一化后相同 OR 核心内容相同 都算重复
            String normalized = normalizeQuestion(question);
            String core = extractCore(normalized);

            List<KnowledgeBase> existing = knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
            for (KnowledgeBase kb : existing) {
                if (kb.getQuestion() == null) continue;
                String kbNorm = normalizeQuestion(kb.getQuestion());
                String kbCore = extractCore(kbNorm);

                if (kbNorm.equals(normalized) || (!core.isEmpty() && kbCore.equals(core))) {
                    log.info("知识库已存在相似问题: {}", kb.getQuestion());
                    return;
                }
            }

            KnowledgeBase kb = new KnowledgeBase();
            kb.setQuestion(question.length() > 200 ? question.substring(0, 200) : question);
            kb.setAnswer(answer);
            kb.setScene(scene);
            kb.setHelpfulCount(0);
            knowledgeBaseRepository.save(kb);
            log.info("知识库新增: question={}, scene={}", kb.getQuestion(), scene);
        } catch (Exception e) {
            log.warn("知识库保存失败: {}", e.getMessage());
        }
    }

    @Override
    public List<KnowledgeBase> search(String keyword, String scene) {
        if (keyword == null || keyword.trim().isEmpty()) {
            if (scene != null && !scene.isEmpty()) {
                return knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
            }
            return knowledgeBaseRepository.findAll();
        }

        List<KnowledgeBase> results = knowledgeBaseRepository.findByQuestionContaining(keyword.trim());

        results.sort((a, b) -> {
            int cmp = Integer.compare(
                b.getHelpfulCount() != null ? b.getHelpfulCount() : 0,
                a.getHelpfulCount() != null ? a.getHelpfulCount() : 0);
            return cmp;
        });

        return results;
    }

    @Override
    public void delete(Long id) {
        knowledgeBaseRepository.deleteById(id);
        log.info("????? id={}", id);
    }

    @Override
    public void markHelpful(Long id) {
        knowledgeBaseRepository.findById(id).ifPresent(kb -> {
            kb.setHelpfulCount(kb.getHelpfulCount() + 1);
            knowledgeBaseRepository.save(kb);
        });
    }

    // ======================== 相似度算法 ========================

    /**
     * 标准化问题：去空格、去标点、去换行
     */
    private String normalizeQuestion(String question) {
        if (question == null) return "";
        String s = question.trim();
        s = s.replaceAll("[\\p{P}\\p{S}，。！？、；：＂＇…—·\u3000]", "");
        s = s.replaceAll("[\\n\\r\\t]", "").trim();
        return s;
    }

    /**
     * 提取核心内容：去掉"怎么做"、"如何"等疑问前缀/后缀
     * 例: "西红柿炒鸡蛋怎么做" → "西红柿炒鸡蛋"
     * 例: "如何做西红柿炒鸡蛋" → "西红柿炒鸡蛋"
     */
    private String extractCore(String normalized) {
        String s = normalized;

        // 去掉前缀
        for (String prefix : QUESTION_PREFIXES) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
                break;
            }
        }

        // 去掉后缀
        for (String suffix : QUESTION_SUFFIXES) {
            if (s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
                break;
            }
        }

        return s.trim();
    }

    /**
     * Jaccard 相似度：两个字符串字符集的交集大小 / 并集大小
     */
    private double jaccardSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.isEmpty() && b.isEmpty()) return 1;

        Set<Character> setA = new HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);

        Set<Character> setB = new HashSet<>();
        for (char c : b.toCharArray()) setB.add(c);

        // 交集
        Set<Character> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        // 并集
        Set<Character> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) return 0;
        return (double) intersection.size() / union.size();
    }

    /**
     * 两个字符串的公共字符数
     */
    private int charOverlap(String a, String b) {
        Set<Character> setA = new HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);

        Set<Character> intersection = new HashSet<>(setA);
        Set<Character> setB = new HashSet<>();
        for (char c : b.toCharArray()) setB.add(c);
        intersection.retainAll(setB);

        return intersection.size();
    }
}
