package com.ishan.syncCanvas.board.dto;

// import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBoardRequest {

    @NotBlank(message = "Board name cannot be empty")
    @Size(max = 100, message = "Board name cannot exceed 100 characters")
    private String name;
}
