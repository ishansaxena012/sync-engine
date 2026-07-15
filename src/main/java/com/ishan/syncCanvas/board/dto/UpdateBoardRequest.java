package com.ishan.syncCanvas.board.dto;

import com.ishan.syncCanvas.board.entity.Visibility;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBoardRequest {

    @Size(max = 100)
    private String name;

    private Visibility visibility;
}