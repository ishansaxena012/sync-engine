# SyncCanvas Test Client

This client allows developers to verify the complete WebSocket messaging pipeline during backend development.

>- Connect to the SyncCanvas WebSocket endpoint.
>- Establish a STOMP connection.
>- Subscribe to board topics.
>- Send test messages.
>- Receive broadcast messages.
>- Debug the collaboration pipeline.

---

> **Note**
>
> This client intentionally uses **native WebSockets** instead of SockJS.
> SyncCanvas targets modern browsers where native WebSockets are fully supported.

---