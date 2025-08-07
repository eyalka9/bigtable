package com.poc.bigtable.repository;

import com.poc.bigtable.model.TableRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TableRowRepository extends JpaRepository<TableRow, String>, 
                                           JpaSpecificationExecutor<TableRow> {
    
    @Query("SELECT tr FROM TableRow tr WHERE tr.sessionId = :sessionId")
    Page<TableRow> findBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
    
    @Query("SELECT COUNT(tr) FROM TableRow tr WHERE tr.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);
    
    void deleteBySessionId(String sessionId);
}