package com.ada.pkdemo.repository;

import com.ada.pkdemo.entity.OrderSequential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSequentialRepository extends JpaRepository<OrderSequential, Long> {
}