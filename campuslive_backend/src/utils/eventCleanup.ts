import cron from 'node-cron';
import { AttendanceService } from '../services/attendance.service';

/**
 * Cleanup task that runs every 5 minutes to:
 * 1. Remove attendees from events that have ended
 * 2. Update event status from LIVE to ENDED
 */
export function startEventCleanupTask() {
  // Run every 5 minutes
  cron.schedule('*/5 * * * *', async () => {
    try {
      console.log('Running event cleanup task...');
      await AttendanceService.cleanupExpiredAttendances();
      console.log('Event cleanup completed');
    } catch (error) {
      console.error('Event cleanup failed:', error);
    }
  });

  console.log('Event cleanup task scheduled (every 5 minutes)');
}

/** Cleanup task that runs every minute for more responsive cleanup
export function startFrequentEventCleanupTask() {
  // Run every minute
  cron.schedule('* * * * *', async () => {
    try {
      await AttendanceService.cleanupExpiredAttendances();
    } catch (error) {
      console.error('Frequent event cleanup failed:', error);
    }
  });

  console.log('Frequent event cleanup task scheduled (every minute)');
}
  **/