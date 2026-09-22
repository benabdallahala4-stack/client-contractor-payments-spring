package com.example.contractpayments.job.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface JobJpaRepository extends JpaRepository<JobEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobEntity j where j.id = :id")
    Optional<JobEntity> findLocked(@Param("id") UUID id);
}
