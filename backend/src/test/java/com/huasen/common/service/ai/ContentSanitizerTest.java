package com.huasen.common.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContentSanitizer 单元测试
 * 测试 HTML/Markdown 清洗和长度截断逻辑
 */
class ContentSanitizerTest {

    private ContentSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new ContentSanitizer();
    }

    /**
     * Test 1: HTML tags stripped from article body
     */
    @Test
    void testHtmlTagsStripped() {
        String input = "<p>Hello <b>world</b></p><div>Test <span>content</span></div>";
        String result = sanitizer.sanitizeForPrompt(input);

        assertFalse(result.contains("<p>"));
        assertFalse(result.contains("<b>"));
        assertFalse(result.contains("</b>"));
        assertFalse(result.contains("<div>"));
        assertFalse(result.contains("<span>"));
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("world"));
        assertTrue(result.contains("Test"));
        assertTrue(result.contains("content"));
    }

    /**
     * Test 2: Markdown syntax (**, ##, lists) stripped to plain text
     */
    @Test
    void testMarkdownSyntaxStripped() {
        String input = "## Title\n\n**bold** text and *italic* text\n\n- list item 1\n- list item 2\n\n```java\ncode block\n```";
        String result = sanitizer.sanitizeForPrompt(input);

        // Should not contain Markdown syntax for headers, bold, code blocks
        assertFalse(result.contains("##"));
        assertFalse(result.contains("**"));
        assertFalse(result.contains("```"));

        // Should contain the actual text content
        assertTrue(result.contains("Title"));
        assertTrue(result.contains("bold"));
        assertTrue(result.contains("text"));
        assertTrue(result.contains("italic"));
        assertTrue(result.contains("list item 1"));
        assertTrue(result.contains("list item 2"));

        // Commonmark text renderer converts Markdown to plain text
        // The key is that bold/italic/headers are rendered as plain text
        assertFalse(result.contains("**bold**"));
        assertFalse(result.contains("*italic*"));
    }

    /**
     * Test 3: Input > 6000 chars truncated to 6000 with head+tail strategy
     */
    @Test
    void testLongContentTruncated() {
        // Create a 7000-character string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 700; i++) {
            sb.append("1234567890"); // 10 chars each
        }
        String input = sb.toString();
        assertEquals(7000, input.length());

        String result = sanitizer.sanitizeForPrompt(input);

        // Should be truncated to approximately 6000 chars (4000 head + [...] + 2000 tail)
        assertTrue(result.length() <= 6100); // Allow some margin for [...] separator
        assertTrue(result.length() >= 6000);
        assertTrue(result.contains("[...]"), "Truncated content should contain [...] separator");

        // Head should be present (first 100 chars)
        assertTrue(result.startsWith(input.substring(0, 100)));

        // Tail should be present (last 100 chars)
        assertTrue(result.endsWith(input.substring(6900)));
    }

    /**
     * Test 4: Input < 6000 chars returned unchanged (after strip)
     */
    @Test
    void testShortContentNotTruncated() {
        String input = "This is a short article with less than 6000 characters. " +
                       "It should be returned without truncation.";

        String result = sanitizer.sanitizeForPrompt(input);

        // Should not be truncated
        assertFalse(result.contains("[...]"));
        assertTrue(result.contains("This is a short article"));
        assertTrue(result.contains("It should be returned without truncation"));
    }

    /**
     * Test 5: Empty/null input returns empty string without exception
     */
    @Test
    void testNullAndEmptyInputHandling() {
        // Null input
        String result1 = sanitizer.sanitizeForPrompt(null);
        assertNotNull(result1);
        assertEquals("", result1);

        // Empty string
        String result2 = sanitizer.sanitizeForPrompt("");
        assertNotNull(result2);
        assertEquals("", result2);

        // Blank string (only whitespace)
        String result3 = sanitizer.sanitizeForPrompt("   \n\t  ");
        assertNotNull(result3);
        assertEquals("", result3);
    }

    /**
     * Test 6: HTML entities are decoded
     */
    @Test
    void testHtmlEntitiesDecoded() {
        String input = "Test &nbsp; content with &lt;tag&gt; and &amp; symbol &quot;quoted&quot;";
        String result = sanitizer.sanitizeForPrompt(input);

        assertTrue(result.contains("<"));
        assertTrue(result.contains(">"));
        assertTrue(result.contains("&"));
        assertTrue(result.contains("\""));
        assertFalse(result.contains("&nbsp;"));
        assertFalse(result.contains("&lt;"));
        assertFalse(result.contains("&gt;"));
        assertFalse(result.contains("&amp;"));
        assertFalse(result.contains("&quot;"));
    }

    /**
     * Test 7: Multiple consecutive whitespaces normalized to single space
     */
    @Test
    void testWhitespaceNormalized() {
        String input = "Text   with    multiple     spaces\n\n\nand\t\ttabs";
        String result = sanitizer.sanitizeForPrompt(input);

        // Should not contain multiple consecutive spaces
        assertFalse(result.contains("  "));
        assertFalse(result.contains("\n\n"));
        assertFalse(result.contains("\t\t"));

        // Should contain the words
        assertTrue(result.contains("Text"));
        assertTrue(result.contains("with"));
        assertTrue(result.contains("multiple"));
        assertTrue(result.contains("spaces"));
        assertTrue(result.contains("and"));
        assertTrue(result.contains("tabs"));
    }

    /**
     * Test 8: Mixed HTML and Markdown are both stripped
     */
    @Test
    void testMixedHtmlAndMarkdownStripped() {
        String input = "<p>## Header in HTML</p>\n\n**Bold <strong>nested</strong> text**";
        String result = sanitizer.sanitizeForPrompt(input);

        // No HTML tags
        assertFalse(result.contains("<p>"));
        assertFalse(result.contains("<strong>"));

        // No Markdown syntax
        assertFalse(result.contains("##"));
        assertFalse(result.contains("**"));

        // Content preserved
        assertTrue(result.contains("Header in HTML"));
        assertTrue(result.contains("Bold"));
        assertTrue(result.contains("nested"));
        assertTrue(result.contains("text"));
    }
}
