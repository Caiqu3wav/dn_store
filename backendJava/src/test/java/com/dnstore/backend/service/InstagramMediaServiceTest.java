package com.dnstore.backend.service;

import com.dnstore.backend.dto.InstagramPostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InstagramMediaServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private InstagramMediaService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new InstagramMediaService(
                restTemplate,
                "https://graph.instagram.com",
                900_000,
                "mtb-user-id",
                "mtb-token",
                "store-user-id",
                "store-token"
        );
    }

    @Test
    void getMtbPostsReturnsImagesVideosAndCarouselChildrenAndCachesTheFeed() {
        server.expect(requestTo("https://graph.instagram.com/mtb-user-id/media"
                        + "?fields=id%2Ccaption%2Cmedia_type%2Cmedia_url%2Cthumbnail_url%2Cpermalink%2Ctimestamp"
                        + "&limit=12&access_token=mtb-token"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("fields", equalTo(
                        "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp"
                )))
                .andExpect(queryParam("limit", equalTo("12")))
                .andExpect(queryParam("access_token", equalTo("mtb-token")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"image-1","caption":"Foto","media_type":"IMAGE","media_url":"https://cdn.example/image.jpg","permalink":"https://instagram.com/p/1","timestamp":"2026-01-02T03:04:05+0000"},
                          {"id":"video-1","caption":"Vídeo","media_type":"VIDEO","media_url":"https://cdn.example/video.mp4","thumbnail_url":"https://cdn.example/video.jpg","permalink":"https://instagram.com/p/2","timestamp":"2026-01-03T03:04:05+0000"},
                          {"id":"album-1","caption":"Carrossel","media_type":"CAROUSEL_ALBUM","permalink":"https://instagram.com/p/3","timestamp":"2026-01-04T03:04:05+0000"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.instagram.com/album-1/children"
                        + "?fields=id%2Cmedia_type%2Cmedia_url%2Cthumbnail_url%2Ctimestamp&access_token=mtb-token"))
                .andExpect(queryParam("access_token", equalTo("mtb-token")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"child-1","media_type":"IMAGE","media_url":"https://cdn.example/child.jpg"},
                          {"id":"child-2","media_type":"VIDEO","media_url":"https://cdn.example/child.mp4","thumbnail_url":"https://cdn.example/child-video.jpg"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<InstagramPostResponse> posts = service.getMtbPosts();
        List<InstagramPostResponse> cachedPosts = service.getMtbPosts();

        assertEquals(3, posts.size());
        assertEquals("IMAGE", posts.get(0).mediaType());
        assertEquals("VIDEO", posts.get(1).mediaType());
        assertEquals("CAROUSEL_ALBUM", posts.get(2).mediaType());
        assertEquals(2, posts.get(2).children().size());
        assertEquals("VIDEO", posts.get(2).children().get(1).mediaType());
        assertEquals(posts, cachedPosts);
        server.verify();
    }

    @Test
    void refreshedTokenIsUsedForSubsequentRequests() {
        server.expect(requestTo("https://graph.instagram.com/refresh_access_token"
                        + "?grant_type=ig_refresh_token&access_token=mtb-token"))
                .andExpect(queryParam("grant_type", equalTo("ig_refresh_token")))
                .andExpect(queryParam("access_token", equalTo("mtb-token")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"rotated-token\",\"token_type\":\"bearer\",\"expires_in\":5184000}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("https://graph.instagram.com/refresh_access_token"
                        + "?grant_type=ig_refresh_token&access_token=store-token"))
                .andExpect(queryParam("grant_type", equalTo("ig_refresh_token")))
                .andExpect(queryParam("access_token", equalTo("store-token")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"rotated-store-token\",\"token_type\":\"bearer\",\"expires_in\":5184000}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("https://graph.instagram.com/mtb-user-id/media"
                        + "?fields=id%2Ccaption%2Cmedia_type%2Cmedia_url%2Cthumbnail_url%2Cpermalink%2Ctimestamp"
                        + "&limit=12&access_token=rotated-token"))
                .andExpect(queryParam("access_token", equalTo("rotated-token")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.instagram.com/store-user-id/media"
                        + "?fields=id%2Ccaption%2Cmedia_type%2Cmedia_url%2Cthumbnail_url%2Cpermalink%2Ctimestamp"
                        + "&limit=12&access_token=rotated-store-token"))
                .andExpect(queryParam("access_token", equalTo("rotated-store-token")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        service.refreshConfiguredAccessTokens();
        service.getMtbPosts();
        service.getStorePosts();

        server.verify();
    }

    @Test
    void missingProfileCredentialsReturnServiceUnavailable() {
        InstagramMediaService unconfigured = new InstagramMediaService(
                restTemplate,
                "https://graph.instagram.com",
                900_000,
                "",
                "",
                "store-user-id",
                "store-token"
        );

        assertThrows(ResponseStatusException.class, unconfigured::getMtbPosts);
        server.verify();
    }
}
