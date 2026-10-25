package com.melicore.engine.repository;

import com.melicore.engine.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    
    // Método utilizado pelo OutboxPoller para buscar eventos pendentes
    List<OutboxEvent> findByProcessedFalse();
}