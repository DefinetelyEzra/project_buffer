import prisma from "../config/database";
import { EventStatus } from "@prisma/client";

export class AttendanceService {
  /**
   * Join an event - with security checks
   */
  static async joinEvent(userId: string, eventId: string) {
    // Check if event exists and is live
    const event = await prisma.event.findUnique({
      where: { id: eventId },
      select: {
        id: true,
        title: true,
        description: true,
        startTime: true,
        endTime: true,
        isLive: true,
        status: true,
        createdAt: true,
        updatedAt: true,
        organizerId: true,
        locationId: true,
        maxAttendees: true,
        attendances: {
          where: { isActive: true },
        },
      },
    });

    if (!event) {
      throw new Error("Event not found");
    }

    if (event.status !== EventStatus.LIVE) {
      throw new Error("Event is not currently live");
    }

    // Check if event has ended
    if (event.endTime && new Date() > event.endTime) {
      throw new Error("Event has already ended");
    }

    // Check if user is already attending this event
    const existingAttendance = await prisma.eventAttendance.findUnique({
      where: {
        userId_eventId: {
          userId,
          eventId,
        },
      },
    });

    if (existingAttendance?.isActive) {
      throw new Error("You are already attending this event");
    }

    // Check if user is attending any other live event
    const currentAttendance = await prisma.eventAttendance.findFirst({
      where: {
        userId,
        isActive: true,
        event: {
          status: EventStatus.LIVE,
        },
      },
      include: {
        event: true,
      },
    });

    if (currentAttendance?.event?.title) {
      throw new Error(
        `You are already attending "${currentAttendance.event.title}". Leave that event first.`
      );
    }

    // Check capacity if set
    if (event.maxAttendees && event.attendances.length >= event.maxAttendees) {
      throw new Error("Event has reached maximum capacity");
    }

    // Create or reactivate attendance
    const attendance = await prisma.eventAttendance.upsert({
      where: {
        userId_eventId: {
          userId,
          eventId,
        },
      },
      update: {
        isActive: true,
        joinedAt: new Date(),
        leftAt: null,
      },
      create: {
        userId,
        eventId,
        isActive: true,
      },
      include: {
        event: {
          include: {
            location: true,
          },
        },
      },
    });

    return attendance;
  }

  /**
   * Leave an event
   */
  static async leaveEvent(userId: string, eventId: string) {
    const attendance = await prisma.eventAttendance.findUnique({
      where: {
        userId_eventId: {
          userId,
          eventId,
        },
      },
    });

    // @ts-ignore: sonarlint(typescript:S6582)
    if (!attendance?.isActive) {
      throw new Error("You are not currently attending this event");
    }

    const updatedAttendance = await prisma.eventAttendance.update({
      where: {
        userId_eventId: {
          userId,
          eventId,
        },
      },
      data: {
        isActive: false,
        leftAt: new Date(),
      },
    });

    return updatedAttendance;
  }

  /**
   * Check if user is currently attending an event
   */
  static async isUserAttending(
    userId: string,
    eventId: string
  ): Promise<boolean> {
    const attendance = await prisma.eventAttendance.findUnique({
      where: {
        userId_eventId: {
          userId,
          eventId,
        },
      },
    });

    return attendance?.isActive || false;
  }

  /**
   * Get user's current attendance (if any)
   */
  static async getCurrentAttendance(userId: string) {
    return await prisma.eventAttendance.findFirst({
      where: {
        userId,
        isActive: true,
        event: {
          status: EventStatus.LIVE,
        },
      },
      include: {
        event: {
          include: {
            location: true,
          },
        },
      },
    });
  }

  /**
   * Remove all attendees when event ends
   */
  static async endEventAttendance(eventId: string) {
    await prisma.eventAttendance.updateMany({
      where: {
        eventId,
        isActive: true,
      },
      data: {
        isActive: false,
        leftAt: new Date(),
      },
    });
  }

  /**
   * Get active attendees for an event
   */
  static async getEventAttendees(eventId: string) {
    return await prisma.eventAttendance.findMany({
      where: {
        eventId,
        isActive: true,
      },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            role: true,
          },
        },
      },
    });
  }

  /**
   * Clean up expired attendances (for events that have ended)
   */
  static async cleanupExpiredAttendances() {
    const expiredEvents = await prisma.event.findMany({
      where: {
        endTime: {
          lt: new Date(),
        },
        status: {
          in: [EventStatus.LIVE, EventStatus.UPCOMING],
        },
      },
    });

    for (const event of expiredEvents) {
      await this.endEventAttendance(event.id);

      // Update event status
      await prisma.event.update({
        where: { id: event.id },
        data: { status: EventStatus.ENDED, isLive: false },
      });
    }
  }
}
