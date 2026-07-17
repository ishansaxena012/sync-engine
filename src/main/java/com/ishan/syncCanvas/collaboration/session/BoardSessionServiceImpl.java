package com.ishan.syncCanvas.collaboration.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class BoardSessionServiceImpl implements BoardSessionService {
    private final BoardSessionManager sessionManager;

    private final CanvasObjectRepository canvasObjectRepository;

    @Override
    public Optional<BoardSession> findSession(UUID boardId) {
        return sessionManager.getSession(boardId);
    }

    @Override
    public void closeSession(UUID boardId) {
        sessionManager.remove(boardId);
        log.info("Closed board session {}", boardId);
    }

    @Override
    public BoardSession openSession(UUID boardId) {

        return sessionManager.getSession(boardId)
                .orElseGet(() -> {

                    log.info("Loading board session {}", boardId);

                    List<CanvasObject> canvasObjects = canvasObjectRepository.findByBoardId(boardId);

                    BoardSession session = new BoardSession(boardId);

                    session.initialize(canvasObjects);
                    session.activate();

                    return sessionManager.getOrCreate(boardId, () -> session);
                });
    }

}
