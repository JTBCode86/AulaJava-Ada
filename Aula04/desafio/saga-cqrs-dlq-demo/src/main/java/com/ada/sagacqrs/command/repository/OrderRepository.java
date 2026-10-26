package com.ada.sagacqrs.command.repository;

import com.ada.sagacqrs.command.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {}