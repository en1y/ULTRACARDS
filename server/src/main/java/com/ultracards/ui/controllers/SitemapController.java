package com.ultracards.ui.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SitemapController {
    private final String siteUrl;

    public SitemapController(@Value("${app.site-url}") String siteUrl) {
        this.siteUrl = siteUrl.replaceFirst("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        var urls = List.of(
                urlEntry(siteUrl + "/"),
                urlEntry(siteUrl + "/guides"),
                urlEntry(siteUrl + "/guides/briskula"),
                urlEntry(siteUrl + "/guides/treseta"),
                urlEntry(siteUrl + "/guides/durak"),
                urlEntry(siteUrl + "/leaderboards")
        );

        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                %s
                </urlset>
                """.formatted(String.join(System.lineSeparator(), urls));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        var content = """
                User-agent: *
                Allow: /
                Allow: /api/cards/
                Allow: /api/leaderboards
                Disallow: /active
                Disallow: /admin
                Disallow: /api
                Disallow: /game
                Disallow: /lobbies
                Disallow: /profile

                Sitemap: %s/sitemap.xml
                """.formatted(siteUrl);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }

    private String urlEntry(String location) {
        return """
                  <url>
                    <loc>%s</loc>
                  </url>
                """.formatted(location);
    }
}
