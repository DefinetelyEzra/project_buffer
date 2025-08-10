import { Server, Socket } from "socket.io";
import { AttendanceService } from "../services/attendance.service";
import jwt, { JsonWebTokenError, TokenExpiredError } from "jsonwebtoken";

interface AuthenticatedSocket extends Socket {
  userId?: string;
}

export function setupAttendanceHandlers(io: Server) {
  // Middleware to authenticate socket connections
  io.use(async (socket: AuthenticatedSocket, next) => {
    try {
      const token =
        socket.handshake.auth.token ||
        socket.handshake.headers.authorization?.split(" ")[1];

      if (!token) {
        return next(new Error("Authentication required"));
      }

      const decoded = jwt.verify(token, process.env.JWT_SECRET!) as {
        userId: string;
      };
      socket.userId = decoded.userId;
      next();
    } catch (error) {
      if (error instanceof TokenExpiredError) {
        console.warn("JWT expired:", error);
        return next(new Error("Token expired"));
      } else if (error instanceof JsonWebTokenError) {
        console.warn("JWT malformed or invalid:", error);
        return next(new Error("Invalid token"));
      } else {
        console.error("Unexpected auth error:", error);
        return next(new Error("Authentication failed"));
      }
    }
  });

  io.on("connection", (socket: AuthenticatedSocket) => {
    console.log(`User ${socket.userId} connected to socket`);

    // Join event attendance room
    socket.on("join-event-room", (eventId: string) => {
      socket.join(`event:${eventId}`);
      console.log(`User ${socket.userId} joined event room: ${eventId}`);
    });

    // Leave event attendance room
    socket.on("leave-event-room", (eventId: string) => {
      socket.leave(`event:${eventId}`);
      console.log(`User ${socket.userId} left event room: ${eventId}`);
    });

    // Handle event joining via socket
    socket.on("join-event", async (eventId: string, callback) => {
      try {
        if (!socket.userId) {
          return callback({ success: false, message: "Not authenticated" });
        }

        const attendance = await AttendanceService.joinEvent(
          socket.userId,
          eventId
        );

        // Join the event room
        socket.join(`event:${eventId}`);

        // Notify all users in the event room about new attendee
        socket.to(`event:${eventId}`).emit("user-joined-event", {
          userId: socket.userId,
          attendance: attendance,
          timestamp: new Date(),
        });

        // Update event attendance count for all listeners
        const attendees = await AttendanceService.getEventAttendees(eventId);
        io.to(`event:${eventId}`).emit("attendance-updated", {
          eventId,
          attendeeCount: attendees.length,
          attendees: attendees.map((a) => ({
            id: a.user.id,
            username: a.user.username,
            joinedAt: a.joinedAt,
          })),
        });

        callback({
          success: true,
          message: "Successfully joined event",
          attendance: attendance,
        });
      } catch (error: any) {
        callback({
          success: false,
          message: error.message || "Failed to join event",
        });
      }
    });

    // Handle event leaving via socket
    socket.on("leave-event", async (eventId: string, callback) => {
      try {
        if (!socket.userId) {
          return callback({ success: false, message: "Not authenticated" });
        }

        await AttendanceService.leaveEvent(socket.userId, eventId);

        // Leave the event room
        socket.leave(`event:${eventId}`);

        // Notify all users in the event room about user leaving
        socket.to(`event:${eventId}`).emit("user-left-event", {
          userId: socket.userId,
          timestamp: new Date(),
        });

        // Update event attendance count
        const attendees = await AttendanceService.getEventAttendees(eventId);
        io.to(`event:${eventId}`).emit("attendance-updated", {
          eventId,
          attendeeCount: attendees.length,
          attendees: attendees.map((a) => ({
            id: a.user.id,
            username: a.user.username,
            joinedAt: a.joinedAt,
          })),
        });

        callback({
          success: true,
          message: "Successfully left event",
        });
      } catch (error: any) {
        callback({
          success: false,
          message: error.message || "Failed to leave event",
        });
      }
    });

    // Handle real-time posting to events
    socket.on(
      "post-to-event",
      async (
        data: {
          eventId: string;
          content?: string;
          mediaUrl?: string;
          locationId: string;
        },
        callback
      ) => {
        try {
          if (!socket.userId) {
            return callback({ success: false, message: "Not authenticated" });
          }

          // Check if user is attending the event
          const isAttending = await AttendanceService.isUserAttending(
            socket.userId,
            data.eventId
          );

          if (!isAttending) {
            return callback({
              success: false,
              message: "You must be attending the event to post",
            });
          }

          // Broadcast the new post to all attendees
          io.to(`event:${data.eventId}`).emit("new-event-post", {
            // post: post,
            eventId: data.eventId,
            timestamp: new Date(),
          });

          callback({ success: true, message: "Post created successfully" });
        } catch (error: any) {
          callback({
            success: false,
            message: error.message || "Failed to create post",
          });
        }
      }
    );

    // Handle disconnection
    socket.on("disconnect", () => {
      console.log(`User ${socket.userId} disconnected from socket`);
    });
  });

  return io;
}

// Helper function to notify about event status changes
export function notifyEventStatusChange(
  io: Server,
  eventId: string,
  status: string
) {
  io.to(`event:${eventId}`).emit("event-status-changed", {
    eventId,
    status,
    timestamp: new Date(),
  });
}

// Helper function to force remove all attendees when event ends
export async function forceEndEventAttendance(io: Server, eventId: string) {
  try {
    // Remove all attendees
    await AttendanceService.endEventAttendance(eventId);

    // Notify all users in the event room that the event has ended
    io.to(`event:${eventId}`).emit("event-ended", {
      eventId,
      message:
        "Event has ended. You have been automatically removed from attendance.",
      timestamp: new Date(),
    });

    // Disconnect all sockets from the event room
    const sockets = await io.in(`event:${eventId}`).fetchSockets();
    sockets.forEach((socket) => {
      socket.leave(`event:${eventId}`);
    });
  } catch (error) {
    console.error("Error force ending event attendance:", error);
  }
}
