package com.ishan.syncCanvas.board.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.ishan.syncCanvas.common.entity.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "boards")
public class Board extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    /**
     * Authoritative per-board operation sequence. Incremented only inside the same
     * PostgreSQL transaction that inserts the corresponding {@code board_event}, under a
     * row lock, so one committed operation is always exactly one durable event with
     * exactly one sequence — and a rolled-back transaction never leaves a gap.
     */
    @Column(nullable = false)
    private long sequence;

}