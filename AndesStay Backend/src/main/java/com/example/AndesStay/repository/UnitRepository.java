package com.example.AndesStay.repository;

import com.example.AndesStay.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface UnitRepository extends JpaRepository<Unit, Long> {

}