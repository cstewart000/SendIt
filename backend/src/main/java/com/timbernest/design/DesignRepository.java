package com.timbernest.design;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DesignRepository extends JpaRepository<Design, Long> {
    List<Design> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
}
