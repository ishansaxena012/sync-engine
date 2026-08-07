package com.ishan.syncCanvas.board.repository;

import com.ishan.syncCanvas.board.entity.Board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    Page<Board> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Board> findByOwnerIdAndNameContainingIgnoreCase(UUID ownerId, String name, Pageable pageable);

    Page<Board> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Board b WHERE b.ownerId = :userId OR b.visibility = 'PUBLIC'")
    Page<Board> findAccessibleBoards(@org.springframework.data.repository.query.Param("userId") UUID userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Board b WHERE (b.ownerId = :userId OR b.visibility = 'PUBLIC') AND LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Board> findAccessibleBoardsByName(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("name") String name, Pageable pageable);

}