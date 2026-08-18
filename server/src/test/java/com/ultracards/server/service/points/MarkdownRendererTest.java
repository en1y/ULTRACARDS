package com.ultracards.server.service.points;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {
    private final MarkdownRenderer markdown = new MarkdownRenderer();

    @Test
    void rendersMarkdownAndSanitizesUntrustedHtml() {
        var html = markdown.render("""
                ## Weekend Cup

                - [x] Join a lobby
                - [ ] Win **three** games

                | Game | Reward |
                | --- | ---: |
                | Durak | 500 P |

                Visit https://example.com or read ~~old rules~~ `carefully`.

                <script>alert('nope')</script>
                [unsafe](javascript:alert('nope'))
                """);

        assertThat(html).contains("<h2>Weekend Cup</h2>", "type=\"checkbox\"", "checked", "<strong>three</strong>",
                "<table>", "<th>Game</th>", "<td align=\"right\">500 P</td>",
                "href=\"https://example.com\"", "<del>old rules</del>", "<code>carefully</code>");
        assertThat(html).doesNotContain("<script", "javascript:");
    }
}
