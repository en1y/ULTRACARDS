package com.ultracards.server.service.points;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ponytail: hand-rolled subset (links, bold, italic, code, line breaks) for short
 * admin-authored event blurbs, run through the project's existing OWASP HTML policy
 * (already a dependency, see ChatService) so a stray literal tag in the input can't
 * survive. Swap for a real CommonMark parser if admins need lists/headings/tables.
 */
@Component("markdown")
public class MarkdownLite {
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("a", "strong", "em", "code", "br")
            .allowAttributes("href", "target", "rel").onElements("a")
            .allowStandardUrlProtocols()
            .toFactory();

    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\((https?://[^\\s)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    public String render(String text) {
        if (text == null || text.isBlank()) return "";
        var html = LINK.matcher(text).replaceAll("<a href=\"$2\" target=\"_blank\" rel=\"noopener noreferrer\">$1</a>");
        html = BOLD.matcher(html).replaceAll("<strong>$1</strong>");
        html = ITALIC.matcher(html).replaceAll("<em>$1</em>");
        html = CODE.matcher(html).replaceAll("<code>$1</code>");
        html = html.replace("\n", "<br>");
        return POLICY.sanitize(html);
    }
}
