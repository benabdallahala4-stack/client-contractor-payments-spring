package com.example.contractpayments.contract.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ContractJpaRepository extends JpaRepository<ContractEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContractEntity c where c.id = :id")
    Optional<ContractEntity> findLocked(@Param("id") UUID id);
}
