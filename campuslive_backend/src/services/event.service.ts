import prisma from '../config/database';
import { CreateEventRequest } from '../types';
import { EventStatus } from '@prisma/client';
import { logger } from '../utils/logger';
import { TimezoneUtils } from '../utils/timezone';

export class EventService {
  static async createEvent(data: CreateEventRequest, organizerId: string) {
    const now = new Date();
    const startTime = new Date(data.startTime);
    const endTime = data.endTime ? new Date(data.endTime) : undefined;

    // Event cannot be created for a time in the past
    if (startTime <= now) {
      const timeDiff = Math.round((now.getTime() - startTime.getTime()) / (60 * 1000));
      throw new Error(`Cannot create event for past time. Start time was ${timeDiff} minutes ago.`);
    }

    // End time must be after start time
    if (endTime && endTime <= startTime) {
      throw new Error('End time must be after start time');
    }

    // Event duration should be reasonable (might remove later)
    const maxDurationHours = 24;
    if (endTime && (endTime.getTime() - startTime.getTime()) > (maxDurationHours * 60 * 60 * 1000)) {
      throw new Error(`Event duration cannot exceed ${maxDurationHours} hours`);
    }

    const event = await prisma.event.create({
      data: {
        title: data.title,
        description: data.description,
        startTime: startTime,
        endTime: endTime,
        organizerId,
        locationId: data.locationId,
        maxAttendees: data.maxAttendees || null,
        status: EventStatus.UPCOMING,
        isLive: false
      },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: {
          select: { id: true, name: true }
        }
      }
    });

    logger.info(`Created new event: "${event.title}" scheduled for ${startTime.toISOString()} at ${event.location.name}`);

