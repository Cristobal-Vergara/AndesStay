package com.example.AndesStay.controller;

import com.example.AndesStay.dto.MessagingStatusResponse;
import com.example.AndesStay.service.MessagingStatusService;
import com.example.AndesStay.service.MessagingDemoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/messaging", "/api/messaging"})
public class MessagingController {
    private final MessagingStatusService status;
    private final ObjectProvider<MessagingDemoService> demo;
    public MessagingController(MessagingStatusService status, ObjectProvider<MessagingDemoService> demo) {
        this.status=status; this.demo=demo;
    }
    @GetMapping("/status")
    public MessagingStatusResponse status() { return status.status(); }

    @PostMapping("/demo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> demo() {
        var service=demo.getIfAvailable();
        if(service==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La prueba local no está habilitada");
        try { return Map.of("eventId",service.publish()); }
        catch(InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo iniciar la prueba");
        } catch(Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo iniciar la prueba");
        }
    }
}
