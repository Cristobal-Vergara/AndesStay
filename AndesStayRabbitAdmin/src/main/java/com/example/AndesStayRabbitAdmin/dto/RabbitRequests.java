package com.example.AndesStayRabbitAdmin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class RabbitRequests {
    private RabbitRequests() {}
    public static final String NAME = "[a-z0-9][a-z0-9._-]{0,127}";
    public static final String ROUTING_KEY = "[A-Za-z0-9._*#-]{0,128}";

    public record QueueCreate(@NotBlank @Pattern(regexp = NAME) String name,
                              @NotNull @Valid QueueSettings settings) {}
    public record QueueSettings(@NotNull @Min(1000) @Max(604800000) Integer messageTtlMs,
                                @NotNull @Min(1) @Max(100000) Integer maxLength,
                                @Pattern(regexp = NAME) String deadLetterExchange) {}
    public record ExchangeCreate(@NotBlank @Pattern(regexp = NAME) String name,
                                 @NotBlank @Pattern(regexp = "direct|fanout|topic") String type) {}
    public record ExchangeSettings(@Pattern(regexp = NAME) String alternateExchange) {}
    public record BindingCreate(@NotBlank @Pattern(regexp = NAME) String exchange,
                                @NotBlank @Pattern(regexp = NAME) String queue,
                                @NotNull @Pattern(regexp = ROUTING_KEY) String routingKey) {}
    public record BindingSettings(@NotNull @Pattern(regexp = ROUTING_KEY) String routingKey) {}
}
