package com.ishan.syncCanvas.video.dto;

import com.ishan.syncCanvas.video.model.VideoParticipant;

import java.util.List;

/**
 * Private reply to START/JOIN on {@code /user/queue/boards/{boardId}/video/roster} —
 * the full current roster, delivered once per join call. A repeat join from a second
 * tab gets this too even though nothing changed for anyone else (no public broadcast),
 * since without it that tab has no way to learn who is already in the call.
 */
public record VideoRosterResponse(List<VideoParticipant> participants) {
}
