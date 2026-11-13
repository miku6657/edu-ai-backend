
package com.edu.ai.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    // 当前页码
    private long current;

    // 每页记录数
    private long size;

    // 总记录数
    private long total;

    // 总页数
    private long pages;

    // 当前页的数据
    private List<T> records;

    public PageResult(
            long current,
            long size,
            long total,
            List<T> records) {

        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records;

        // 计算总页数
        this.pages = size > 0
                ? total / size + (total % size == 0 ? 0 : 1)
                : 0;
    }
}
