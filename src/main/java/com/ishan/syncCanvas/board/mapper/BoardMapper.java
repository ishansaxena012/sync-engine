package com.ishan.syncCanvas.board.mapper;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.entity.Board;

public class BoardMapper {

    private BoardMapper() {}

    public static BoardResponse toResponse(Board board) {

        return BoardResponse.builder()
                .id(board.getId())
                .name(board.getName())
                .ownerId(board.getOwnerId())
                .visibility(board.getVisibility())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .build();
    }
}