package com.ultracards.ui.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false",
        "app.version=test-version",
        "app.site-url=https://ultracards.test"
})
class GuidePageRenderingTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void publicGuideSurfaceRendersFromTheRealGameConfigurations() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://ultracards.test\"")))
                .andExpect(content().string(containsString("name=\"description\"")))
                .andExpect(content().string(containsString("\"@type\": \"WebSite\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"home-secondary-actions\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/guides\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/leaderboards\"")));

        mockMvc.perform(get("/guides"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://ultracards.test/guides\"")))
                .andExpect(content().string(containsString("name=\"description\"")));

        mockMvc.perform(get("/guides/briskula"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://ultracards.test/guides/briskula\"")))
                .andExpect(content().string(containsString("name=\"description\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-players=\"2\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"trump-card\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-scroll-cue\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-coach-bubble\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-coach-tail\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-previous\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-footer-actions\"")));

        mockMvc.perform(get("/guides/treseta"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://ultracards.test/guides/treseta\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"FOUR_PLAYERS_WITH_TEAMS_WITH_DECLARATIONS\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-declarations=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-scroll-cue\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-coach-bubble\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-coach-tail\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-previous\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-footer-actions\"")));

        mockMvc.perform(get("/guides/poker"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"robots\" content=\"noindex,follow\"")));
        mockMvc.perform(get("/guides/durak"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://ultracards.test/guides/durak\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-rules\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/ui/guides.css?v=test-version")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui/guides/guide-durak.js?v=test-version")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/lobbies\"")));

        mockMvc.perform(get("/leaderboards"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://ultracards.test/leaderboards\"")))
                .andExpect(content().string(containsString("name=\"description\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/ui/leaderboards.css?v=test-version")));

        mockMvc.perform(get("/images/card-suits/italian/DENARI.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/images/card-suits/poker/HEARTS.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void crawlerDiscoveryFilesOnlyAdvertiseIndexablePublicPages() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Sitemap: https://ultracards.test/sitemap.xml")))
                .andExpect(content().string(containsString("Allow: /api/leaderboards")))
                .andExpect(content().string(containsString("Disallow: /api")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<loc>https://ultracards.test/guides/briskula</loc>")))
                .andExpect(content().string(containsString("<loc>https://ultracards.test/guides/treseta</loc>")))
                .andExpect(content().string(containsString("<loc>https://ultracards.test/guides/durak</loc>")))
                .andExpect(content().string(containsString("<loc>https://ultracards.test/leaderboards</loc>")))
                .andExpect(content().string(not(containsString("/guides/poker"))))
                .andExpect(content().string(not(containsString("/profile"))));
    }
}
