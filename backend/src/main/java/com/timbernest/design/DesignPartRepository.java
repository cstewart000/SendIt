package com.timbernest.design;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DesignPartRepository extends JpaRepository<DesignPart, Long> {
    List<DesignPart> findByDesignVersionIdOrderByPartIndexAsc(Long designVersionId);

    @Modifying
    @Transactional
    void deleteByDesignVersionId(Long designVersionId);

    long countByDesignVersionId(Long designVersionId);
}
