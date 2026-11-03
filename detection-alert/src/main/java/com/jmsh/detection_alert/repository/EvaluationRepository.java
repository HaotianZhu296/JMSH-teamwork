package com.jmsh.detection_alert.repository;

import com.jmsh.detection_alert.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByMachineId(String machineId);
}
