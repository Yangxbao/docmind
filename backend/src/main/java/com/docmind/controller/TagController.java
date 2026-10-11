package com.docmind.controller;

import com.docmind.common.Result;
import com.docmind.common.UserContext;
import com.docmind.service.TagService;
import com.docmind.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 标签接口：查询当前用户的标签（列表页筛选、预览页选择标签时用） */
@Tag(name = "标签")
@RestController
@RequestMapping("/api/tag")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "我的标签列表")
    @GetMapping("/list")
    public Result<List<TagVO>> list() {
        Long userId = UserContext.get();
        List<TagVO> tags = tagService.listUserTags(userId).stream()
                .map(t -> new TagVO(t.getId(), t.getName()))
                .toList();
        return Result.ok(tags);
    }
}
