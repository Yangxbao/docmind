package com.docmind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docmind.entity.User;

/**
 * 用户表 Mapper。
 * 继承 BaseMapper 后自动拥有 insert/selectById/selectPage/updateById/deleteById 等通用方法，
 * 简单查询用 LambdaQueryWrapper 拼条件即可，无需手写 SQL。
 */
public interface UserMapper extends BaseMapper<User> {
}
