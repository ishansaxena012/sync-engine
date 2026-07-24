package com.ishan.syncCanvas.collaboration.persistence;

import com.ishan.syncCanvas.collaboration.session.BoardSession;

public interface BoardPersistenceService {

    PersistenceResult persist(BoardSession session);

}