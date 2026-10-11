package com.docmind.dto;

import java.util.List;

/** 手动修改文档标签的入参（传入的标签列表会整体替换旧标签） */
public record TagsUpdateDTO(
        List<String> tags
) {
}
