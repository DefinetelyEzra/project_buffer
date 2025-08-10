import { createServer } from 'http';
import { Server } from 'socket.io';
import app from './app';
import { logger } from './utils/logger';
import { SocketHandler } from './socket/socket.handler';
import { corsOptions } from './config/cors';
import { startEventCleanupTask } from './utils/eventCleanup'; 

const PORT = process.env.PORT || 3001;

const server = createServer(app);

// Socket.io setup with CORS
const io = new Server(server, {
  cors: corsOptions, 
  transports: ['websocket', 'polling'],
});

// Initialize socket handler
const socketHandler = new SocketHandler(io);

// Start event cleanup task
startEventCleanupTask(); 

const gracefulShutdown = (signal: string) => {
  logger.info(`${signal} received, shutting down gracefully`);
  
  server.close(() => {
    logger.info('HTTP server closed');
    
    io.close(() => {
      logger.info('Socket.io server closed');
      process.exit(0);
    });
  });

  // Force close after 10 seconds
  setTimeout(() => {
    logger.error('Could not close connections in time, forcefully shutting down');
    process.exit(1);
  }, 10000);
};

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// Handle uncaught exceptions
process.on('uncaughtException', (error) => {
  logger.error('Uncaught Exception:', error);
  gracefulShutdown('UNCAUGHT_EXCEPTION');
});

process.on('unhandledRejection', (reason, promise) => {
  logger.error('Unhandled Rejection', { promise, reason });
  gracefulShutdown('UNHANDLED_REJECTION');
});

server.listen(PORT, () => {
  logger.info(`Server running on port ${PORT} in ${process.env.NODE_ENV || 'development'} mode`);
  logger.info(`Health check: http://localhost:${PORT}/health`);
  logger.info(`Socket.io ready for connections`);
});

// Export server and socketHandler for use in other modules if needed
export { server, io, socketHandler };