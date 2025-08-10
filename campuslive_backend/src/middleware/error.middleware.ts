import { Request, Response, NextFunction } from 'express';
import { logger } from '../utils/logger';
import { sendError } from '../utils/response';

export const errorHandler = (
  error: Error,
  req: Request,
  res: Response,
  next: NextFunction
) => {
  logger.error('Unhandled error:', error);

  if (error.name === 'ValidationError') {
    return sendError(res, 'Validation Error', error.message, 400);
  }

  if (error.name === 'UnauthorizedError') {
    return sendError(res, 'Unauthorized', error.message, 401);
  }

  if (error.name === 'PrismaClientKnownRequestError') {
    return sendError(res, 'Database Error', 'A database error occurred', 500);
  }

  return sendError(res, 'Internal Server Error', 
    process.env.NODE_ENV === 'development' ? error.message : 'Something went wrong', 
    500
  );
};

export const notFoundHandler = (req: Request, res: Response) => {
  return sendError(res, 'Route not found', `Route ${req.originalUrl} not found`, 404);
};