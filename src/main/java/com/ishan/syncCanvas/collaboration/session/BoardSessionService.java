package com.ishan.syncCanvas.collaboration.session;

import java.util.Optional;
import java.util.UUID;

public interface BoardSessionService {

    BoardSession openSession(UUID boardId);

    Optional<BoardSession> findSession(UUID boardId);

    void closeSession(UUID boardId);

}