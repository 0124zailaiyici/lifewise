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
@DisplayName("常识库服务测试")
class KnowledgeBaseServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

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
        @DisplayName("应该保存新问答到常识库")
        void shouldSaveNewAnswer() {
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "做法...", "cooking", USER_ID);

            List<KnowledgeBase> all = repository.findAll();
            assertEquals(1, all.size());
            assertEquals(USER_ID, all.get(0).getUserId());
            assertEquals("西红柿炒鸡蛋怎么做", all.get(0).getQuestion());
            assertEquals("cooking", all.get(0).getScene());
        }

        @Test
        @DisplayName("同用户相同问题不应该重复保存")
        void shouldNotDuplicateExactQuestionForSameUser() {
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "看纹路...", "shopping", USER_ID);
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "看纹路...", "shopping", USER_ID);

            assertEquals(1, repository.findAll().size());
        }

        @Test
        @DisplayName("不同用户相同问题应该分别保存")
        void shouldAllowSameQuestionForDifferentUsers() {
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "用户1答案", "shopping", USER_ID);
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "用户2答案", "shopping", OTHER_USER_ID);

            assertEquals(2, repository.findAll().size());
            assertEquals(1, repository.findByUserId(USER_ID).size());
            assertEquals(1, repository.findByUserId(OTHER_USER_ID).size());
        }

        @Test
        @DisplayName("相似问题（核心相同）不应该重复保存")
        void shouldNotDuplicateSimilarQuestion() {
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "做法...", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋如何做", "做法...", "cooking", USER_ID);

            assertEquals(1, repository.findAll().size());
        }

        @Test
        @DisplayName("不同场景的相似问题可以分别保存")
        void shouldAllowSameCoreInDifferentScenes() {
            knowledgeBaseService.saveAnswer("鱼怎么做", "清蒸...", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("鱼怎么做", "水族箱...", "pet", USER_ID);

            assertEquals(2, repository.findAll().size());
        }

        @Test
        @DisplayName("空内容不应该保存")
        void shouldNotSaveEmptyContent() {
            knowledgeBaseService.saveAnswer("", "答案", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("问题", "", "cooking", USER_ID);

            assertEquals(0, repository.findAll().size());
        }

        @Test
        @DisplayName("错误提示类回答不应该入库")
        void shouldNotSaveInvalidAiResponse() {
            knowledgeBaseService.saveAnswer("炖排骨", "API key not configured", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("炖排骨", "Mock response", "cooking", USER_ID);

            assertEquals(0, repository.findAll().size());
        }
    }

    @Nested
    @DisplayName("查找答案（相似度匹配）")
    class FindAnswer {

        @BeforeEach
        void initData() {
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "{title:西红柿炒鸡蛋}", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("怎么挑西瓜", "{品类:西瓜}", "shopping", USER_ID);
            knowledgeBaseService.saveAnswer("水龙头漏水怎么办", "{problem:水龙头漏水}", "repair", USER_ID);
            knowledgeBaseService.saveAnswer("西红柿炒鸡蛋怎么做", "{title:其他用户答案}", "cooking", OTHER_USER_ID);
        }

        @Test
        @DisplayName("精确匹配应该命中缓存")
        void shouldHitExactMatch() {
            String result = knowledgeBaseService.findAnswer("西红柿炒鸡蛋怎么做", "cooking", USER_ID);

            assertNotNull(result);
            assertTrue(result.contains("西红柿炒鸡蛋"));
        }

        @Test
        @DisplayName("相同核心不同表述应该命中缓存")
        void shouldHitCoreMatch() {
            String result = knowledgeBaseService.findAnswer("西红柿炒鸡蛋如何做", "cooking", USER_ID);

            assertNotNull(result);
        }

        @Test
        @DisplayName("相似问题应该命中缓存")
        void shouldHitSimilarMatch() {
            String result = knowledgeBaseService.findAnswer("怎么挑西瓜？", "shopping", USER_ID);

            assertNotNull(result);
        }

        @Test
        @DisplayName("不相关的问题应该返回 null")
        void shouldReturnNullForUnrelated() {
            String result = knowledgeBaseService.findAnswer("空调不制冷怎么办", "repair", USER_ID);

            assertNull(result);
        }

        @Test
        @DisplayName("跨场景不匹配")
        void shouldNotMatchAcrossScenes() {
            String result = knowledgeBaseService.findAnswer("西红柿炒鸡蛋怎么做", "shopping", USER_ID);

            assertNull(result);
        }

        @Test
        @DisplayName("不能命中其他用户的缓存")
        void shouldNotHitOtherUsersCache() {
            String result = knowledgeBaseService.findAnswer("水龙头漏水怎么办", "repair", OTHER_USER_ID);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("搜索常识库")
    class Search {

        @BeforeEach
        void initData() {
            knowledgeBaseService.saveAnswer("红烧排骨怎么做", "做法...", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("番茄牛腩怎么做", "做法...", "cooking", USER_ID);
            knowledgeBaseService.saveAnswer("怎么挑苹果", "看颜色...", "shopping", USER_ID);
            knowledgeBaseService.saveAnswer("红烧排骨怎么做", "其他用户做法...", "cooking", OTHER_USER_ID);
        }

        @Test
        void shouldSearchByKeyword() {
            List<KnowledgeBase> result = knowledgeBaseService.search("排骨", null, USER_ID);

            assertEquals(1, result.size());
            assertEquals(USER_ID, result.get(0).getUserId());
        }

        @Test
        void shouldFilterByScene() {
            List<KnowledgeBase> result = knowledgeBaseService.search(null, "cooking", USER_ID);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(kb -> USER_ID.equals(kb.getUserId())));
        }

        @Test
        void shouldReturnEmptyForNoMatch() {
            List<KnowledgeBase> result = knowledgeBaseService.search("xyzNotFound", null, USER_ID);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("标记有用")
    class MarkHelpful {

        @Test
        void shouldIncrementHelpfulCountForOwner() {
            knowledgeBaseService.saveAnswer("测试问题", "测试答案", "other", USER_ID);
            Long id = repository.findAll().get(0).getId();

            knowledgeBaseService.markHelpful(id, USER_ID);

            assertEquals(1, repository.findById(id).orElseThrow().getHelpfulCount());
        }

        @Test
        void shouldNotIncrementHelpfulCountForOtherUser() {
            knowledgeBaseService.saveAnswer("测试问题", "测试答案", "other", USER_ID);
            Long id = repository.findAll().get(0).getId();

            knowledgeBaseService.markHelpful(id, OTHER_USER_ID);

            assertEquals(0, repository.findById(id).orElseThrow().getHelpfulCount());
        }

        @Test
        void shouldNotThrowForNonExistentId() {
            assertDoesNotThrow(() -> knowledgeBaseService.markHelpful(99999L, USER_ID));
        }
    }
}
