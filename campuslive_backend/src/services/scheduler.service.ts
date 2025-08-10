import { EventService } from './event.service';
import { logger } from '../utils/logger';
import { schedulerConfig } from '../config/scheduler';
import { io } from '../server';
import { forceEndEventAttendance, notifyEventStatusChange } from '../socket/attendanceHandler';
import prisma from '../config/database';

export class SchedulerService {
  private static instance: SchedulerService;
  private startCheckInterval: NodeJS.Timeout | null = null;
  private endCheckInterval: NodeJS.Timeout | null = null;
  private isRunning = false;
  private lastStartCheck = new Date();
  private lastEndCheck = new Date();
  private startedEventCount = 0;
  private endedEventCount = 0;

  private constructor() { }

  static getInstance(): SchedulerService {
    if (!SchedulerService.instance) {
      SchedulerService.instance = new SchedulerService();
    }
    return SchedulerService.instance;
  }

  start() {
    if (this.isRunning) {
      logger.warn('Scheduler is already running');
      return;
    }

    if (!schedulerConfig.enabled) {
      logger.info('Scheduler is disabled via configuration');
      return;
    }

    this.isRunning = true;
    this.setupEventStartScheduler();
    this.setupEventEndScheduler();

    logger.info('🚀 Event Scheduler started successfully', {
      startCheckInterval: `${schedulerConfig.startCheckIntervalMs}ms`,
      endCheckInterval: `${schedulerConfig.endCheckIntervalMs}ms`,
      timezone: schedulerConfig.timezone,
      startBuffer: `${schedulerConfig.startBufferMinutes} minutes`,
      endBuffer: `${schedulerConfig.endBufferMinutes} minutes`
    });
  }

  stop() {
    if (!this.isRunning) {
      logger.warn('Scheduler is not running');
      return;
    }

    if (this.startCheckInterval) {
      clearInterval(this.startCheckInterval);
      this.startCheckInterval = null;
    }

    if (this.endCheckInterval) {
      clearInterval(this.endCheckInterval);
      this.endCheckInterval = null;
    }

    this.isRunning = false;
    logger.info('⏹️ Event Scheduler stopped');
  }

  getStatus() {
    return {
      isRunning: this.isRunning,
      lastStartCheck: this.lastStartCheck,
      lastEndCheck: this.lastEndCheck,
      startedEventCount: this.startedEventCount,
      endedEventCount: this.endedEventCount,
      config: schedulerConfig
    };
  }

  private setupEventStartScheduler() {
    this.startCheckInterval = setInterval(async () => {
      try {
        await this.checkAndStartEvents();
        this.lastStartCheck = new Date();
      } catch (error) {
        logger.error('Error in event start scheduler:', error);
      }
    }, schedulerConfig.startCheckIntervalMs);
  }

  private setupEventEndScheduler() {
    this.endCheckInterval = setInterval(async () => {
      try {
        await this.checkAndEndEvents();
        this.lastEndCheck = new Date();
      } catch (error) {
        logger.error('Error in event end scheduler:', error);
      }
    }, schedulerConfig.endCheckIntervalMs);
  }

  private async checkAndStartEvents() {
    try {
      const eventsToStart = await EventService.getEventsToStart();

      if (eventsToStart.length === 0) {
        // Only log every 10th check to reduce noise but still provide visibility
        if (this.startedEventCount % 10 === 0) {
          logger.debug('No events to start at this time');
        }
        return;
      }

      logger.info(`🔍 Found ${eventsToStart.length} event(s) ready to start`);

      for (const event of eventsToStart) {
        try {
          // Double-check event hasn't been started by another process
          const currentEvent = await prisma.event.findUnique({
            where: { id: event.id },
            select: { isLive: true, status: true }
          });

          if (!currentEvent || currentEvent.isLive) {
            logger.debug(`Event ${event.id} already started or not found, skipping`);
            continue;
          }

          await EventService.autoStartEvent(event.id);
          this.startedEventCount++;

          // Emit socket events for real-time updates
          if (io) {
            // Broadcast to all connected clients
            io.emit('eventStatusUpdate', {
              eventId: event.id,
              isLive: true,
              status: 'LIVE',
              locationId: event.location.id,
              timestamp: new Date().toISOString(),
              autoStarted: true
            });

            // Notify event room specifically
            notifyEventStatusChange(io, event.id, 'LIVE');
          }

          logger.info(`✅ Auto-started event: "${event.title}" at ${event.location.name} (scheduled: ${event.startTime.toISOString()})`);

        } catch (error) {
          logger.error(`❌ Failed to auto-start event "${event.title}" (${event.id}):`, error);
        }
      }

    } catch (error) {
      logger.error('Error checking events to start:', error);
    }
  }

  private async checkAndEndEvents() {
    try {
      const eventsToEnd = await EventService.getEventsToEnd();

      if (eventsToEnd.length === 0) {
        // Only log every 10th check to reduce noise but still provide visibility
        if (this.endedEventCount % 10 === 0) {
          logger.debug('No events to end at this time');
        }
        return;
      }

      logger.info(`🔍 Found ${eventsToEnd.length} event(s) ready to end`);

      for (const event of eventsToEnd) {
        try {
          // Double-check event hasn't been ended by another process
          const currentEvent = await prisma.event.findUnique({
            where: { id: event.id },
            select: { isLive: true, status: true }
          });

          if (!currentEvent?.isLive) {
            logger.debug(`Event ${event.id} already ended or not found, skipping`);
            continue;
          }

          await EventService.autoEndEvent(event.id);
          this.endedEventCount++;

          // Force end all attendances and emit socket events
          if (io) {
            // Remove all attendees from the event
            await forceEndEventAttendance(io, event.id);

            // Broadcast to all connected clients
            io.emit('eventStatusUpdate', {
              eventId: event.id,
              isLive: false,
              status: 'ENDED',
              locationId: event.location.id,
              timestamp: new Date().toISOString(),
              autoEnded: true
            });

            io.emit('eventEnded', {
              eventId: event.id,
              timestamp: new Date().toISOString(),
              autoEnded: true
            });

            // Notify event room specifically
            notifyEventStatusChange(io, event.id, 'ENDED');
          }

          logger.info(`🔴 Auto-ended event: "${event.title}" at ${event.location.name} (scheduled end: ${event.endTime?.toISOString()})`);

        } catch (error) {
          logger.error(`❌ Failed to auto-end event "${event.title}" (${event.id}):`, error);
        }
      }

    } catch (error) {
      logger.error('Error checking events to end:', error);
    }
  }

  // Method to manually trigger checks (useful for testing)
  async triggerStartCheck() {
    if (!this.isRunning) {
      throw new Error('Scheduler is not running');
    }

    logger.info('Manually triggering event start check');
    await this.checkAndStartEvents();
  }

  async triggerEndCheck() {
    if (!this.isRunning) {
      throw new Error('Scheduler is not running');
    }

    logger.info('Manually triggering event end check');
    await this.checkAndEndEvents();
  }

  // Health check method
  isHealthy(): boolean {
    if (!this.isRunning) return false;

    const now = new Date();
    const maxStaleTime = Math.max(
      schedulerConfig.startCheckIntervalMs,
      schedulerConfig.endCheckIntervalMs
    ) * 2; // Allow for 2x the check interval

    const startCheckStale = now.getTime() - this.lastStartCheck.getTime() > maxStaleTime;
    const endCheckStale = now.getTime() - this.lastEndCheck.getTime() > maxStaleTime;

    return !startCheckStale && !endCheckStale;
  }
}