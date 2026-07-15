package com.ishan.syncCanvas.board.repository;

import com.ishan.syncCanvas.board.entity.Board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    Page<Board> findByNameContainingIgnoreCase(String name, Pageable pageable);

}