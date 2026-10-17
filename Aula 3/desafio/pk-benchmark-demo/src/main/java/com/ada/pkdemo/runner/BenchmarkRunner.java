package com.ada.pkdemo.runner;

import com.ada.pkdemo.entity.OrderSequential;
import com.ada.pkdemo.entity.OrderUuid;
import com.ada.pkdemo.repository.OrderSequentialRepository;
import com.ada.pkdemo.repository.OrderUuidRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class BenchmarkRunner implements CommandLineRunner {

    private final OrderSequentialRepository seqRepository;
    private final OrderUuidRepository uuidRepository;

    public BenchmarkRunner(OrderSequentialRepository seqRepository, OrderUuidRepository uuidRepository) {
        this.seqRepository = seqRepository;
        this.uuidRepository = uuidRepository;
    }

    @Override
    public void run(String... args) {
        int totalRecords = 50000;
        System.out.println("==================================================");
        System.out.println("🚀 INICIANDO BENCHMARK DE INSERÇÃO: " + totalRecords + " REGISTROS");
        System.out.println("==================================================");

        // 1. Inserção Sequencial (BIGINT)
        List<OrderSequential> seqList = new ArrayList<>();
        for (int i = 0; i < totalRecords; i++) {
            seqList.add(new OrderSequential("Customer " + i, BigDecimal.valueOf(100.50)));
        }

        long startTimeSeq = System.currentTimeMillis();
        seqRepository.saveAll(seqList);
        long endTimeSeq = System.currentTimeMillis();
        System.out.println("✅ Inserção Sequencial (BIGINT): " + (endTimeSeq - startTimeSeq) + " ms");

        // 2. Inserção UUIDv4
        List<OrderUuid> uuidList = new ArrayList<>();
        for (int i = 0; i < totalRecords; i++) {
            uuidList.add(new OrderUuid("Customer " + i, BigDecimal.valueOf(100.50)));
        }

        long startTimeUuid = System.currentTimeMillis();
        uuidRepository.saveAll(uuidList);
        long endTimeUuid = System.currentTimeMillis();
        System.out.println("✅ Inserção UUIDv4: " + (endTimeUuid - startTimeUuid) + " ms");
        System.out.println("==================================================");
    }
}