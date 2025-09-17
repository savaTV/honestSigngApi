package org.example;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;



public class CrptApi {
    // Базовый URL API Честного знака
    private static final String BASE_URL = "http://<server-name>[:server-port]" +
            "/api/v2/{extension}/ rollout?omsId={omsId}";
    // Эндпоинт для создания документа
    private static final String ENDPOINT = "/documents/create";

    // HTTP-клиент для отправки запросов
    private final HttpClient httpClient;
    // Для сериализации объектов в JSON
    private final Gson gson = new Gson();
    // Для ограничения количества запросов
    private final RateLimiter rateLimiter;
    // Заголовок авторизации (Basic Auth)
    private final String authHeader;


    public CrptApi(String participantId, String apiKey, TimeUnit timeUnit, int requestLimit) {
        // Создаем HTTP-клиент с таймаутом 10 секунд
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.rateLimiter = new RateLimiter(timeUnit, requestLimit);
        // Формируем Basic Auth: кодируем participantId:apiKey в Base64
        String credentials = participantId + ":" + apiKey;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + encoded;
    }


    public String createDocument(Object document, String signature) throws IOException, InterruptedException {
        // Ждем доступный слот для запроса
        rateLimiter.acquire();
        // Сериализуем документ в JSON
        String docJson = gson.toJson(document);
        // Формируем тело запроса: документ и подпись
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("document", docJson);
        bodyMap.put("signature", signature);
        String bodyJson = gson.toJson(bodyMap);

        // Создаем HTTP POST-запрос
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + ENDPOINT))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();

        // Отправляем запрос и получаем ответ
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Проверяем статус ответа
        if (response.statusCode() != 200) {
            throw new IOException("API error: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }

    /**
     * Внутренний класс для ограничения количества запросов.
     */
    private static class RateLimiter {
        // Размер временного окна в миллисекундах
        private final long windowSizeMillis;
        // Максимальное количество запросов в окне
        private final int limit;
        // Очередь временных меток запросов
        private final Deque<Long> timestamps = new ArrayDeque<>();
        // Блокировка для thread-safe доступа
        private final Lock lock = new ReentrantLock();
        // Условие для ожидания освобождения слота
        private final Condition condition = lock.newCondition();


        public RateLimiter(TimeUnit timeUnit, int requestLimit) {
            this.windowSizeMillis = timeUnit.toMillis(1);
            this.limit = requestLimit;
        }


        public void acquire() throws InterruptedException {
            lock.lock();
            try {
                while (true) {
                    long now = System.currentTimeMillis();
                    // Удаляем устаревшие метки
                    while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - windowSizeMillis) {
                        timestamps.pollFirst();
                    }
                    // Если есть свободный слот, добавляем метку и выходим
                    if (timestamps.size() < limit) {
                        timestamps.addLast(now);
                        return;
                    }
                    // Ждем, пока освободится слот
                    long waitTime = timestamps.peekFirst() + windowSizeMillis - now;
                    if (waitTime > 0) {
                        condition.await(waitTime, TimeUnit.MILLISECONDS);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public static class Document {

        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        // Геттеры и сеттеры
        public Description getDescription() { return description; }
        public void setDescription(Description description) { this.description = description; }
        public String getDoc_id() { return doc_id; }
        public void setDoc_id(String doc_id) { this.doc_id = doc_id; }
        public String getDoc_status() { return doc_status; }
        public void setDoc_status(String doc_status) { this.doc_status = doc_status; }
        public String getDoc_type() { return doc_type; }
        public void setDoc_type(String doc_type) { this.doc_type = doc_type; }
        public boolean isImportRequest() { return importRequest; }
        public void setImportRequest(boolean importRequest) { this.importRequest = importRequest; }
        public String getOwner_inn() { return owner_inn; }
        public void setOwner_inn(String owner_inn) { this.owner_inn = owner_inn; }
        public String getParticipant_inn() { return participant_inn; }
        public void setParticipant_inn(String participant_inn) { this.participant_inn = participant_inn; }
        public String getProducer_inn() { return producer_inn; }
        public void setProducer_inn(String producer_inn) { this.producer_inn = producer_inn; }
        public String getProduction_date() { return production_date; }
        public void setProduction_date(String production_date) { this.production_date = production_date; }
        public String getProduction_type() { return production_type; }
        public void setProduction_type(String production_type) { this.production_type = production_type; }
        public List<Product> getProducts() { return products; }
        public void setProducts(List<Product> products) { this.products = products; }
        public String getReg_date() { return reg_date; }
        public void setReg_date(String reg_date) { this.reg_date = reg_date; }
        public String getReg_number() { return reg_number; }
        public void setReg_number(String reg_number) { this.reg_number = reg_number; }
    }

    public static class Description {
        private String participantInn;
        public String getParticipantInn() { return participantInn; }
        public void setParticipantInn(String participantInn) { this.participantInn = participantInn; }
    }

    public static class Product {

        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public String getCertificate_document() { return certificate_document; }
        public void setCertificate_document(String certificate_document) { this.certificate_document = certificate_document; }
        public String getCertificate_document_date() { return certificate_document_date; }
        public void setCertificate_document_date(String certificate_document_date) { this.certificate_document_date = certificate_document_date; }
        public String getCertificate_document_number() { return certificate_document_number; }
        public void setCertificate_document_number(String certificate_document_number) { this.certificate_document_number = certificate_document_number; }
        public String getOwner_inn() { return owner_inn; }
        public void setOwner_inn(String owner_inn) { this.owner_inn = owner_inn; }
        public String getProducer_inn() { return producer_inn; }
        public void setProducer_inn(String producer_inn) { this.producer_inn = producer_inn; }
        public String getProduction_date() { return production_date; }
        public void setProduction_date(String production_date) { this.production_date = production_date; }
        public String getTnved_code() { return tnved_code; }
        public void setTnved_code(String tnved_code) { this.tnved_code = tnved_code; }
        public String getUit_code() { return uit_code; }
        public void setUit_code(String uit_code) { this.uit_code = uit_code; }
        public String getUitu_code() { return uitu_code; }
        public void setUitu_code(String uitu_code) { this.uitu_code = uitu_code; }
    }
}
