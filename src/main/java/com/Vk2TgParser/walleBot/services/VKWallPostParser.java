package com.Vk2TgParser.walleBot.services;

import com.Vk2TgParser.walleBot.Configurations.PostData;
import com.Vk2TgParser.walleBot.Configurations.VideoObject;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallItem;
import com.vk.api.sdk.queries.wall.WallGetQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class VKWallPostParser {

    private final VkApiClient vk;
    private final UserActor actor;

    @Value("${vk.domain}")
    private String domain;

    @Value("${request.filter}")
    private String filter;

    @Value("${request.count}")
    private int postCount;

    public VKWallPostParser(@Value("${vk.adminID}") Long adminID,
                            @Value("${vk.accessToken}") String accessToken) {

        this.vk = new VkApiClient(HttpTransportClient.getInstance());
        this.actor = new UserActor(adminID, accessToken);
    }

    // Метод для получения ID самого свежего поста
    public Long getLatestPostId() {
        log.info("Запрашиваем последний пост с домена: {} с фильтром: {}", domain, filter);
        try {
            List<WallItem> posts = vk.wall()
                    .get(actor)
                    .domain(domain)
                    .count(postCount)
                    .filter(GetFilter.valueOf(filter))
                    .execute()
                    .getItems();

            if (!posts.isEmpty() && posts.size() > 1) {
                log.info("Получен последний пост с ID: {}", (posts.get(0).isPinned()) ? (long) posts.get(1).getId() : (long) posts.get(0).getId());
                return (posts.get(0).isPinned()) ? (long) posts.get(1).getId() : (long) posts.get(0).getId();
            }
            log.warn("Не найдено ни одного поста.");
        } catch (IllegalArgumentException e) {
            log.error("Некорректный фильтр для запроса к VK API: {}", filter, e);
        } catch (ApiException e) {
            log.error("Ошибка при запросе к VK API: {}", e.getMessage(), e);
        } catch (ClientException e) {
            log.error("Ошибка при выполнении запроса: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Произошла непредвиденная ошибка: {}", e.getMessage(), e);
        }
        return null;
    }

    // Метод для парсинга постов с определённого offset
    public List<PostData> parsePostsSince(long lastPostID, int count, int offset) {
        log.info("Запрашиваем посты начиная с ID: {} с количеством: {} и смещением: {}", lastPostID, count, offset);
        try {
            List<PostData> parsedPosts = new ArrayList<>();
            WallGetQuery query = vk.wall()
                    .get(actor)
                    .domain(domain)
                    .count(count)
                    .offset(offset)
                    .filter(GetFilter.valueOf(filter));
            List<WallItem> posts = query.execute().getItems();

            for (WallItem post : posts) {
                if (post.getId() > lastPostID) {
                    parsedPosts.add(processPost(post));
                }
            }
            log.info("Всего обработано {} постов", parsedPosts.size());
            return parsedPosts;
        } catch (IllegalArgumentException e) {
            log.error("Некорректный фильтр для запроса к VK API: {}", filter, e);
        } catch (ApiException e) {
            log.error("Ошибка при запросе к VK API: {}", e.getMessage(), e);
        } catch (ClientException e) {
            log.error("Ошибка при выполнении запроса: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Ошибка при запросе к VK API", e);
        }
        return new ArrayList<>();
    }

    // Метод для обработки поста и извлечения данных
    private PostData processPost(WallItem post) {
        log.info("Обрабатываем пост с ID: {}", post.getId());

        String postText = post.getText();
        List<String> postPhotoUrls = PostData.extractPhotoUrls(post); // Извлечение ссылок на фото
        List<VideoObject> postVideoObjects = PostData.extractVideoObjects(post, vk, actor); // Извлечение ссылок на видео
        Integer postID = post.getId();

        return new PostData(postText, postPhotoUrls, postVideoObjects, postID);
    }
}
