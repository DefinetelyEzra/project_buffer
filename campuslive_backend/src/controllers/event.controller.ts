import { Request, Response, NextFunction } from 'express';
import { EventService } from '../services/event.service';
import { AttendanceService } from '../services/attendance.service';
import { sendSuccess, sendError } from '../utils/response';
import { logger } from '../utils/logger';
import { io } from '../server';
import { forceEndEventAttendance, notifyEventStatusChange } from '../socket/attendanceHandler';
import { AuthenticatedUser } from '../types';

interface AuthenticatedRequest extends Request {
  user?: AuthenticatedUser;
}

export class EventController {
  static async createEvent(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const organizerId = req.user?.id;
      if (!organizerId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      const event = await EventService.createEvent(req.body, organizerId);

      logger.info(`Event created: ${event.title} by user ${organizerId}`);
      return sendSuccess(res, 'Event created successfully', event, 201);
    } catch (error) {
      logger.error('Create event error:', error);
      next(error);
    }
  }

  static async getAllEvents(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { live } = req.query;
      const userId = req.user?.id; // Optional - only available if authenticated
      
      let isLive: boolean | undefined;

      if (live === 'true') {
        isLive = true;
      } else if (live === 'false') {
        isLive = false;
      }

      const events = await EventService.getAllEvents(isLive, userId);

      return sendSuccess(res, 'Events retrieved successfully', events);
    } catch (error) {
      logger.error('Get events error:', error);
      next(error);
    }
  }

  static async getEventById(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;
      const userId = req.user?.id; // Optional - only available if authenticated
      
      const event = await EventService.getEventById(id, userId);

      if (!event) {
        return sendError(res, 'Event not found', undefined, 404);
      }

      return sendSuccess(res, 'Event retrieved successfully', event);
    } catch (error) {
      logger.error('Get event error:', error);
      next(error);
    }
  }

  static async toggleEventLive(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;
      const { isLive } = req.body;
      const organizerId = req.user?.id;

      if (!organizerId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      const event = await EventService.updateEventStatus(id, isLive, organizerId);

      // If event is ending, remove all attendees
      if (!isLive) {
        await forceEndEventAttendance(io, id);
      }

      // Broadcast live event update to all connected clients
      io.emit('eventStatusUpdate', {
        eventId: id,
        isLive,
        locationId: event.location.id,
        timestamp: new Date().toISOString()
      });

      // Notify attendees in the event room
      notifyEventStatusChange(io, id, isLive ? 'LIVE' : 'ENDED');

      logger.info(`Event ${isLive ? 'started' : 'stopped'}: ${id}`);
      const message = isLive ? 'Event started successfully' : 'Event stopped successfully';
      return sendSuccess(res, message, event);
    } catch (error) {
      logger.error('Toggle event live error:', error);

      if (error instanceof Error) {
        let statusCode = 500;

        if (error.message.includes('not found')) {
          statusCode = 404;
        } else if (error.message.includes('Unauthorized')) {
          statusCode = 403;
        }

        return sendError(res, 'Update failed', error.message, statusCode);
      }

      next(error);
    }
  }

  static async endEvent(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;
      const organizerId = req.user?.id;

      if (!organizerId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      const event = await EventService.endEvent(id, organizerId);

      // Force remove all attendees when event ends
      await forceEndEventAttendance(io, id);

      // Broadcast event end to all connected clients
      io.emit('eventEnded', {
        eventId: id,
        timestamp: new Date().toISOString()
      });

      logger.info(`Event ended: ${id}`);
      return sendSuccess(res, 'Event ended successfully', event);
    } catch (error) {
      logger.error('End event error:', error);

      if (error instanceof Error) {
        let statusCode = 500;

        if (error.message.includes('not found')) {
          statusCode = 404;
        } else if (error.message.includes('Unauthorized')) {
          statusCode = 403;
        }

        return sendError(res, 'End event failed', error.message, statusCode);
      }

      next(error);
    }
  }

  static async joinEvent(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;
      const userId = req.user?.id;

      if (!userId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      const attendance = await AttendanceService.joinEvent(userId, id);

      // Notify other attendees
      io.to(`event:${id}`).emit('user-joined-event', {
        userId,
        username: req.user?.username,
        timestamp: new Date()
      });

      logger.info(`User ${userId} joined event ${id}`);
      return sendSuccess(res, 'Successfully joined event', {
        attendance,
        event: attendance.event
      });

    } catch (error) {
      logger.error('Join event error:', error);

      if (error instanceof Error) {
        return sendError(res, 'Failed to join event', error.message, 400);
      }

      next(error);
    }
  }

  static async leaveEvent(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;
      const userId = req.user?.id;

      if (!userId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      await AttendanceService.leaveEvent(userId, id);

      // Notify other attendees
      io.to(`event:${id}`).emit('user-left-event', {
        userId,
        username: req.user?.username,
        timestamp: new Date()
      });

      logger.info(`User ${userId} left event ${id}`);
      return sendSuccess(res, 'Successfully left event');

    } catch (error) {
      logger.error('Leave event error:', error);

      if (error instanceof Error) {
        return sendError(res, 'Failed to leave event', error.message, 400);
      }

      next(error);
    }
  }

  static async getMyAttendance(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const userId = req.user?.id;

      if (!userId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      const attendance = await AttendanceService.getCurrentAttendance(userId);

      return sendSuccess(res, 'Attendance retrieved successfully', attendance);

    } catch (error) {
      logger.error('Get attendance error:', error);
      next(error);
    }
  }

  static async getEventAttendees(req: Request, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;

      const attendees = await AttendanceService.getEventAttendees(id);

      return sendSuccess(res, 'Attendees retrieved successfully', {
        count: attendees.length,
        attendees: attendees.map(a => ({
          id: a.id,
          joinedAt: a.joinedAt,
          user: a.user
        }))
      });

    } catch (error) {
      logger.error('Get attendees error:', error);
      next(error);
    }
  }

  static async deleteEvent(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    try {
      const { id } = req.params;
      const userId = req.user?.id;
      const userRole = req.user?.role;

      if (!userId) {
        return sendError(res, 'Authentication required', undefined, 401);
      }

      // For non-admin users, pass organizerId to check ownership
      // For admin users, pass undefined to allow deletion of any event
      const organizerId = userRole === 'ADMIN' ? undefined : userId;

      const deletedEvent = await EventService.deleteEvent(id, organizerId);

      // If event was live, notify all clients
      if (deletedEvent.status === 'LIVE') {
        // Force remove all attendees if any
        await forceEndEventAttendance(io, id);

        // Broadcast event deletion to all connected clients
        io.emit('eventDeleted', {
          eventId: id,
          eventTitle: deletedEvent.title,
          locationId: deletedEvent.location.id,
          timestamp: new Date().toISOString(),
          deletedBy: req.user?.username
        });
      }

      logger.info(`Event deleted: "${deletedEvent.title}" (${id}) by ${req.user?.username} (${userRole})`);
      
      return sendSuccess(res, 'Event deleted successfully', {
        deletedEvent: {
          id: deletedEvent.id,
          title: deletedEvent.title,
          organizer: deletedEvent.organizer,
          location: deletedEvent.location,
          deletedAt: new Date().toISOString(),
          deletedBy: req.user?.username
        }
      });
    } catch (error) {
      logger.error('Delete event error:', error);

      if (error instanceof Error) {
        let statusCode = 500;

        if (error.message.includes('not found')) {
          statusCode = 404;
        } else if (error.message.includes('Unauthorized')) {
          statusCode = 403;
        } else if (error.message.includes('Cannot delete')) {
          statusCode = 400;
        }

        return sendError(res, 'Delete failed', error.message, statusCode);
      }

      next(error);
    }
  }
}