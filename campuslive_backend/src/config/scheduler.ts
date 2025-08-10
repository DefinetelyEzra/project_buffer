import dotenv from 'dotenv';

dotenv.config();

export const schedulerConfig = {
  // Enable/disable the scheduler
  enabled: process.env.SCHEDULER_ENABLED !== 'false', // Default to true unless explicitly disabled
  
  // How often to check for events to start (in milliseconds)
  startCheckIntervalMs: parseInt(process.env.EVENT_START_CHECK_INTERVAL_MS || '30000'), // Default: 30 seconds
  
  // How often to check for events to end (in milliseconds)
  endCheckIntervalMs: parseInt(process.env.EVENT_END_CHECK_INTERVAL_MS || '30000'), // Default: 30 seconds
  
  // Buffer time before event start time to consider starting (in minutes)
  startBufferMinutes: parseInt(process.env.EVENT_START_BUFFER_MINUTES || '1'), // Default: 1 minute
  
  // Buffer time before event end time to consider ending (in minutes)
  endBufferMinutes: parseInt(process.env.EVENT_END_BUFFER_MINUTES || '1'), // Default: 1 minute
  
  // Timezone for scheduler operations
  timezone: process.env.SCHEDULER_TIMEZONE || 'Africa/Lagos', // Default to WAT
  
  // Maximum events to process per check cycle
  maxEventsPerCheck: parseInt(process.env.MAX_EVENTS_PER_CHECK || '10'),
  
  // Enable verbose logging for scheduler operations
  verboseLogging: process.env.SCHEDULER_VERBOSE_LOGGING === 'true',
  
  // Retry configuration
  retryAttempts: parseInt(process.env.SCHEDULER_RETRY_ATTEMPTS || '3'),
  retryDelayMs: parseInt(process.env.SCHEDULER_RETRY_DELAY_MS || '5000'), // 5 seconds
  
  // Health check configuration
  healthCheckEnabled: process.env.SCHEDULER_HEALTH_CHECK_ENABLED !== 'false',
  healthCheckIntervalMs: parseInt(process.env.SCHEDULER_HEALTH_CHECK_INTERVAL_MS || '300000'), // 5 minutes
};

// Validation
if (schedulerConfig.startCheckIntervalMs < 5000) {
  console.warn('⚠️ Event start check interval is less than 5 seconds. This may cause high database load.');
}

if (schedulerConfig.endCheckIntervalMs < 5000) {
  console.warn('⚠️ Event end check interval is less than 5 seconds. This may cause high database load.');
}

if (schedulerConfig.startBufferMinutes < 0 || schedulerConfig.startBufferMinutes > 60) {
  console.warn('⚠️ Event start buffer should be between 0-60 minutes.');
}

if (schedulerConfig.endBufferMinutes < 0 || schedulerConfig.endBufferMinutes > 60) {
  console.warn('⚠️ Event end buffer should be between 0-60 minutes.');
}

// Log configuration on startup
if (schedulerConfig.enabled) {
  console.log(' Event Scheduler Configuration:', {
    enabled: schedulerConfig.enabled,
    startCheckInterval: `${schedulerConfig.startCheckIntervalMs}ms`,
    endCheckInterval: `${schedulerConfig.endCheckIntervalMs}ms`,
    startBuffer: `${schedulerConfig.startBufferMinutes}min`,
    endBuffer: `${schedulerConfig.endBufferMinutes}min`,
    timezone: schedulerConfig.timezone,
    maxEventsPerCheck: schedulerConfig.maxEventsPerCheck
  });
}