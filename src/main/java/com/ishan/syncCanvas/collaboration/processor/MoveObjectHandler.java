package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MoveObjectHandler
        extends AbstractOperationHandler<MoveObjectOperation> {

    public MoveObjectHandler(BoardSessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    public Class<MoveObjectOperation> supports() {
        return MoveObjectOperation.class;
    }

    @Override
    protected void execute(
            BoardSession session,
            MoveObjectOperation operation) {

        // TODO

        log.debug("MOVE_OBJECT executed");
    }
}