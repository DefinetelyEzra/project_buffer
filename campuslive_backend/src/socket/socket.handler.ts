import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { logger } from '../utils/logger';
import { AuthenticatedUser } from '../types';
import { AttendanceService } from '../services/attendance.service';

interface AuthenticatedSocket extends Socket {
  user?: AuthenticatedUser;
}

export class SocketHandler {
  private readonly io: Server;
  private readonly connectedUsers: Map<string, string> = new Map(); // userId -> socketId

  constructor(io: Server) {
    this.io = io;
    this.setupMiddleware();
    this.setupEventHandlers();
    this.setupAttendanceHandlers();
  }

  private setupMiddleware() {
    // Authentication middleware for socket connections
    this.io.use((socket: AuthenticatedSocket, next) => {
      const token = socket.handshake.auth.token;

      if (!token) {
        return next(new Error('Authentication required'));
      }

      try {
        const secret = process.env.JWT_SECRET;
        if (!secret) {
          logger.error('JWT_SECRET not configured');
          throw new Error('JWT misconfiguration');
        }

        const decoded = jwt.verify(token, secret) as AuthenticatedUser;
        socket.user = decoded;
        next();
      } catch (error: any) {
        logger.error(`Socket authentication failed: ${error.message}`);
        next(new Error('Authentication failed'));
      }
    });
  }

  private setupEventHandlers() {
    this.io.on('connection', (socket: AuthenticatedSocket) => {
      logger.info(`User connected: ${socket.user?.username} (${socket.id})`);

      if (socket.user) {
        this.connectedUsers.set(socket.user.id, socket.id);
      }

      // Join location-specific rooms
      socket.on('joinLocation', (locationId: string) => {
        socket.join(`location:${locationId}`);
        logger.info(`User ${socket.user?.username} joined location: ${locationId}`);
      });

      // Leave location-specific rooms
      socket.on('leaveLocation', (locationId: string) => {
        socket.leave(`location:${locationId}`);
        logger.info(`User ${socket.user?.username} left location: ${locationId}`);
      });

      // Join event-specific rooms
      socket.on('joinEvent', (eventId: string) => {
        socket.join(`event:${eventId}`);
        logger.info(`User ${socket.user?.username} joined event: ${eventId}`);
      });

      // Leave event-specific rooms
      socket.on('leaveEvent', (eventId: string) => {
        socket.leave(`event:${eventId}`);
        logger.info(`User ${socket.user?.username} left event: ${eventId}`);
      });

      // Handle real-time messaging for events
      socket.on('eventMessage', (data: {
        eventId: string;
        message: string;
        timestamp: string;
      }) => {
        socket.to(`event:${data.eventId}`).emit('newEventMessage', {
          ...data,
          user: {
            id: socket.user?.id,
            username: socket.user?.username,
          },
        });
      });

      // Handle typing indicators
      socket.on('typing', (data: { eventId: string; isTyping: boolean }) => {
        socket.to(`event:${data.eventId}`).emit('userTyping', {
          userId: socket.user?.id,
          username: socket.user?.username,
          isTyping: data.isTyping,
        });
      });

      // Handle disconnect
      socket.on('disconnect', () => {
        if (socket.user) {
          this.connectedUsers.delete(socket.user.id);
        }
        logger.info(`User disconnected: ${socket.user?.username} (${socket.id})`);
      });
    });
  }

  // Method to broadcast new posts to location subscribers
  public broadcastNewPost(locationId: string, post: any) {
    this.io.to(`location:${locationId}`).emit('newPost', post);
  }

  // Method to broadcast event status updates
  public broadcastEventUpdate(eventId: string, locationId: string, update: any) {
    this.io.to(`event:${eventId}`).emit('eventUpdate', update);
    this.io.to(`location:${locationId}`).emit('locationEventUpdate', update);
  }

  // Method to get connected users count
  public getConnectedUsersCount(): number {
    return this.connectedUsers.size;
  }

  // Method to check if user is online
  public isUserOnline(userId: string): boolean {
    return this.connectedUsers.has(userId);
  }

  private setupAttendanceHandlers() {
    this.io.on('connection', (socket: AuthenticatedSocket) => {
      // Add these new event handlers to your existing connection handler

      // Handle event attendance via socket
      socket.on('join-event-attendance', async (eventId: string, callback) => {
        try {
          if (!socket.user?.id) {
            return callback({ success: false, message: 'Not authenticated' });
          }

          const attendance = await AttendanceService.joinEvent(socket.user.id, eventId);

          // Join the event room for real-time updates
          socket.join(`event:${eventId}`);

          // Notify other attendees
          socket.to(`event:${eventId}`).emit('user-joined-event', {
            userId: socket.user.id,
            username: socket.user.username,
            attendance: attendance,
            timestamp: new Date()
          });

          // Update attendance count for all listeners
          const attendees = await AttendanceService.getEventAttendees(eventId);
          this.io.to(`event:${eventId}`).emit('attendance-updated', {
            eventId,
            attendeeCount: attendees.length
          });

          callback({
            success: true,
            message: 'Successfully joined event',
            attendance: attendance
          });

        } catch (error: any) {
          callback({
            success: false,
            message: error.message || 'Failed to join event'
          });
        }
      });

      socket.on('leave-event-attendance', async (eventId: string, callback) => {
        try {
          if (!socket.user?.id) {
            return callback({ success: false, message: 'Not authenticated' });
          }

          await AttendanceService.leaveEvent(socket.user.id, eventId);

          // Leave the event room
          socket.leave(`event:${eventId}`);

          // Notify other attendees
          socket.to(`event:${eventId}`).emit('user-left-event', {
            userId: socket.user.id,
            username: socket.user.username,
            timestamp: new Date()
          });

          // Update attendance count
          const attendees = await AttendanceService.getEventAttendees(eventId);
          this.io.to(`event:${eventId}`).emit('attendance-updated', {
            eventId,
            attendeeCount: attendees.length
          });

          callback({
            success: true,
            message: 'Successfully left event'
          });

        } catch (error: any) {
          callback({
            success: false,
            message: error.message || 'Failed to leave event'
          });
        }
      });
    });
  }

  public async forceEndEventAttendance(eventId: string) {
    try {
      // Remove all attendees
      await AttendanceService.endEventAttendance(eventId);

      // Notify all users in the event room
      this.io.to(`event:${eventId}`).emit('event-ended', {
        eventId,
        message: 'Event has ended. You have been automatically removed from attendance.',
        timestamp: new Date()
      });

      // Remove all sockets from the event room
      const sockets = await this.io.in(`event:${eventId}`).fetchSockets();
      sockets.forEach(socket => {
        socket.leave(`event:${eventId}`);
      });

    } catch (error) {
      logger.error('Error force ending event attendance:', error);
    }
  }

  public notifyEventStatusChange(eventId: string, status: string) {
    this.io.to(`event:${eventId}`).emit('event-status-changed', {
      eventId,
      status,
      timestamp: new Date()
    });
  }
}
