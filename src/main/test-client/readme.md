# SyncCanvas Test Client

This client allows developers to verify the complete WebSocket messaging pipeline during backend development.

>- Connect to the SyncCanvas WebSocket endpoint.
>- Establish a STOMP connection.
>- Subscribe to board topics.
>- Send test messages.
>- Receive broadcast messages.
>- Debug the collaboration pipeline.

---
src\main\java\com\ishan\syncCanvas\websocket\controller\CollaborationController.java

Test Code: (replace the code to test the WebSocket connection)

>    @MessageMapping("/boards/{boardId}/operations")
>    public void test(
>            @DestinationVariable UUID boardId,
>            String message) {
>
>        System.out.println("Received: " + message);
>
>        messagingTemplate.convertAndSend(
>                "/topic/boards/" + boardId,
>                "Echo: " + message);
>    }
---

> **Note**
>
> This client intentionally uses **native WebSockets** instead of SockJS.
> SyncCanvas targets modern browsers where native WebSockets are fully supported.

---