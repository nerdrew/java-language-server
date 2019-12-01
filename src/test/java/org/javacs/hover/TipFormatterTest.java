package org.javacs.hover;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class TipFormatterTest {
    @Test
    public void formatSimpleTags() {
        assertThat(TipFormatter.asMarkdown("<i>foo</i>"), equalTo("*foo*"));
        assertThat(TipFormatter.asMarkdown("<b>foo</b>"), equalTo("**foo**"));
        assertThat(TipFormatter.asMarkdown("<pre>foo</pre>"), equalTo("`foo`"));
        assertThat(TipFormatter.asMarkdown("<code>foo</code>"), equalTo("`foo`"));
        assertThat(TipFormatter.asMarkdown("{@code foo}"), equalTo("`foo`"));
        assertThat(
                TipFormatter.asMarkdown("<a href=\"#bar\">foo</a>"),
                equalTo("foo")); // TODO it would be nice if this converted to a link
    }

    @Test
    public void formatMultipleTags() {
        assertThat(TipFormatter.asMarkdown("<code>foo</code> <code>bar</code>"), equalTo("`foo` `bar`"));
        assertThat(TipFormatter.asMarkdown("{@code foo} {@code bar}"), equalTo("`foo` `bar`"));
    }
}