package com.dnstore.backend.service;

import com.dnstore.backend.dto.InstagramPostResponse;
import com.dnstore.backend.dto.InstagramPostResponse.MediaItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class InstagramMediaService {

    private static final String MEDIA_FIELDS =
            "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp";
    private static final String CHILD_FIELDS = "id,media_type,media_url,thumbnail_url,timestamp";
    private static final int MEDIA_LIMIT = 12;

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final long cacheTtlMillis;
    private final Map<Profile, CachedPosts> cache = new ConcurrentHashMap<>();
    private final Account mtbAccount;
    private final Account storeAccount;

    public InstagramMediaService(
            RestTemplate restTemplate,
            @Value("${instagram.api-base-url:https://graph.instagram.com}") String apiBaseUrl,
            @Value("${instagram.media-cache-ttl-ms:900000}") long cacheTtlMillis,
            @Value("${instagram.mtb.user-id:}") String mtbUserId,
            @Value("${instagram.mtb.access-token:}") String mtbAccessToken,
            @Value("${instagram.store.user-id:}") String storeUserId,
            @Value("${instagram.store.access-token:}") String storeAccessToken
    ) {
        this.restTemplate = restTemplate;
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.cacheTtlMillis = cacheTtlMillis;
        this.mtbAccount = new Account(Profile.MTB, mtbUserId, mtbAccessToken);
        this.storeAccount = new Account(Profile.STORE, storeUserId, storeAccessToken);
    }

    public List<InstagramPostResponse> getMtbPosts() {
        return getPosts(mtbAccount);
    }

    public List<InstagramPostResponse> getStorePosts() {
        return getPosts(storeAccount);
    }

    @Scheduled(
            initialDelayString = "${instagram.token-refresh-initial-delay-ms:86400000}",
            fixedDelayString = "${instagram.token-refresh-interval-ms:86400000}"
    )
    public void refreshConfiguredAccessTokens() {
        refreshToken(mtbAccount);
        refreshToken(storeAccount);
    }

    private List<InstagramPostResponse> getPosts(Account account) {
        if (!account.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Instagram credentials are not configured for " + account.profile.label
            );
        }

        CachedPosts cached = cache.get(account.profile);
        if (cached != null && cached.expiresAtMillis > System.currentTimeMillis()) {
            return cached.posts;
        }

        synchronized (account) {
            cached = cache.get(account.profile);
            if (cached != null && cached.expiresAtMillis > System.currentTimeMillis()) {
                return cached.posts;
            }

            List<InstagramPostResponse> posts = fetchPosts(account);
            cache.put(account.profile, new CachedPosts(
                    posts,
                    System.currentTimeMillis() + cacheTtlMillis
            ));
            return posts;
        }
    }

    private List<InstagramPostResponse> fetchPosts(Account account) {
        GraphMediaCollection collection = getGraphMedia(
                account,
                mediaUri(account.userId, "media", MEDIA_FIELDS, MEDIA_LIMIT, account.accessToken)
        );
        if (collection.data() == null) {
            throw instagramResponseError(account);
        }

        return collection.data().stream()
                .map(media -> toPostResponse(account, media))
                .toList();
    }

    private InstagramPostResponse toPostResponse(Account account, GraphMedia media) {
        if (media == null || media.id() == null || media.mediaType() == null) {
            throw instagramResponseError(account);
        }

        List<MediaItem> children = List.of();
        if ("CAROUSEL_ALBUM".equals(media.mediaType())) {
            GraphMediaCollection childCollection = getGraphMedia(
                    account,
                    mediaUri(media.id(), "children", CHILD_FIELDS, null, account.accessToken)
            );
            if (childCollection.data() == null) {
                throw instagramResponseError(account);
            }
            children = childCollection.data().stream()
                    .map(mediaItem -> toMediaItem(account, mediaItem))
                    .toList();
        }

        return new InstagramPostResponse(
                media.id(),
                media.caption(),
                media.mediaType(),
                media.mediaUrl(),
                media.thumbnailUrl(),
                media.permalink(),
                media.timestamp(),
                children
        );
    }

    private MediaItem toMediaItem(Account account, GraphMedia media) {
        if (media == null || media.id() == null || media.mediaType() == null) {
            throw instagramResponseError(account);
        }
        return new MediaItem(
                media.id(),
                media.mediaType(),
                media.mediaUrl(),
                media.thumbnailUrl(),
                media.timestamp()
        );
    }

    private GraphMediaCollection getGraphMedia(Account account, URI uri) {
        try {
            GraphMediaCollection response = restTemplate.getForObject(uri, GraphMediaCollection.class);
            if (response == null) {
                throw instagramResponseError(account);
            }
            return response;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Instagram is unavailable for " + account.profile.label,
                    exception
            );
        }
    }

    private URI mediaUri(String id, String endpoint, String fields, Integer limit, String accessToken) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .pathSegment(id, endpoint)
                .queryParam("fields", fields);
        if (limit != null) {
            builder.queryParam("limit", limit);
        }
        return builder.queryParam("access_token", accessToken).build().encode().toUri();
    }

    private void refreshToken(Account account) {
        if (!account.isConfigured()) {
            return;
        }

        URI uri = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .pathSegment("refresh_access_token")
                .queryParam("grant_type", "ig_refresh_token")
                .queryParam("access_token", account.accessToken)
                .build()
                .encode()
                .toUri();

        try {
            TokenRefreshResponse response = restTemplate.getForObject(uri, TokenRefreshResponse.class);
            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.expiresIn() == null
                    || response.expiresIn() <= 0) {
                log.error("Instagram token refresh returned an invalid response for profile={}", account.profile.label);
                return;
            }
            synchronized (account) {
                account.accessToken = response.accessToken();
                cache.remove(account.profile);
            }
            log.info(
                    "Instagram token refreshed for profile={} expiresInSeconds={}",
                    account.profile.label,
                    response.expiresIn()
            );
        } catch (RestClientResponseException exception) {
            log.error(
                    "Instagram token refresh failed for profile={} status={}",
                    account.profile.label,
                    exception.getStatusCode()
            );
        } catch (RestClientException exception) {
            log.error(
                    "Instagram token refresh failed for profile={} cause={}",
                    account.profile.label,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private ResponseStatusException instagramResponseError(Account account) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Instagram returned an invalid response for " + account.profile.label
        );
    }

    private enum Profile {
        MTB("desafionaturezamtb"),
        STORE("lojadesafionatureza");

        private final String label;

        Profile(String label) {
            this.label = label;
        }
    }

    private static final class Account {
        private final Profile profile;
        private final String userId;
        private volatile String accessToken;

        private Account(Profile profile, String userId, String accessToken) {
            this.profile = profile;
            this.userId = userId;
            this.accessToken = accessToken;
        }

        private boolean isConfigured() {
            return userId != null && !userId.isBlank()
                    && accessToken != null && !accessToken.isBlank();
        }
    }

    private record CachedPosts(List<InstagramPostResponse> posts, long expiresAtMillis) {}

    private record GraphMediaCollection(List<GraphMedia> data) {}

    private record GraphMedia(
            String id,
            String caption,
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("media_url") String mediaUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            String permalink,
            String timestamp
    ) {}

    private record TokenRefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn
    ) {}
}
