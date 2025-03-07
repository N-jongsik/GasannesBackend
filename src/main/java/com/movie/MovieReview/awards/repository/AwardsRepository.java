package com.movie.MovieReview.awards.repository;

import com.movie.MovieReview.awards.entity.AwardsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AwardsRepository extends JpaRepository<AwardsEntity, Long> {
    List<AwardsEntity> findByStatus(int status); //for past

//    Optional<AwardsEntity> findByStatusCurrent(int status); //for current

    List<AwardsEntity> findByStatusOrderByAwardsIdDesc(int status);

    List<AwardsEntity> findByTopMovieId(@Param("topMovieId") Long topMovieId);
}
