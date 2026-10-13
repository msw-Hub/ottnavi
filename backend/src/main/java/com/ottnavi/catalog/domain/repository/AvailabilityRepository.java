package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
}
