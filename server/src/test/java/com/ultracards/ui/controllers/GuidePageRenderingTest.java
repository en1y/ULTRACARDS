package com.ultracards.ui.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"home-secondary-actions\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/guides\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/leaderboards\"")));

        mockMvc.perform(get("/guides/briskula"))
                .andExpect(status().isOk())
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"FOUR_PLAYERS_WITH_TEAMS_WITH_DECLARATIONS\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-declarations=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-scroll-cue\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-coach-bubble\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-coach-tail\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"guide-previous\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"guide-footer-actions\"")));

        mockMvc.perform(get("/guides/poker")).andExpect(status().isOk());
        mockMvc.perform(get("/guides/durak")).andExpect(status().isOk());
    }
}
