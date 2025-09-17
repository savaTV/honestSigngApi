package org.example;

import org.example.CrptApi;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        // Создаем экземпляр CrptApi
        CrptApi api = new CrptApi("testParticipantId", "testApiKey", TimeUnit.SECONDS, 2);

        // Создаем тестовый документ
        CrptApi.Document document = new CrptApi.Document();
        CrptApi.Description description = new CrptApi.Description();
        description.setParticipantInn("1234567890");
        document.setDescription(description);
        document.setDoc_id("doc123");
        document.setDoc_status("NEW");
        document.setDoc_type("LP_INTRODUCE_GOODS");
        document.setImportRequest(false);
        document.setOwner_inn("0987654321");
        document.setParticipant_inn("1234567890");
        document.setProducer_inn("1122334455");
        document.setProduction_date("2025-01-01");
        document.setProduction_type("OWN_PRODUCTION");
        CrptApi.Product product = new CrptApi.Product();
        product.setCertificate_document("CERT123");
        product.setCertificate_document_date("2025-01-01");
        product.setCertificate_document_number("CERTNUM123");
        product.setOwner_inn("0987654321");
        product.setProducer_inn("1122334455");
        product.setProduction_date("2025-01-01");
        product.setTnved_code("123456");
        product.setUit_code("UIT123456");
        document.setProducts(Arrays.asList(product));
        document.setReg_date("2025-01-01");
        document.setReg_number("REG123");

        // Подпись
        String signature = "testSignature";

        try {
            // Вызываем API
            String response = api.createDocument(document, signature);
            System.out.println("Ответ API: " + response);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }
}