package com.docmind.service;

import com.docmind.common.StopWords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动标签单元测试。
 * 注意：标签规则的"规则命中/词频提取"是纯逻辑，但 findOrCreate 等方法依赖数据库，
 * 这里只测纯逻辑部分，数据库交互在集成环境验证。
 */
class TagServiceTest {

    private final TagService tagService = new TagService(null, null);

    @Test
    @DisplayName("规则分类：会议纪要应命中'会议'标签")
    void ruleMeeting() {
        List<String> tags = tagService.autoTags("产品周会纪要.docx.md", "本周参会人员对齐了版本计划");
        assertTrue(tags.contains("会议"), "文件名+正文命中'会议/纪要/参会'应打上'会议'标签");
    }

    @Test
    @DisplayName("规则分类：简历应命中'简历'标签")
    void ruleResume() {
        List<String> tags = tagService.autoTags("我的简历.md", "工作经历：负责后端开发，教育背景：某大学");
        assertTrue(tags.contains("简历"));
    }

    @Test
    @DisplayName("词频关键词：应提取正文里重复出现的核心词，且过滤停用词")
    void keywordFrequency() {
        String content = ("向量数据库的选型非常关键。"
                + "向量数据库的性能决定了检索速度。"
                + "综合来看向量数据库是本项目的核心组件。").repeat(3);
        List<String> tags = tagService.autoTags("技术调研.md", content);
        assertTrue(tags.contains("向量数据库") || tags.stream().anyMatch(t -> t.contains("向量")),
                "高频词'向量数据库'应成为标签, 实际: " + tags);
        assertTrue(tags.stream().noneMatch(StopWords::isStopWord), "停用词不能成为标签");
        assertTrue(tags.size() <= 6, "标签最多6个");
    }

    @Test
    @DisplayName("空内容也应基于文件名产出标签，且不抛异常")
    void emptyContentOk() {
        List<String> tags = tagService.autoTags("会议纪要.md", "");
        assertTrue(tags.contains("会议"));
        assertFalse(tagService.autoTags(null, null) == null);
    }
}
