package com.ishan.syncCanvas.chat.repository;

import com.ishan.syncCanvas.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Every read here is bounded by a {@link Pageable} — a board's chat history is
 * unbounded in principle and is never loaded whole.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * One page of a board's history, newest first, so page 0 is the most recent
     * conversation rather than the oldest. {@code ChatService} reverses the page
     * contents for display.
     *
     * <p>Ordered by id as well as timestamp: two messages committed in the same
     * millisecond would otherwise have no stable relative order, and a page boundary
     * falling between them could repeat or skip one. Backed by
     * {@code idx_chat_message_board_created}.
     */
    Page<ChatMessage> findByBoardIdOrderByCreatedAtDescIdDesc(UUID boardId, Pageable pageable);

    /**
     * Explicit companion to the {@code ON DELETE CASCADE} foreign key, matching how
     * board deletion clears events, snapshots and undo history.
     */
    void deleteByBoardId(UUID boardId);
}
