package com.example.AndesStay.dto;

import java.util.List;

public record ListenerStatusResponse(String id, String label, List<String> queues, State state) {
    public enum State { RUNNING, STOPPING, STOPPED }
}
