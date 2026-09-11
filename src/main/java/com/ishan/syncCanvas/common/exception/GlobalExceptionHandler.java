package com.ishan.syncCanvas.common.exception;


import com.ishan.syncCanvas.chat.exception.InvalidChatMessageException;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.common.response.ErrorResponse;
import com.ishan.syncCanvas.common.response.ValidationError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex
    ){
        List<ValidationError> errors=ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error->new ValidationError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .message("Validation failed")
                .errors(errors)
                .build();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBoardNotFound(
            BoardNotFoundException ex
    ){
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errors(List.of())
                .build();
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    /**
     * BoardAccessGuard is the shared authorization check for every board-scoped
     * channel; the chat history endpoint is the first to reach an HTTP caller through
     * it, so it needs translating to a 403 rather than falling through as a 500.
     *
     * <p>Deliberately does not distinguish "board does not exist" from "you may not see
     * it" — the guard throws the same exception for both precisely so a caller cannot
     * probe which private boards exist.
     */
    @ExceptionHandler(BoardAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleBoardAccessDenied(
            BoardAccessDeniedException ex
    ){
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errors(List.of())
                .build();
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(InvalidChatMessageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidChatMessage(
            InvalidChatMessageException ex
    ){
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errors(List.of())
                .build();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}
