import { Router } from 'express';
import { PostController } from '../controllers/post.controller';
import { validateRequest } from '../middleware/validation.middleware';
import { authenticateToken } from '../middleware/auth.middleware';
import { upload } from '../middleware/upload.middleware';
import { createPostSchema } from '../utils/validation';

const router = Router();

// Public routes
router.get('/location/:locationId', PostController.getPostsByLocation);
router.get('/event/:eventId', PostController.getPostsByEvent);

// Protected routes
router.use(authenticateToken); // All routes below require authentication

router.post('/', 
  upload.single('media'), 
  validateRequest(createPostSchema), 
  PostController.createPost 
);

// Get posts user can access based on attendance
router.get('/my/accessible', PostController.getMyAccessiblePosts);

// Delete post (with attendance checks)
router.delete('/:id', PostController.deletePost);

export default router;