package com.docmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docmind.common.StopWords;
import com.docmind.entity.DocumentTag;
import com.docmind.entity.Tag;
import com.docmind.mapper.DocumentTagMapper;
import com.docmind.mapper.TagMapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自动打标签服务（不调用大模型：隐私安全、零成本、毫秒级完成）。
 *
 * 两路策略：
 *   ① 规则分类：文件名+正文开头命中关键词表 → 打上类目标签（会议/报告/论文/简历…）；
 *   ② 词频关键词：jieba 分词 + 停用词过滤 + 取出现次数最多的前 4 个词。
 * 两路合并去重，最多 6 个标签。
 */
@Service
public class TagService {

    /** 规则表：按顺序匹配，命中任意关键词即打该标签 */
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();

    static {
        RULES.put("会议", new String[]{"会议", "纪要", "议程", "参会"});
        RULES.put("报告", new String[]{"报告", "总结", "汇报", "复盘"});
        RULES.put("论文", new String[]{"论文", "Abstract", "参考文献"});
        RULES.put("方案设计", new String[]{"方案", "架构", "技术选型", "设计文档"});
        RULES.put("笔记", new String[]{"笔记", "读书", "学习"});
        RULES.put("简历", new String[]{"简历", "Resume", "工作经历", "教育背景"});
        RULES.put("合同", new String[]{"合同", "协议", "甲方", "乙方"});
        RULES.put("计划", new String[]{"TODO", "待办", "日程", "周报"});
    }

    private static final int MAX_TAGS = 6;
    private static final int SAMPLE_LENGTH = 2000;

    private final TagMapper tagMapper;
    private final DocumentTagMapper documentTagMapper;
    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    public TagService(TagMapper tagMapper, DocumentTagMapper documentTagMapper) {
        this.tagMapper = tagMapper;
        this.documentTagMapper = documentTagMapper;
    }

    /**
     * 自动生成标签。
     * @param fileName 文件名（文件名往往最浓缩地表达了文档主题）
     * @param content  提取出的正文（只取开头一段参与统计，控制耗时）
     */
    public List<String> autoTags(String fileName, String content) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String sample = (fileName == null ? "" : fileName) + "\n"
                + (content == null ? "" : content.substring(0, Math.min(content.length(), SAMPLE_LENGTH)));

        // ① 规则分类
        RULES.forEach((tag, words) -> {
            for (String word : words) {
                if (sample.contains(word)) {
                    tags.add(tag);
                    break;
                }
            }
        });

        // ② 词频 Top4：词长≥2、非停用词、非纯数字
        Map<String, Long> freq = segmenter.process(sample, JiebaSegmenter.SegMode.SEARCH).stream()
                .map(token -> token.word)
                .filter(word -> word.length() >= 2
                        && !StopWords.isStopWord(word)
                        && !word.matches("\\d+"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(4)
                .map(Map.Entry::getKey)
                .forEach(tags::add);

        return List.copyOf(tags).stream().limit(MAX_TAGS).toList();
    }

    /**
     * 给一组文档批量加载标签名（用于列表页/搜索结果展示）。
     * 固定两次查询（关联表 + 标签表），避免"每篇文档查一次库"的 N+1 问题。
     *
     * @return docId → 标签名列表
     */
    public Map<Long, List<String>> loadTagsMap(List<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Map.of();
        }
        List<DocumentTag> relations = documentTagMapper.selectList(
                new LambdaQueryWrapper<DocumentTag>().in(DocumentTag::getDocumentId, docIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = relations.stream().map(DocumentTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> tagNames = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));

        Map<Long, List<String>> result = new HashMap<>();
        for (DocumentTag relation : relations) {
            String name = tagNames.get(relation.getTagId());
            if (name != null) {
                result.computeIfAbsent(relation.getDocumentId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    /** 找到或创建标签（按 userId + name 唯一） */
    public Tag findOrCreate(Long userId, String name) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, name));
        if (tag != null) {
            return tag;
        }
        Tag fresh = new Tag();
        fresh.setUserId(userId);
        fresh.setName(name);
        try {
            tagMapper.insert(fresh);
            return fresh;
        } catch (Exception e) {
            // 并发下可能撞唯一索引，重新查一次即可
            return tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getUserId, userId)
                    .eq(Tag::getName, name));
        }
    }

    /** 按标签 id 反查关联的文档 id 列表（列表页"按标签筛选"用） */
    public List<Long> docIdsByTag(Long tagId) {
        return documentTagMapper.selectList(new LambdaQueryWrapper<DocumentTag>()
                        .eq(DocumentTag::getTagId, tagId))
                .stream()
                .map(DocumentTag::getDocumentId)
                .toList();
    }

    /** 清空某文档的标签关联（重新打标 / 手动改标签前调用） */
    public void clearDocTags(Long docId) {
        documentTagMapper.delete(new LambdaQueryWrapper<DocumentTag>()
                .eq(DocumentTag::getDocumentId, docId));
    }

    /** 建立文档-标签关联（已存在则跳过） */
    public void bindTag(Long docId, Long tagId) {
        Long exists = documentTagMapper.selectCount(new LambdaQueryWrapper<DocumentTag>()
                .eq(DocumentTag::getDocumentId, docId)
                .eq(DocumentTag::getTagId, tagId));
        if (exists == null || exists == 0) {
            DocumentTag relation = new DocumentTag();
            relation.setDocumentId(docId);
            relation.setTagId(tagId);
            documentTagMapper.insert(relation);
        }
    }

    /** 查询某用户的全部标签 */
    public List<Tag> listUserTags(Long userId) {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .orderByAsc(Tag::getName));
    }
}
