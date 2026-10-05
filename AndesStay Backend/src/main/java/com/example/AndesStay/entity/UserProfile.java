package com.example.AndesStay.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "USER_PROFILES", uniqueConstraints = @UniqueConstraint(name = "uq_user_profile_owner", columnNames = "owner_key"))
public class UserProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_key", nullable = false, length = 255, updatable = false)
    private String ownerKey;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(length = 320)
    private String email;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserProfile() {}
    public UserProfile(String ownerKey, String displayName, String email, Instant createdAt) {
        this.ownerKey = ownerKey;
        this.displayName = displayName;
        this.email = email;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getOwnerKey() { return ownerKey; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
}
