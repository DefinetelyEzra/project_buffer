import { Request, Response, NextFunction } from "express";
import { PostService } from "../services/post.service";
import { AttendanceService } from "../services/attendance.service";
import { sendSuccess, sendError } from "../utils/response";
import { logger } from "../utils/logger";
import { EventStatus } from "@prisma/client";
import { AuthenticatedUser } from "../types";
import prisma from "../config/database";

interface AuthenticatedRequest extends Request {
  user?: AuthenticatedUser;
}

export class PostController {
  static async createPost(
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ) {
    try {
      const userId = req.user?.id;
      if (!userId) {
        return sendError(res, "Authentication required", undefined, 401);
      }

      const { eventId } = req.body;

      // If posting to an event, check attendance and event status
      if (eventId) {
        // Check if event exists and is live
        const event = await prisma.event.findUnique({
          where: { id: eventId },
        });

        if (!event) {
          return sendError(res, "Event not found", undefined, 404);
        }

        if (event.status !== EventStatus.LIVE) {
          return sendError(res, "Event is not currently live", undefined, 403);
        }

        // Check if event has ended
        if (event.endTime && new Date() > event.endTime) {
          return sendError(res, "Event has ended", undefined, 403);
        }

        // Check if user is attending this event
        const isAttending = await AttendanceService.isUserAttending(
          userId,
          eventId
        );

        if (!isAttending) {
          return sendError(
            res,
            "You must be attending the event to post content",
            undefined,
            403
          );
        }
      }

      const post = await PostService.createPost(req.body, userId, req.file);

      let message = `Post created by user ${userId} at location ${req.body.locationId}`;
      if (eventId) {
        message += ` for event ${eventId}`;
      }
      logger.info(message);

      return sendSuccess(res, "Post created successfully", post, 201);
    } catch (error) {
      logger.error("Create post error:", error);
      next(error);
    }
  }

  static async getPostsByLocation(
    req: Request,
    res: Response,
    next: NextFunction
  ) {
    try {
      const { locationId } = req.params;
      const page = parseInt(req.query.page as string) || 1;
      const limit = parseInt(req.query.limit as string) || 20;
      const { eventOnly } = req.query; // Optional filter for event posts only

      const result = await PostService.getPostsByLocation(
        locationId,
        page,
        limit,
        eventOnly === "true"
      );

      return sendSuccess(res, "Posts retrieved successfully", result);
    } catch (error) {
      logger.error("Get posts error:", error);
      next(error);
    }
  }

  static async getPostsByEvent(
    req: Request,
    res: Response,
    next: NextFunction
  ) {
    try {
      const { eventId } = req.params;
      const page = parseInt(req.query.page as string) || 1;
      const limit = parseInt(req.query.limit as string) || 20;

      const posts = await PostService.getPostsByEvent(eventId, page, limit);

      return sendSuccess(res, "Event posts retrieved successfully", posts);
    } catch (error) {
      logger.error("Get event posts error:", error);
      next(error);
    }
  }

  static async deletePost(
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ) {
    try {
      const { id } = req.params;
      const userId = req.user?.id;

      if (!userId) {
        return sendError(res, "Authentication required", undefined, 401);
      }

      const post = await prisma.post.findUnique({
        where: { id },
        select: {
          userId: true,
          eventId: true,
          event: {
            select: {
              status: true,
              endTime: true,
            },
          },
        },
      });

      if (!post) return sendError(res, "Post not found", undefined, 404);

      const isAdmin = req.user?.role === "ADMIN";

      const isPostOwner = post.userId === userId;
      if (!isPostOwner && !isAdmin) {
        return sendError(
          res,
          "Unauthorized to delete this post",
          undefined,
          403
        );
      }

      if (post.eventId && post.event?.status === EventStatus.LIVE && !isAdmin) {
        const isStillAttending = await AttendanceService.isUserAttending(
          userId,
          post.eventId
        );
        if (!isStillAttending) {
          return sendError(
            res,
            "You can only delete event posts while attending the event",
            undefined,
            403
          );
        }
      }

      await PostService.deletePost(id, userId);
      logger.info(`Post deleted: ${id} by user ${userId}`);
      return sendSuccess(res, "Post deleted successfully");
    } catch (error) {
      logger.error("Delete post error:", error);

      if (error instanceof Error) {
        let statusCode = 500;
        if (error.message.includes("not found")) statusCode = 404;
        else if (error.message.includes("Unauthorized")) statusCode = 403;

        return sendError(res, "Delete failed", error.message, statusCode);
      }

      next(error);
    }
  }

  // Get posts that current user can see (based on attendance)
  static async getMyAccessiblePosts(
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ) {
    try {
      const userId = req.user?.id;

      if (!userId) {
        return sendError(res, "Authentication required", undefined, 401);
      }

      // Get current attendance
      const currentAttendance = await AttendanceService.getCurrentAttendance(
        userId
      );

      const posts = await PostService.getPostsForUser(
        userId,
        currentAttendance?.eventId
      );

      return sendSuccess(res, "Accessible posts retrieved successfully", posts);
    } catch (error) {
      logger.error("Get accessible posts error:", error);
      next(error);
    }
  }
}
