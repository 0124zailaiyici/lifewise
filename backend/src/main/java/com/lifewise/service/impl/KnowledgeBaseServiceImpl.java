package com.lifewise.service.impl;

import com.lifewise.entity.KnowledgeBase;
import com.lifewise.repository.KnowledgeBaseRepository;
import com.lifewise.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    // 关键词匹配阈值：命中 2 个以上关键词即为匹配
    private static final int MIN_KEYWORD_MATCH = 2;

    @Override
    public String findAnswer(String question, String scene) {
        List<KnowledgeBase> list;
        if (scene != null && !scene.isEmpty()) {
            list = knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
        } else {
            list = knowledgeBaseRepository.findAll();
        }

        // 提取问题中的关键词（简单切词：2~4字片段）
        String[] keywords = extractKeywords(question);

        KnowledgeBase best = null;
        int bestScore = 0;

        for (KnowledgeBase kb : list) {
            int score = 0;
            for (String kw : keywords) {
                if (kb.getQuestion() != null && kb.getQuestion().contains(kw)) {
                    score++;
                }
            }
            // 权重：有用次数多的优先
            if (score > bestScore) {
                bestScore = score;
                best = kb;
            } else if (score == bestScore && best != null && kb.getHelpfulCount() > best.getHelpfulCount()) {
                best = kb;
            }
        }

        if (best != null && bestScore >= MIN_KEYWORD_MATCH) {
            log.info("知识库命中: question={}, score={}, helpful={}",
                best.getQuestion(), bestScore, best.getHelpfulCount());
            return best.getAnswer();
        }

        return null;
    }

    @Override
    public void saveAnswer(String question, String answer, String scene) {
        try {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setQuestion(question.length() > 200 ? question.substring(0, 200) : question);
            kb.setAnswer(answer);
            kb.setScene(scene);
            kb.setHelpfulCount(0);
            // 从回答中提取标签
            String tags = extractTags(question, scene);
            kb.setTags(tags);
            knowledgeBaseRepository.save(kb);
            log.info("知识库新增: question={}, scene={}", kb.getQuestion(), scene);
        } catch (Exception e) {
            log.warn("知识库保存失败: {}", e.getMessage());
        }
    }

    @Override
    public List<KnowledgeBase> search(String keyword, String scene) {
        if (scene != null && !scene.isEmpty()) {
            return knowledgeBaseRepository.findBySceneOrderByHelpfulCountDesc(scene);
        }
        return knowledgeBaseRepository.findByQuestionContaining(keyword);
    }

    @Override
    public void markHelpful(Long id) {
        knowledgeBaseRepository.findById(id).ifPresent(kb -> {
            kb.setHelpfulCount(kb.getHelpfulCount() + 1);
            knowledgeBaseRepository.save(kb);
        });
    }

    private String[] extractKeywords(String question) {
        // 简单切词：取 2~6 字的连续片段作为关键词
        // 实际项目建议用分词库（jieba/hanlp），这里用简单方法
        if (question == null || question.length() < 2) return new String[]{question};

        java.util.Set<String> keywords = new java.util.LinkedHashSet<>();
        int maxLen = Math.min(6, question.length());
        for (int len = 2; len <= maxLen; len++) {
            for (int i = 0; i <= question.length() - len; i++) {
                String seg = question.substring(i, i + len);
                // 过滤纯标点/空格
                if (seg.matches(".*[\\u4e00-\\u9fa5a-zA-Z]+.*")) {
                    keywords.add(seg);
                }
            }
        }
        return keywords.toArray(new String[0]);
    }

    private String extractTags(String question, String scene) {
        StringBuilder tags = new StringBuilder();
        if (scene != null) tags.append(scene).append(",");
        // 取问题前 5 个字作为标签
        if (question != null && question.length() > 2) {
            tags.append(question.substring(0, Math.min(5, question.length())));
        }
        return tags.toString();
    }
}
