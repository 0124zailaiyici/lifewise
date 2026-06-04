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

    // 匹配阈值：至少命中 3 个关键词
    private static final int MIN_KEYWORD_MATCH = 3;

    // 中文停用词
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们",
        "怎么", "如何", "什么", "哪个", "哪些", "哪里", "为什么", "多少",
        "可以", "能", "会", "应该", "需要", "想要", "打算",
        "吗", "呢", "啊", "吧", "呀", "嘛", "哦", "嗯",
        "做", "弄", "搞", "处理", "解决", "回事",
        "请问", "请教", "求教", "求助",
        "一下", "一点", "一些", "之后", "时候", "方法",
        "步骤", "技巧", "注意", "事项", "流程"
    ));

    @Override
    public String findAnswer(String question, String scene) {
        List<KnowledgeBase> list;
        if (scene != null && !scene.isEmpty()) {
            list = knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
        } else {
            list = knowledgeBaseRepository.findAll();
        }

        // 标准化问题
        String normalized = normalizeQuestion(question);
        // 提取关键词
        Set<String> keywords = extractKeywords(normalized);

        if (keywords.isEmpty()) return null;

        log.debug("知识库查询: question={}, keywords={}", question, keywords);

        KnowledgeBase best = null;
        int bestScore = 0;
        int bestExactBonus = 0;

        for (KnowledgeBase kb : list) {
            if (kb.getQuestion() == null) continue;

            String kbQuestion = normalizeQuestion(kb.getQuestion());
            int score = 0;
            int matchedCount = 0;

            for (String kw : keywords) {
                if (kbQuestion.contains(kw)) {
                    // 长关键词权重更高
                    int weight = Math.max(1, kw.length() - 1);
                    score += weight;
                    matchedCount++;
                }
            }

            // 精确匹配奖励
            int exactBonus = 0;
            if (kbQuestion.equals(normalized)) {
                exactBonus = 100;
            } else if (kbQuestion.contains(normalized) || normalized.contains(kbQuestion)) {
                exactBonus = 50;
            }

            score += exactBonus;

            // 比较：先比分数，再比精确匹配加成，再比有用次数
            if (matchedCount >= MIN_KEYWORD_MATCH || exactBonus > 0) {
                if (score > bestScore ||
                    (score == bestScore && exactBonus > bestExactBonus) ||
                    (score == bestScore && exactBonus == bestExactBonus &&
                     best != null && kb.getHelpfulCount() > best.getHelpfulCount())) {
                    bestScore = score;
                    bestExactBonus = exactBonus;
                    best = kb;
                }
            }
        }

        if (best != null) {
            log.info("知识库命中: question={}, score={}, helpful={}",
                best.getQuestion(), bestScore, best.getHelpfulCount());
            return best.getAnswer();
        }

        return null;
    }

    @Override
    public void saveAnswer(String question, String answer, String scene) {
        try {
            // 检查是否已存在相似问题，避免重复存储
            String normalized = normalizeQuestion(question);
            List<KnowledgeBase> existing = knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
            for (KnowledgeBase kb : existing) {
                if (kb.getQuestion() != null && normalizeQuestion(kb.getQuestion()).equals(normalized)) {
                    log.info("知识库已存在相似问题: {}", kb.getQuestion());
                    return;
                }
            }

            KnowledgeBase kb = new KnowledgeBase();
            kb.setQuestion(question.length() > 200 ? question.substring(0, 200) : question);
            kb.setAnswer(answer);
            kb.setScene(scene);
            kb.setHelpfulCount(0);
            kb.setTags(extractTags(question, scene, answer));
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

        // 先按包含关键词查找
        List<KnowledgeBase> results = knowledgeBaseRepository.findByQuestionContaining(keyword.trim());

        // 按得分排序
        String normalized = normalizeQuestion(keyword);
        results.sort((a, b) -> {
            int scoreA = scoreQuestion(normalizeQuestion(a.getQuestion() != null ? a.getQuestion() : ""), normalized);
            int scoreB = scoreQuestion(normalizeQuestion(b.getQuestion() != null ? b.getQuestion() : ""), normalized);
            int cmp = Integer.compare(scoreB, scoreA);
            if (cmp == 0) cmp = Integer.compare(
                b.getHelpfulCount() != null ? b.getHelpfulCount() : 0,
                a.getHelpfulCount() != null ? a.getHelpfulCount() : 0);
            return cmp;
        });

        return results;
    }

    @Override
    public void markHelpful(Long id) {
        knowledgeBaseRepository.findById(id).ifPresent(kb -> {
            kb.setHelpfulCount(kb.getHelpfulCount() + 1);
            knowledgeBaseRepository.save(kb);
        });
    }

    /**
     * 标准化问题：去空格、去标点、统一大小写、移除停用词片段
     */
    private String normalizeQuestion(String question) {
        if (question == null) return "";
        // 去空格
        String s = question.trim();
        // 去标点符号（中英文）
        s = s.replaceAll("[\\p{P}\\p{S}，。！？、；：·……—\u3000]", "");
        return s;
    }

    /**
     * 提取关键词：2~6 字中文片段，过滤停用词
     */
    private Set<String> extractKeywords(String text) {
        if (text == null || text.length() < 2) {
            if (text != null && text.length() == 1) {
                Set<String> single = new HashSet<>();
                single.add(text);
                return single;
            }
            return new HashSet<>();
        }

        Set<String> keywords = new LinkedHashSet<>();
        int maxLen = Math.min(6, text.length());

        // 提取所有 2~6 字片段
        for (int len = 2; len <= maxLen; len++) {
            for (int i = 0; i <= text.length() - len; i++) {
                String seg = text.substring(i, i + len);
                // 必须包含中文或英文字母
                if (!seg.matches(".*[\\u4e00-\\u9fa5a-zA-Z]+.*")) continue;
                // 过滤停用词
                if (isStopWord(seg)) continue;
                keywords.add(seg);
            }
        }

        // 如果整个问题是关键词，添加进去
        if (text.length() >= 2 && text.length() <= 10 && !isStopWord(text)) {
            keywords.add(text);
        }

        return keywords;
    }

    private boolean isStopWord(String word) {
        if (STOP_WORDS.contains(word)) return true;
        // 过滤纯数字
        if (word.matches("\\d+")) return true;
        // 过滤单字（除非是有效中文词的一部分）
        if (word.length() == 1) return true;
        return false;
    }

    /**
     * 评分函数：一个问题和关键词的匹配程度
     */
    private int scoreQuestion(String kbQuestion, String normalized) {
        if (kbQuestion.isEmpty()) return 0;
        if (kbQuestion.equals(normalized)) return 100;
        if (kbQuestion.contains(normalized) || normalized.contains(kbQuestion)) return 50;

        // 关键词匹配：提取标准化的关键词
        Set<String> keywords = extractKeywords(normalized);
        int score = 0;
        for (String kw : keywords) {
            if (kbQuestion.contains(kw)) {
                score += Math.max(1, kw.length() - 1);
            }
        }
        return score;
    }

    /**
     * 提取标签：从问题和回答中提取有意义的关键词
     */
    private String extractTags(String question, String scene, String answer) {
        StringBuilder tags = new StringBuilder();
        if (scene != null) tags.append(scene).append(",");

        String normalized = normalizeQuestion(question != null ? question : "");
        Set<String> keywords = extractKeywords(normalized);

        // 取最长的一些关键词作为标签（最多 5 个）
        List<String> sorted = keywords.stream()
            .sorted((a, b) -> Integer.compare(b.length(), a.length()))
            .limit(5)
            .collect(Collectors.toList());

        if (!sorted.isEmpty()) {
            tags.append(String.join(",", sorted));
        }

        return tags.length() > 200 ? tags.substring(0, 200) : tags.toString();
    }
}