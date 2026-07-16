package com.ishan.syncCanvas.canvas.entity;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import com.ishan.syncCanvas.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "canvas_objects", indexes = {
        @Index(name = "idx_canvas_board", columnList = "board_id"),
        @Index(name = "idx_canvas_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CanvasObject extends BaseEntity {

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanvasObjectType type;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double rotation;

    @Column(name = "z_index", nullable = false)
    private int zindex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private CanvasPayload payload;

    @Column(name = "created_by", updatable = false, nullable = true)
    private UUID createdBy;

    @Version
    private Long version;
}