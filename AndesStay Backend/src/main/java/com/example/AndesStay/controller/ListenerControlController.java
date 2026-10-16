package com.example.AndesStay.controller;

import com.example.AndesStay.dto.ListenerStatusResponse;
import com.example.AndesStay.dto.ListenerStatusResponse.State;
import com.example.AndesStay.service.ListenerControlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/messaging/listeners", "/api/messaging/listeners"})
public class ListenerControlController {
    private final ListenerControlService service;

    public ListenerControlController(ListenerControlService service) { this.service = service; }

    @GetMapping
    public List<ListenerStatusResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public ListenerStatusResponse status(@PathVariable String id) { return service.status(id); }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ListenerStatusResponse> pause(@PathVariable String id) {
        ListenerStatusResponse result = service.pause(id);
        return result.state() == State.STOPPING ? ResponseEntity.accepted().body(result) : ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/resume")
    public ListenerStatusResponse resume(@PathVariable String id) { return service.resume(id); }
}
