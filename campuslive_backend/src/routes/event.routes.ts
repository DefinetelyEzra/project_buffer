import { Router } from 'express';
import { EventController } from '../controllers/event.controller';
import { validateRequest } from '../middleware/validation.middleware';
import { authenticateToken, requireRole } from '../middleware/auth.middleware';
import { singleEventAttendance, canManageAttendance } from '../middleware/event-posting.middleware';
import { createEventSchema } from '../utils/validation';

const router = Router();

// Public routes
router.get('/', EventController.getAllEvents);
router.get('/:id', EventController.getEventById);
router.get('/:id/attendees', EventController.getEventAttendees);

// Protected routes
router.use(authenticateToken); // All routes below require authentication

// Event creation and management (Faculty/Admin only)
router.post('/', 
  requireRole(['FACULTY', 'ADMIN']), 
  validateRequest(createEventSchema), 
  EventController.createEvent
);

router.patch('/:id/toggle-live', 
  requireRole(['FACULTY', 'ADMIN']), 
  EventController.toggleEventLive
);

router.patch('/:id/end', 
  requireRole(['FACULTY', 'ADMIN']), 
  EventController.endEvent
);

// Event deletion - Admin can delete any event, Faculty can only delete their own events
router.delete('/:id', 
  requireRole(['FACULTY', 'ADMIN']), 
  EventController.deleteEvent
);

// Attendance management (All authenticated users)
router.post('/:id/join', 
  canManageAttendance,
  singleEventAttendance,
  EventController.joinEvent
);

router.post('/:id/leave',
  canManageAttendance,
  EventController.leaveEvent
);

router.get('/my/attendance', EventController.getMyAttendance);

export default router;