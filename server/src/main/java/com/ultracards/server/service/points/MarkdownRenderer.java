package com.ultracards.server.service.points;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarkdownRenderer {
    private static final List<Extension> EXTENSIONS = List.of(
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TablesExtension.create(),
            TaskListItemsExtension.create()
    );
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol",
                    "li", "strong", "em", "code", "pre", "a", "hr", "table", "thead", "tbody", "tr",
                    "th", "td", "del", "input")
            .allowAttributes("href").onElements("a")
            .allowAttributes("align").onElements("th", "td")
            .allowAttributes("type", "checked", "disabled").onElements("input")
            .allowStandardUrlProtocols()
            .toFactory();

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return POLICY.sanitize(RENDERER.render(PARSER.parse(markdown)));
    }
}
