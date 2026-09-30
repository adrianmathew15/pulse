package com.pulse.repository;

import com.pulse.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<MonitoredService, UUID> {
    boolean existsByNameIgnoreCase(String name);
    List<MonitoredService> findAllByOrderByCreatedAtDescIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select service from MonitoredService service where service.id = :id")
    Optional<MonitoredService> findByIdForUpdate(@Param("id") UUID id);
}
