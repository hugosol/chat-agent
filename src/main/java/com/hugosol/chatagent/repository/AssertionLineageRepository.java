package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.AssertionLineage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssertionLineageRepository extends JpaRepository<AssertionLineage, AssertionLineage.LineageId> {

    @Query(value = """
        WITH RECURSIVE lineage(parent_id) AS (
            SELECT parent_id FROM assertion_lineage WHERE child_id = :childId
            UNION ALL
            SELECT al.parent_id FROM assertion_lineage al
            INNER JOIN lineage l ON al.child_id = l.parent_id
        )
        SELECT parent_id FROM lineage
        """, nativeQuery = true)
    List<String> findAncestorIds(@Param("childId") String childId);
}