    // Convert to local timezone for response
    return TimezoneUtils.convertEventDates(event);
  }

  static async getAllEvents(isLive?: boolean, userId?: string) {
    const where: any = {};

    if (isLive !== undefined) {
      where.isLive = isLive;
      if (isLive) {
        where.status = 'LIVE';
      }
    }

    const events = await prisma.event.findMany({
      where,
      orderBy: { startTime: 'asc' },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: {
          select: { id: true, name: true }
        },
        attendances: {
          where: { isActive: true },
          select: {
            id: true,
            userId: true,
            joinedAt: true
          }
        },
        _count: {
          select: {
            posts: true,
            attendances: {
              where: { isActive: true }
            }
          }
        }
      }
    });

    // Add attendance info for current user if provided
    const eventsWithAttendanceInfo = events.map(event => ({
      ...event,
      attendeeCount: event._count.attendances,
      isUserAttending: userId ? event.attendances.some(a => a.userId === userId) : false,
      attendances: undefined // Remove detailed attendance from public response
    }));

    // Convert to local timezone
    return TimezoneUtils.convertEventsDates(eventsWithAttendanceInfo);
  }

  static async getEventById(id: string, userId?: string) {
    const event = await prisma.event.findUnique({
      where: { id },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: true,
        attendances: {
          where: { isActive: true },
          select: {
            id: true,
            userId: true,
            joinedAt: true,
            user: {
              select: {
                id: true,
                username: true
              }
            }
          }
        },
        posts: {
          orderBy: { createdAt: 'desc' },
          include: {
            user: {
              select: { id: true, username: true }
            }
          }
        },
        _count: {
          select: {
            attendances: {
              where: { isActive: true }
            }
          }
        }
      }
    });

    if (!event) return null;

    // Add attendance info for current user
    const eventWithAttendanceInfo = {
      ...event,
      attendeeCount: event._count.attendances,
      isUserAttending: userId ? event.attendances.some(a => a.userId === userId) : false,
      canJoin: event.status === EventStatus.LIVE &&
        (!event.maxAttendees || event._count.attendances < event.maxAttendees) &&
        (!event.endTime || new Date() < event.endTime)
    };

    // Convert to local timezone
    return TimezoneUtils.convertEventDates(eventWithAttendanceInfo);
  }

  static async updateEventStatus(id: string, isLive: boolean, organizerId: string) {
    const event = await prisma.event.findUnique({
      where: { id },
      select: { organizerId: true }
    });

    if (!event) {
      throw new Error('Event not found');
    }

    if (event.organizerId !== organizerId) {
      throw new Error('Unauthorized to update this event');
    }

    const status = isLive ? EventStatus.LIVE : EventStatus.UPCOMING;

    const updatedEvent = await prisma.event.update({
      where: { id },
      data: {
        isLive,
        status: status
      },
      include: {
        location: {
          select: { id: true, name: true }
        }
      }
    });

    return updatedEvent;
  }

  static async endEvent(id: string, organizerId: string) {
    const event = await prisma.event.findUnique({
      where: { id },
      select: { organizerId: true }
    });

    if (!event) {
      throw new Error('Event not found');
    }

    if (event.organizerId !== organizerId) {
      throw new Error('Unauthorized to update this event');
    }

    // End all active attendances first
    await prisma.eventAttendance.updateMany({
      where: {
        eventId: id,
        isActive: true
      },
      data: {
        isActive: false,
        leftAt: new Date()
      }
    });

    const updatedEvent = await prisma.event.update({
      where: { id },
      data: {
        isLive: false,
        status: EventStatus.ENDED,
        endTime: new Date()
      }
    });

    return updatedEvent;
  }

  static async deleteEvent(id: string, organizerId?: string) {
    const event = await prisma.event.findUnique({
      where: { id },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: {
          select: { id: true, name: true }
        },
        _count: {
          select: {
            attendances: {
              where: { isActive: true }
            },
            posts: true
          }
        }
      }
    });

    if (!event) {
      throw new Error('Event not found');
    }

    // If organizerId is provided (non-admin), check ownership
    if (organizerId && event.organizerId !== organizerId) {
      throw new Error('Unauthorized to delete this event');
    }

    // Check if event is currently live with active attendances
    if (event.status === EventStatus.LIVE && event._count.attendances > 0) {
      throw new Error('Cannot delete a live event with active attendees. Please end the event first.');
    }

    // Delete the event (cascade will handle related records)
    const deletedEvent = await prisma.event.delete({
      where: { id },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: {
          select: { id: true, name: true }
        }
      }
    });

    return deletedEvent;
  }

  // NEW HELPER METHODS FOR ATTENDANCE
  static async canUserJoinEvent(userId: string, eventId: string): Promise<{
    canJoin: boolean;
    reason?: string;
  }> {
    const event = await prisma.event.findUnique({
      where: { id: eventId },
      include: {
        attendances: {
          where: { isActive: true }
        }
      }
    });

    if (!event) {
      return { canJoin: false, reason: 'Event not found' };
    }

    if (event.status !== EventStatus.LIVE) {
      return { canJoin: false, reason: 'Event is not live' };
    }

    if (event.endTime && new Date() > event.endTime) {
      return { canJoin: false, reason: 'Event has ended' };
    }

    if (event.maxAttendees && event.attendances.length >= event.maxAttendees) {
      return { canJoin: false, reason: 'Event is at capacity' };
    }

    // Check if user is already attending this event
    const userAttendance = event.attendances.find(a => a.userId === userId);
    if (userAttendance) {
      return { canJoin: false, reason: 'Already attending this event' };
    }

    // Check if user is attending another live event
    const currentAttendance = await prisma.eventAttendance.findFirst({
      where: {
        userId,
        isActive: true,
        event: {
          status: EventStatus.LIVE
        }
      },
      include: {
        event: {
          select: { title: true }
        }
      }
    });

    if (currentAttendance) {
      return {
        canJoin: false,
        reason: `Already attending "${currentAttendance.event.title}"`
      };
    }

    return { canJoin: true };
  }

  static async getEventsToStart() {
    const now = new Date();
    const bufferMinutes = parseInt(process.env.EVENT_START_BUFFER_MINUTES || '2');

    // Only look for events that should start within the next buffer window
    const futureWindow = new Date(now.getTime() + bufferMinutes * 60 * 1000);

    // Don't pick up events that are more than 2x buffer time late
    const maxLateWindow = new Date(now.getTime() - (bufferMinutes * 2) * 60 * 1000);

    const events = await prisma.event.findMany({
      where: {
        status: EventStatus.UPCOMING,
        isLive: false,
        startTime: {
          lte: futureWindow, 
          gte: maxLateWindow 
        }
      },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: {
          select: { id: true, name: true }
        }
      },
      orderBy: {
        startTime: 'asc' // Process earliest events first
      }
    });

    if (events.length > 0) {
      logger.info(`🔍 Scheduler found ${events.length} events to potentially start:`);
      events.forEach(e => {
        const timeDiff = Math.round((now.getTime() - e.startTime.getTime()) / (60 * 1000));
        const status = timeDiff > 0 ? `${timeDiff}min late` : `${Math.abs(timeDiff)}min early`;
        logger.debug(`  - "${e.title}" (${status}) at ${e.location.name}`);
      });
    }

    return events;
  }

  static async getEventsToEnd() {
    const now = new Date();
    const bufferMinutes = parseInt(process.env.EVENT_END_BUFFER_MINUTES || '2');

    // Only look for events that should end within the next buffer window
    const futureWindow = new Date(now.getTime() + bufferMinutes * 60 * 1000);

    // Don't pick up events that are more than 2x buffer time overdue
    const maxLateWindow = new Date(now.getTime() - (bufferMinutes * 2) * 60 * 1000);

    const events = await prisma.event.findMany({
      where: {
        status: EventStatus.LIVE,
        isLive: true,
        endTime: {
          lte: futureWindow, 
          gte: maxLateWindow,
          not: null
        }
      },
      include: {
        organizer: {
          select: { id: true, username: true }
        },
        location: {
          select: { id: true, name: true }
        }
      },
      orderBy: {
        endTime: 'asc' // Process earliest endings first
      }
    });

    if (events.length > 0) {
      logger.info(`🔍 Scheduler found ${events.length} events to potentially end:`);
      events.forEach(e => {
        const timeDiff = Math.round((now.getTime() - (e.endTime?.getTime() || 0)) / (60 * 1000));
        const status = timeDiff > 0 ? `${timeDiff}min overdue` : `${Math.abs(timeDiff)}min remaining`;
        logger.debug(`  - "${e.title}" (${status}) at ${e.location.name}`);
      });
    }

    return events;
  }

  static async autoStartEvent(eventId: string) {
    try {
      // End all active attendances for other events first to prevent conflicts
      await prisma.eventAttendance.updateMany({
        where: {
          isActive: true,
          event: {
            status: EventStatus.LIVE,
            NOT: {
              id: eventId
            }
          }
        },
        data: {
          isActive: false,
          leftAt: new Date()
        }
      });

      const updatedEvent = await prisma.event.update({
        where: { id: eventId },
        data: {
          isLive: true,
          status: EventStatus.LIVE
        },
        include: {
          organizer: {
            select: { id: true, username: true }
          },
          location: {
            select: { id: true, name: true }
          }
        }
      });

      logger.info(`🟢 AUTO-STARTED: Event "${updatedEvent.title}" (${eventId}) - Location: ${updatedEvent.location.name}`);
      return updatedEvent;
    } catch (error) {
      logger.error(`Failed to auto-start event ${eventId}:`, error);
      throw error;
    }
  }

  static async autoEndEvent(eventId: string) {
    try {
      // End all active attendances first
      await prisma.eventAttendance.updateMany({
        where: {
          eventId: eventId,
          isActive: true
        },
        data: {
          isActive: false,
          leftAt: new Date()
        }
      });

      const updatedEvent = await prisma.event.update({
        where: { id: eventId },
        data: {
          isLive: false,
          status: EventStatus.ENDED
        },
        include: {
          organizer: {
            select: { id: true, username: true }
          },
          location: {
            select: { id: true, name: true }
          }
        }
      });

      logger.info(`🔴 AUTO-ENDED: Event "${updatedEvent.title}" (${eventId}) - Location: ${updatedEvent.location.name}`);
      return updatedEvent;
    } catch (error) {
      logger.error(`Failed to auto-end event ${eventId}:`, error);
      throw error;
    }
  }
}