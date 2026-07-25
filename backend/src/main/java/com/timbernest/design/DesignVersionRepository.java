package com.timbernest.design;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DesignVersionRepository extends JpaRepository<DesignVersion, Long> {
    List<DesignVersion> findByDesignIdOrderByVersionNumberDesc(Long designId);
    Optional<DesignVersion> findByIdAndDesignId(Long id, Long designId);
    Optional<DesignVersion> findTopByDesignIdOrderByVersionNumberDesc(Long designId);
}
