package com.melicore.engine.repository;

import com.melicore.engine.domain.OrderSeq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSeqRepository extends JpaRepository<OrderSeq, Long> {
    // Repositório Spring Data JPA padrão para OrderSeq
}