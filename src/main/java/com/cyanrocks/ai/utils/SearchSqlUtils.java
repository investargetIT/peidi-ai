package com.cyanrocks.ai.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.cyanrocks.ai.vo.request.SearchReq;
import com.cyanrocks.ai.vo.request.SortReq;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author wjq
 * @Date 2024/8/21 12:05
 */
@Component
public class SearchSqlUtils {

    public String buildSearchSql(List<SearchReq> searchReqs){
        StringBuilder searchSb = new StringBuilder();
        if (CollectionUtil.isNotEmpty(searchReqs)) {
            for (SearchReq searchReq : searchReqs) {
                if ("like".equals(searchReq.getSearchType())) {
                    String[] values = searchReq.getSearchValue().split("&#&");
                    if (values.length > 1){
                        searchSb.append(" (");
                        for (String value : values) {
                            value = value.replace("'","\\'").replace("%","\\%").replace("_","\\_");
                            searchSb.append(" " + this.convertToSnakeCase(searchReq.getSearchName()) + " like '%" + value + "%'");
                            searchSb.append(" or");
                        }
                        searchSb.delete(searchSb.length() - 2, searchSb.length());
                        searchSb.append(" )");
                    }else {
                        String value = searchReq.getSearchValue().replace("'","\\'").replace("%","\\%").replace("_","\\_");
                        searchSb.append(" " + this.convertToSnakeCase(searchReq.getSearchName()) + " like '%" + value + "%'");
                    }
                } else if ("equals".equals(searchReq.getSearchType())) {
                    String[] values = searchReq.getSearchValue().split("&#&");
                    if (values.length > 1){
                        searchSb.append(" (");
                        for (String value : values) {
                            searchSb.append(" " + this.convertToSnakeCase(searchReq.getSearchName()) + " = " +value);
                            searchSb.append(" or");
                        }
                        searchSb.delete(searchSb.length() - 2, searchSb.length());
                        searchSb.append(" )");
                    }else {
                        searchSb.append(" " + this.convertToSnakeCase(searchReq.getSearchName()) + " = " + searchReq.getSearchValue());
                    }
                } else if ("between".equals(searchReq.getSearchType())) {
                    String[] value = searchReq.getSearchValue().split(",");
                    searchSb.append(" " + this.convertToSnakeCase(searchReq.getSearchName()) + " <= "
                            + value[1] + " and " + this.convertToSnakeCase(searchReq.getSearchName()) + " >= " + value[0]);
                } else if ("betweenStr".equals(searchReq.getSearchType())) {
                    String[] value = searchReq.getSearchValue().split(",");
                    searchSb.append(" " + this.convertToSnakeCase(searchReq.getSearchName()) + " <= '"
                            + value[1] + "' and " + this.convertToSnakeCase(searchReq.getSearchName()) + " >= '" + value[0] + "'");
                }
                searchSb.append(" and");
            }
            searchSb.delete(searchSb.length() - 3, searchSb.length());
        }
        return searchSb.toString();
    }

    public String buildSortSql(List<SortReq> sortReqs){
        StringBuilder sortSb = new StringBuilder();
        if (CollectionUtil.isNotEmpty(sortReqs)) {
            for (SortReq sortReq : sortReqs) {
                if ("asc".equals(sortReq.getSortType())){
                    sortSb.append(this.convertToSnakeCase(sortReq.getSortName())).append(" asc,");
                }else if ("desc".equals(sortReq.getSortType())){
                    sortSb.append(this.convertToSnakeCase(sortReq.getSortName())).append(" desc,");
                }
            }
            sortSb.delete(sortSb.length() - 1, sortSb.length());
        }
        return sortSb.toString();
    }


    public String convertToSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 使用 StringBuilder 来构建结果字符串
        StringBuilder result = new StringBuilder();

        // 遍历输入字符串的每个字符
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            // 如果字符是大写字母
            if (Character.isUpperCase(ch)) {
                // 如果不是第一个字符，则在大写字母前添加下划线
                if (i > 0) {
                    result.append('_');
                }
                // 将大写字母转换为小写字母
                result.append(Character.toLowerCase(ch));
            } else {
                // 如果不是大写字母，直接添加到结果字符串中
                result.append(ch);
            }
        }

        return result.toString();
    }
}
