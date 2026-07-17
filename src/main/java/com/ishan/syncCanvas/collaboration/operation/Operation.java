package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateObjectOperation.class, name = "CREATE_OBJECT"),
        @JsonSubTypes.Type(value = MoveObjectOperation.class, name = "MOVE_OBJECT"),
        @JsonSubTypes.Type(value = DeleteObjectOperation.class, name = "DELETE_OBJECT"),
        @JsonSubTypes.Type(value = ChangePayloadOperation.class, name = "CHANGE_PAYLOAD"),
        @JsonSubTypes.Type(value = RotateObjectOperation.class, name = "ROTATE_OBJECT")
})
public sealed interface Operation
        permits
        CreateObjectOperation,
        MoveObjectOperation,
        DeleteObjectOperation,
        ChangePayloadOperation,
        RotateObjectOperation {

    UUID boardId();

    UUID userId();

    Instant timestamp();

    OperationType type();

}