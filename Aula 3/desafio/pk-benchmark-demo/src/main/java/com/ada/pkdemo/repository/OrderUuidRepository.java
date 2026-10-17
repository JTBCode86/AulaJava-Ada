package com.ada.pkdemo.repository;

import com.ada.pkdemo.entity.OrderUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderUuidRepository extends JpaRepository<OrderUuid, UUID> {
}