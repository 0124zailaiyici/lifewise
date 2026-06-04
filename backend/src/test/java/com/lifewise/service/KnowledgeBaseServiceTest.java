package com.lifewise.service;

import com.lifewise.entity.KnowledgeBase;
import com.lifewise.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("知识库服务测试")
class KnowledgeBaseServiceTest {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeBaseRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Nested
    @DisplayName("保存回答")
    class SaveAnswer {

        @Test
        @DisplayName("应该保存新问答到知识库")
        void shouldSaveNewAnswer() {
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "做法...", "cooking");
            List<KnowledgeBase> all = repository.findAll();
            assertEquals(1, all.size());
            assertEquals("西红柿炒鸡蛋怎么做", all.get(0).getQuestion());
            assertEquals("cooking", all.get(0).getScene());
        }

        @Test
        @DisplayName("相同问题不应该重复保存")
        void shouldNotDuplicateExactQuestion() {
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "看纹路...", "shopping");
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "看纹路...", "shopping");
            assertEquals(1, repository.findAll().size());
        }

        @Test
        @DisplayName("相似问题（核心相同）不应该重复保存")
        void shouldNotDuplicateSimilarQuestion() {
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "做法...", "cooking");
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋如何做", "做法...", "cooking");
            assertEquals(1, repository.findAll().size());
        }

        @Test
        @DisplayName("不同场景的相似问题可以分别保存")
        void shouldAllowSameCoreInDifferentScenes() {
            knowledgeBaseService.saveAnswer("鱼怎么做", "清蒸...", "cooking");
            knowledgeBaseService.saveAnswer("鱼怎么做", "水族箱...", "pet");
            assertEquals(2, repository.findAll().size());
        }

        @Test
        @DisplayName("空内容不应该保存")
        void shouldNotSaveEmptyContent() {
            knowledgeBaseService.saveAnswer("", "答案", "cooking");
            knowledgeBaseService.saveAnswer("问题", "", "cooking");
            assertEquals(0, repository.findAll().size());
        }
    }

    @Nested
    @DisplayName("查找答案（相似度匹配）")
    class FindAnswer {

        @BeforeEach
        void initData() {
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "{title:西红柿炒鸡蛋}", "cooking");
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "{品类:西瓜}", "shopping");
            knowledgeBaseService.saveAnswer("水龙头滴水怎么办", "{problem:水龙头滴水}", "repair");
        }

        @Test
        @DisplayName("精确匹配应该命中缓存")
        void shouldHitExactMatch() {
            String r = knowledgeBaseService.findAnswer("西红柿炒鸡蛋怎么做", "cooking");
            assertNotNull(r);
        }

        @Test
        @DisplayName("相同核心不同表述应该命中缓存")
        void shouldHitCoreMatch() {
            String r = knowledgeBaseService.findAnswer("西红柿炒鸡蛋如何做", "cooking");
            assertNotNull(r);
        }

        @Test
        @DisplayName("相似问题应该命中缓存")
        void shouldHitSimilarMatch() {
            String r = knowledgeBaseService.findAnswer("怎么挑选西瓜", "shopping");
            assertNotNull(r);
        }

        @Test
        @DisplayName("不相关的问题应该返回 null")
        void shouldReturnNullForUnrelated() {
            String r = knowledgeBaseService.findAnswer("空调不制冷怎么办", "repair");
            assertNull(r);
        }

        @Test
        @DisplayName("跨场景不匹配")
        void shouldNotMatchAcrossScenes() {
            String r = knowledgeBaseService.findAnswer("西红柿炒鸡蛋怎么做", "shopping");
            assertNull(r);
        }
    }

    @Nested
    @DisplayName("搜索知识库")
    class Search {

        @BeforeEach
        void initData() {
            knowledgeBaseService.saveAnswer("红烧排骨怎么做", "做法...", "cooking");
            knowledgeBaseService.saveAnswer("番茄牛腩怎么做", "做法...", "cooking");
            knowledgeBaseService.saveAnswer("怎么挑苹果", "看颜色...", "shopping");
        }

        @Test
        void shouldSearchByKeyword() {
            List<KnowledgeBase> r = knowledgeBaseService.search("排骨", null);
            assertEquals(1, r.size());
        }

        @Test
        void shouldFilterByScene() {
            List<KnowledgeBase> r = knowledgeBaseService.search(null, "cooking");
            assertEquals(2, r.size());
        }

        @Test
        void shouldReturnEmptyForNoMatch() {
            List<KnowledgeBase> r = knowledgeBaseService.search("xyzNotFound", null);
            assertTrue(r.isEmpty());
        }
    }

    @Nested
    @DisplayName("标记有用")
    class MarkHelpful {

        @Test
        void shouldIncrementHelpfulCount() {
            knowledgeBaseService.saveAnswer("测试问题", "测试答案", "other");
            Long id = repository.findAll().get(0).getId();
            knowledgeBaseService.markHelpful(id);
            assertEquals(1, repository.findById(id).get().getHelpfulCount());
        }

        @Test
        void shouldNotThrowForNonExistentId() {
            assertDoesNotThrow(() -> knowledgeBaseService.markHelpful(99999L));
        }
    }
}
