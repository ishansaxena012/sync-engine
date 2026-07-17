package com.ishan.syncCanvas.canvas.repository;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CanvasObjectRepository extends JpaRepository<CanvasObject, UUID> {

    List<CanvasObject> findByBoardId(UUID boardId);

    List<CanvasObject> findByBoardIdOrderByZindexAsc(UUID boardId);

    void deleteByBoardId(UUID boardId);
}
