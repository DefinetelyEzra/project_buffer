import { ZodObject, ZodRawShape } from "zod";
import { Request, Response, NextFunction } from "express";
import { sendError } from "../utils/response";

export const validateRequest = (
  schema: ZodObject<ZodRawShape>
) => {
  return (req: Request, res: Response, next: NextFunction) => {
    try {
      schema.parse(req.body);
      next();
    } catch (error) {
      if (error instanceof Error) {
        return sendError(res, "Validation failed", error.message, 400);
      }
      return sendError(res, "Validation failed", "Unknown validation error", 400);
    }
  };
};
