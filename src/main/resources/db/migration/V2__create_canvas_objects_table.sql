CREATE TABLE canvas_objects
(
    id UUID PRIMARY KEY,

    board_id UUID NOT NULL,

    type VARCHAR(50) NOT NULL,

    x DOUBLE PRECISION NOT NULL,

    y DOUBLE PRECISION NOT NULL,

    rotation DOUBLE PRECISION NOT NULL DEFAULT 0,

    z_index INTEGER NOT NULL DEFAULT 0,

    payload JSONB NOT NULL,

    created_by UUID,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_canvas_board
ON canvas_objects(board_id);

CREATE INDEX idx_canvas_type
ON canvas_objects(type);

CREATE INDEX idx_canvas_payload
ON canvas_objects
USING GIN(payload);