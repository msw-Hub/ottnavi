package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityChangeRepository extends JpaRepository<AvailabilityChange, Long> {
}
