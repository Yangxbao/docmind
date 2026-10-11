package com.docmind.controller;

import com.docmind.common.Result;
import com.docmind.dto.SearchDTO;
import com.docmind.service.SearchService;
import com.docmind.vo.HistoryVO;
import com.docmind.vo.SearchItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 检索接口：语义/关键词检索、搜索历史 */
@Tag(name = "检索")
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "智能检索（mode=semantic 语义 / keyword 关键词）")
    @PostMapping
    public Result<List<SearchItemVO>> search(@Valid @RequestBody SearchDTO dto) {
        return Result.ok(searchService.search(dto));
    }

    @Operation(summary = "搜索历史（去重，最近 8 条）")
    @GetMapping("/history")
    public Result<List<HistoryVO>> history() {
        return Result.ok(searchService.history());
    }
}
