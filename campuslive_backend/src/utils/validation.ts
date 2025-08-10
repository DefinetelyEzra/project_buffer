import { z } from 'zod';

// Email regex pattern for validation
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ISO datetime regex pattern
const datetimeRegex = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?Z?$/;

export const registerSchema = z.object({
  email: z.string().regex(emailRegex, 'Invalid email format'),
  username: z.string().min(3, 'Username must be at least 3 characters').max(50, 'Username must be at most 50 characters'),
  password: z.string().min(8, 'Password must be at least 8 characters').regex(
    /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/,
    'Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character'
  ),
  role: z.enum(['STUDENT', 'FACULTY', 'ADMIN']).optional().default('STUDENT'),
});

export const loginSchema = z.object({
  email: z.string().regex(emailRegex, 'Invalid email format'),
  password: z.string().min(1, 'Password is required'),
});

export const createLocationSchema = z.object({
  name: z.string().min(1, 'Location name is required'),
  description: z.string().optional(),
  latitude: z.number().min(-90, 'Latitude must be ≥ -90').max(90, 'Latitude must be ≤ 90'),
  longitude: z.number().min(-180, 'Longitude must be ≥ -180').max(180, 'Longitude must be ≤ 180'),
  category: z.string().min(1, 'Category is required'),
});

export const createEventSchema = z.object({
  title: z.string().min(1, 'Title is required').max(100, 'Title too long'),
  description: z.string().max(500, 'Description too long').optional(),
  startTime: z.string().regex(datetimeRegex, 'Invalid start time format (expected ISO 8601)'),
  endTime: z.string().regex(datetimeRegex, 'Invalid end time format (expected ISO 8601)').optional(),
  locationId: z.string().min(1, 'Invalid location ID'),
  maxAttendees: z.number().int().positive('Max attendees must be positive').optional(),
}).refine(
  (data) => {
    if (data.endTime) {
      return new Date(data.startTime) < new Date(data.endTime);
    }
    return true;
  },
  {
    message: 'End time must be after start time',
    path: ['endTime'],
  }
);

export const createPostSchema = z.object({
  content: z.string().max(1000, 'Content too long').optional(),
  mediaType: z.enum(['IMAGE', 'VIDEO']).optional(),
  locationId: z.string().min(1, 'Invalid location ID'),
  eventId: z.string().min(1, 'Invalid event ID').optional(),
}).refine(
  (data) => {
    return data.content || data.mediaType; // Require content or mediaType
  },
  {
    message: 'Post must have content or media',
    path: ['content'],
  }
);