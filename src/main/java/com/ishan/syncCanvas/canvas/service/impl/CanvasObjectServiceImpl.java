package com.ishan.syncCanvas.canvas.service.impl;

import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.canvas.mapper.CanvasObjectMapper;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import com.ishan.syncCanvas.canvas.service.CanvasObjectService;
import com.ishan.syncCanvas.common.exception.BoardNotFoundException;
import com.ishan.syncCanvas.common.exception.CanvasObjectNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CanvasObjectServiceImpl implements CanvasObjectService {

    private final CanvasObjectRepository canvasObjectRepository;
    private final BoardRepository boardRepository;
    private final CanvasObjectMapper canvasObjectMapper;

    @Override
    @Transactional
    public CanvasObjectResponse createObject(CreateCanvasObjectRequest request) {
        if (!boardRepository.existsById(request.getBoardId())) {
            throw new BoardNotFoundException("Board not found with id: " + request.getBoardId());
        }

        CanvasObject entity = canvasObjectMapper.toEntity(request);
        CanvasObject saved = canvasObjectRepository.save(entity);
        return canvasObjectMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanvasObjectResponse> getObjectsByBoard(UUID boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new BoardNotFoundException("Board not found with id: " + boardId);
        }

        return canvasObjectRepository.findByBoardIdOrderByZindexAsc(boardId)
                .stream()
                .map(canvasObjectMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteObject(UUID objectId) {
        if (!canvasObjectRepository.existsById(objectId)) {
            throw new CanvasObjectNotFoundException(objectId);
        }
        canvasObjectRepository.deleteById(objectId);
    }

    @Override
    @Transactional
    public CanvasObjectResponse moveObject(UUID objectId, double x, double y, int zindex) {
        CanvasObject entity = getObjectEntity(objectId);
        entity.setX(x);
        entity.setY(y);
        entity.setZindex(zindex);
        return canvasObjectMapper.toResponse(canvasObjectRepository.save(entity));
    }

    @Override
    @Transactional
    public CanvasObjectResponse rotateObject(UUID objectId, double rotation) {
        CanvasObject entity = getObjectEntity(objectId);
        entity.setRotation(rotation);
        return canvasObjectMapper.toResponse(canvasObjectRepository.save(entity));
    }

    @Override
    @Transactional
    public CanvasObjectResponse changePayload(UUID objectId, CanvasPayload newPayload) {
        CanvasObject entity = getObjectEntity(objectId);
        entity.setPayload(newPayload);
        return canvasObjectMapper.toResponse(canvasObjectRepository.save(entity));
    }

    private CanvasObject getObjectEntity(UUID objectId) {
        return canvasObjectRepository.findById(objectId)
                .orElseThrow(() -> new CanvasObjectNotFoundException(objectId));
    }
}
