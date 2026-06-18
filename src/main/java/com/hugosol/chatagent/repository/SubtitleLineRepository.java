package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.SubtitleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface SubtitleLineRepository extends JpaRepository<SubtitleLine, String> {

    @Query("SELECT s FROM SubtitleLine s WHERE s.imdbId IN :imdbIds AND s.wordsLower LIKE :pattern ORDER BY s.imdbId, s.lineIndex")
    List<SubtitleLine> findByImdbIdInAndWordsLowerLike(@Param("imdbIds") Collection<String> imdbIds,
                                                        @Param("pattern") String pattern);

    @Query("SELECT s FROM SubtitleLine s WHERE s.imdbId = :imdbId AND s.lineIndex BETWEEN :start AND :end ORDER BY s.lineIndex")
    List<SubtitleLine> findByImdbIdAndLineIndexBetween(@Param("imdbId") String imdbId,
                                                        @Param("start") int start,
                                                        @Param("end") int end);

    @Modifying
    @Transactional
    void deleteByImdbId(String imdbId);

    int countByImdbId(String imdbId);
}
