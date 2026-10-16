package com.example.AndesStay.service;

public class InvalidNotificationEventException extends IllegalArgumentException {
    public InvalidNotificationEventException(String message) {
        super(message);
    }

    public InvalidNotificationEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
