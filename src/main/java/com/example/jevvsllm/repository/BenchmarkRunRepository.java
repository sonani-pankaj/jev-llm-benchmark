package com.example.jevvsllm.repository;

import com.example.jevvsllm.persistence.BenchmarkRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRunEntity, Long> {

    Optional<BenchmarkRunEntity> findFirstByOrderByIdDesc();
}
