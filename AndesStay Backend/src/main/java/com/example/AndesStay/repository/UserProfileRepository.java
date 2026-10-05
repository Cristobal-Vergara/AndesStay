package com.example.AndesStay.repository;

import com.example.AndesStay.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    boolean existsByOwnerKey(String ownerKey);
    Optional<UserProfile> findByOwnerKey(String ownerKey);
}
