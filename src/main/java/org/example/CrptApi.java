package com.example; // Опционально, можно убрать для одного файла

import com.fasterxml.jackson.databind.ObjectMapper; // Для сериализации объектов в JSON
import java.io.IOException; // Для обработки ошибок ввода-вывода
import java.net.URI; // Для работы с URL
import java.net.http.HttpClient; // Стандартный HTTP-клиент Java 11
import java.net.http.HttpRequest; // Для создания HTTP-запросов
import java.net.http.HttpResponse; // Для обработки HTTP-ответов
import java.nio.charset.StandardCharsets; // Для кодировки UTF-8
import java.time.Duration; // Для задания таймаутов
import java.util.Base64; // Для кодирования авторизации в Base64
import java.util.HashMap; // Для создания JSON-объекта запроса
import java.util.Map; // Для работы с парами ключ-значение
import java.util.concurrent.TimeUnit; // Для указания единиц времени
import java.util.concurrent.locks.ReentrantLock; // Для thread-safe блокировки
import java.util.concurrent.locks.Lock; // Интерфейс блокировки
import java.util.concurrent.locks.Condition; // Для ожидания в RateLimiter
import java.util.Deque; // Для двусторонней очереди временных меток
import java.util.ArrayDeque; // Реализация Deque

public class CrptApi {
    // Базовый URL API Честного знака
    private static final String BASE_URL = "https://ismp.crpt.ru/api/v3/true";
    // Эндпоинт для создания документа
    private static final String ENDPOINT = "/doc/create";

    // HTTP-клиент для отправки запросов
    private final HttpClient httpClient;
    // Для сериализации объектов в JSON
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Для ограничения количества запросов
    private final RateLimiter rateLimiter;
    // Заголовок авторизации (Basic Auth)
    private final String authHeader;

    // Конструктор: инициализирует клиента, лимитер и авторизацию
    public CrptApi(String participantId, String apiKey, TimeUnit timeUnit, int requestLimit) {
        // Создаем HTTP-клиент с таймаутом 10 секунд
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        // Инициализируем RateLimiter с указанным интервалом и лимитом
        this.rateLimiter = new RateLimiter(timeUnit, requestLimit);
        // Формируем Basic Auth: кодируем participantId:apiKey в Base64
        String credentials = participantId + ":" + apiKey;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + encoded;
    }

    // Метод для создания документа в API
    public String createDocument(Object document, String signature) throws IOException, InterruptedException {
        // Ждем, пока не будет доступен слот для запроса (RateLimiter)
        rateLimiter.acquire();
        // Сериализуем документ в JSON
        String docJson = objectMapper.writeValueAsString(document);
        // Создаем тело запроса: документ и подпись
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("document", docJson);
        bodyMap.put("signature", signature);
        String bodyJson = objectMapper.writeValueAsString(bodyMap);

        // Формируем HTTP POST-запрос
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + ENDPOINT)) // Указываем полный URL
                .header("Content-Type", "application/json") // Указываем тип содержимого
                .header("Authorization", authHeader) // Добавляем заголовок авторизации
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8)) // Тело запроса
                .build();

        // Отправляем запрос и получаем ответ
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Проверяем статус ответа
        if (response.statusCode() != 200) {
            throw new IOException("API error: " + response.statusCode() + " - " + response.body());
        }
        // Возвращаем тело ответа
        return response.body();
    }

    // Внутренний класс для ограничения количества запросов
    private static class RateLimiter {
        // Размер временного окна в миллисекундах
        private final long windowSizeMillis;
        // Максимальное количество запросов в окне
        private final int limit;
        // Очередь для хранения временных меток запросов
        private final Deque<Long> timestamps = new ArrayDeque<>();
        // Блокировка для thread-safe доступа
        private final Lock lock = new ReentrantLock();
        // Условие для ожидания освобождения слота
        private final Condition condition = lock.newCondition();

        // Конструктор: преобразует TimeUnit в миллисекунды и задает лимит
        public RateLimiter(TimeUnit timeUnit, int requestLimit) {
            this.windowSizeMillis = timeUnit.toMillis(1);
            this.limit = requestLimit;
        }

        // Метод для получения разрешения на запрос
        public void acquire() throws InterruptedException {
            lock.lock(); // Захватываем блокировку
            try {
                while (true) {
                    long now = System.currentTimeMillis(); // Текущее время
                    // Удаляем метки, вышедшие за пределы временного окна
                    while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - windowSizeMillis) {
                        timestamps.pollFirst();
                    }
                    // Если есть свободный слот, добавляем метку и выходим
                    if (timestamps.size() < limit) {
                        timestamps.addLast(now);
                        return;
                    }
                    // Если лимит достигнут, ждем, пока освободится слот
                    long waitTime = timestamps.peekFirst() + windowSizeMillis - now;
                    if (waitTime > 0) {
                        condition.await(waitTime, TimeUnit.MILLISECONDS);
                    }
                }
            } finally {
                lock.unlock(); // Освобождаем блокировку
            }
        }
    }
}