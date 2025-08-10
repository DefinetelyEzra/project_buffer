import { Router } from 'express';
import { AuthController } from '../controllers/auth.controller';
import { validateRequest } from '../middleware/validation.middleware';
import { authenticateToken, requireRole, restrictAdminCreation } from '../middleware/auth.middleware';
import { registerSchema, loginSchema } from '../utils/validation';

const router = Router();

router.post('/register', authenticateToken, restrictAdminCreation, validateRequest(registerSchema), AuthController.register);
router.post('/login', validateRequest(loginSchema), AuthController.login);
router.get('/profile', authenticateToken, AuthController.getProfile);
router.delete('/delete/:id', authenticateToken, requireRole(['ADMIN']), AuthController.deleteUser);

export default router;