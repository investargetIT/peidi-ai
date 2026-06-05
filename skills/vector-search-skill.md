# Vector Search Skill

## 接口信息

- **路径**: `https://api.peidigroup.cn/ai/milvus/vector/search`
- **方法**: `POST`
- **功能**: 纯向量检索文章（不调用AI生成回答）
- **认证**: 需携带 Authorization Token

## 认证说明

请求需要携带 Authorization Header：

```
Authorization: 填写 token
```

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | String | 是 | 查询问题 |
| reportType | String | 否 | 报告类型过滤 |
| topK | Integer | 否 | 返回结果数量 |

## 返回

```json
{
  "code": 200,
  "success": true,
  "data": {
    "rewriteQuestion": "重写后的问题",
    "results": [
      {
        "title": "文档标题",
        "source": "文档来源",
        "reportType": "文档类型",
        "text": "文档内容",
        "score": 0.85
      }
    ],
    "id": "检索记录ID"
  }
}
```

## 示例

```bash
# 基本调用
curl -X POST "https://api.peidigroup.cn/ai/milvus/vector/search?question=产品检测方法" \
  -H "Authorization: 填写 token"

# 指定类型
curl -X POST "https://api.peidigroup.cn/ai/milvus/vector/search?question=产品检测方法&reportType=检测报告" \
  -H "Authorization: 填写 token"

# 指定数量
curl -X POST "https://api.peidigroup.cn/ai/milvus/vector/search?question=产品检测方法&topK=10" \
  -H "Authorization: 填写 token"
```

## 特点

- 纯向量检索，不调用AI模型
- 支持按文档类型过滤
- 自动问题重写优化
- 返回相似度分数
