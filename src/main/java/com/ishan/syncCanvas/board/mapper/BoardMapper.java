package com.ishan.syncCanvas.board.mapper;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.entity.Board;

import com.ishan.syncCanvas.user.dto.UserProfileResponse;

public class BoardMapper {

    private BoardMapper() {}

    public static BoardResponse toResponse(Board board, UserProfileResponse owner) {

        return BoardResponse.builder()
                .id(board.getId())
                .title(board.getName())
                .owner(owner)
                .visibility(board.getVisibility())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .sequence(board.getSequence())
                .build();
    }
}