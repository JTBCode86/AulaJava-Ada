package com.melicore.engine.repository;

import com.melicore.engine.domain.OrderUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderUuidRepository extends JpaRepository<OrderUuid, UUID> {
    // Repositório Spring Data JPA padrão para OrderUuid
}