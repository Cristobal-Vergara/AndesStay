package com.example.andesstay.AndesStayBFF.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BffReservationService {

    private final RestClient restClient;

    public BffReservationService(
            @Value("${backend.url:http://localhost:8080}") String backendUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(backendUrl)
                .build();
    }

    public String getReservations(String token) {

        return restClient
                .get()
                .uri("/api/reservations")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
    }

    public String createReservation(
            String token,
            String body) {

        try {

            return restClient
                    .post()
                    .uri("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .body(String.class);

        } catch (org.springframework.web.client.HttpClientErrorException e) {

            System.out.println("BACKEND STATUS: " + e.getStatusCode());
            System.out.println("BACKEND BODY: " + e.getResponseBodyAsString());

            throw e;
        }
    }

    public String updateReservationStatus(
            String token,
            Long reservationId,
            String body) {

        return restClient
                .put()
                .uri("/api/reservations/" + reservationId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String getCatalog(String token) {

        return restClient
                .get()
                .uri("/api/catalog/units")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
    }

    public String getReports(String token) {

        return restClient
                .get()
                .uri("/api/reports/kpis")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
    }

    public String getAudit(String token) {

        return restClient
                .get()
                .uri("/api/audit")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
    }
}