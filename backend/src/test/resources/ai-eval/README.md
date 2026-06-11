# AI Evaluation Dataset — TinyBlog Article Summary

## Purpose

This directory contains the **gold standard evaluation dataset** for阿里百炼 (Alibaba Bailian) AI-generated article summaries. The dataset is used to:

1. **Manual review**: Portal owner inspects AI output vs human-written "ideal summary"
2. **Offline quality metrics**: Calculate edit_ratio (Levenshtein distance / max length) to track prompt quality over time
3. **LLM judge evaluation** (future): Automated faithfulness/density/thesis-capture scoring via qwen-max as judge
4. **Promptfoo CI integration** (future): Automated regression testing on every prompt change

## Dataset Structure

**File**: `tinyblog-gold-dataset.yaml`

Each row represents one TinyBlog article with:
- `id`: Unique identifier (art_001, art_002, ...)
- `feature`: Always "tinyblog_summary"
- `input.article_id`: TinyBlogPost.id (from MySQL, nullable)
- `input.body`: Full article Markdown content
- `gold_output`: Human-written "ideal summary" (70-110 Chinese characters)
- `notes`: Why this is the right summary (reasoning / context)
- `failure_mode_target`: Which adversarial case this row guards against

**Current size**: 10 rows
- 4 typical tech blog posts (1500–3000 chars)
- 3 edge cases (very long, very short, code-heavy)
- 3 adversarial (thesis in conclusion, clickbait title, Q&A structure)

**Target size**: 20 rows (expand when new failure modes discovered)

## How to Populate

The dataset ships as a **template** with placeholders (`TODO: 粘贴真实文章正文`). The portal owner must:

1. **Select 10 representative TinyBlog articles** from production database:
   ```sql
   SELECT id, title, content FROM tiny_blog_post 
   WHERE content IS NOT NULL AND LENGTH(content) > 500
   ORDER BY create_time DESC LIMIT 20;
   ```
   Pick 4 typical + 3 edge + 3 adversarial per composition rules.

2. **For each article**:
   - Copy `id` → `input.article_id`
   - Copy `content` (Markdown) → `input.body`
   - Write your "ideal summary" (70-110 chars) → `gold_output`
   - Document your reasoning → `notes`
   - Tag the failure mode → `failure_mode_target`

3. **Validation checklist** (for each gold_output):
   - Length: 70-110 Chinese characters (use `String.codePointCount()`)
   - Objective: No invented features, no marketing fluff
   - Thesis-captured: Not just lead paragraph, covers core argument
   - Clean format: No prefix ("摘要："), no quotes, no Markdown
   - Informative: A reader can decide whether to read the full article

## How to Use

### Manual Review (current Phase 12)

Run ArticleSummaryService on each row and compare output:

```java
ArticleSummaryService service = ...; // inject
for (Row row : dataset) {
    String aiSummary = service.generateSummary(row.input.article_id, row.input.body);
    int editDistance = LevenshteinDistance.compute(aiSummary, row.gold_output);
    double editRatio = (double) editDistance / Math.max(aiSummary.length(), row.gold_output.length());
    System.out.printf("Row %s: edit_ratio=%.2f%n", row.id, editRatio);
}
```

If **avg edit_ratio > 0.4** across all rows → prompts need revision (see `PromptTemplates.java`).

### Promptfoo Integration (future Phase 12.x)

Create `promptfooconfig.yaml` pointing to this dataset:

```yaml
providers:
  - dashscope:
      model: qwen-plus
      config:
        apiKey: ${DASHSCOPE_API_KEY}

prompts:
  - file://PromptTemplates.ARTICLE_SUMMARY_SYSTEM_V1.txt
  - "{{input.body}}"

tests:
  - vars:
      file: tinyblog-gold-dataset.yaml
    assert:
      - type: is-length
        min: 70
        max: 110
      - type: not-contains
        value: "摘要："
      - type: llm-rubric
        provider: dashscope:qwen-max
        rubric: "忠实于原文，无编造内容"
```

Run: `promptfoo eval --config promptfooconfig.yaml`

Gate PR merge if:
- D1+D5 (code checks) < 100% pass rate
- D2 (faithfulness rubric) < 90% pass rate

## Quality Dimensions

Per [12-RESEARCH.md §5.2](../../.planning/phases/12-ai-content-generation/12-RESEARCH.md):

| Dimension | Priority | Measurement | Pass Criteria |
|-----------|----------|-------------|---------------|
| D1: 长度契合 (length fits) | Critical | Code (char count) | 70-110 chars + finish_reason=stop |
| D2: 客观忠实零编造 (faithfulness) | Critical | LLM judge (qwen-max) | Every claim grounded in input |
| D3: 信息密度 (info density) | High | LLM judge | Concrete nouns, not marketing fluff |
| D4: 抓主旨 (thesis captured) | High | LLM judge | Reflects central argument, not just intro |
| D5: 纯净输出 (clean format) | High | Code (regex) | No prefix/quotes/Markdown |

## Expansion Rules

Add new rows when:
1. Production AI output exhibits a new failure mode not covered by existing rows
2. Prompt version bumps (add 2-3 rows to validate the change)
3. Model upgrade (qwen-plus → qwen-max — revalidate with full set)

Keep the dataset representative but compact (<50 rows). Archive old rows when failure modes are fixed.

## References

- Phase 12 Context: `.planning/phases/12-ai-content-generation/12-CONTEXT.md`
- AI-SPEC (research): `.planning/phases/12-ai-content-generation/12-RESEARCH.md`
- Prompt templates: `backend/src/main/java/com/huasen/common/service/ai/PromptTemplates.java`
