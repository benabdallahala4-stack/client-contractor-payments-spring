package com.example.contractpayments.profile.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProfileJpaRepository extends JpaRepository<ProfileEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProfileEntity p where p.id = :id")
    Optional<ProfileEntity> findLocked(@Param("id") UUID id);
}
