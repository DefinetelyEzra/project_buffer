import { Request, Response, NextFunction } from 'express';

// Custom MongoDB injection protection 
export const mongoSanitizer = (req: Request, res: Response, next: NextFunction): void => {
  try {
    // Sanitize body
    if (req.body) {
      req.body = sanitizeMongoInjection(req.body);
    }

    if (req.query && Object.keys(req.query).length > 0) {
      // Store sanitized query in a custom property
      req.sanitizedQuery = sanitizeMongoInjection({ ...req.query });
    }
    
    if (req.params && Object.keys(req.params).length > 0) {
      req.sanitizedParams = sanitizeMongoInjection({ ...req.params });
    }
    
    next();
  } catch (error) {
    console.error('Error in mongo sanitization middleware:', error);
    next(error);
  }
};

// XSS protection middleware
export const xssProtection = (req: Request, res: Response, next: NextFunction): void => {
  try {
    // Clean body
    if (req.body && typeof req.body === 'object') {
      req.body = cleanXSS(req.body);
    }
    
    // Clean headers 
    if (req.headers) {
      const headersToClean = ['user-agent', 'referer', 'origin'];
      headersToClean.forEach(header => {
        const headerValue = req.headers[header];
        if (headerValue && typeof headerValue === 'string') {
          req.headers[header] = cleanXSSString(headerValue);
        }
      });
    }
    
    next();
  } catch (error) {
    console.error('Error in XSS protection middleware:', error);
    next(error);
  }
};

// Comprehensive input sanitization
export const sanitizeInput = (req: Request, res: Response, next: NextFunction): void => {
  try {
    // Use sanitized versions if available, otherwise sanitize directly
    const queryToUse = req.sanitizedQuery || req.query;
    const paramsToUse = req.sanitizedParams || req.params;
    
    // Store sanitized data in custom properties for route handlers to use
    if (queryToUse && typeof queryToUse === 'object') {
      req.cleanQuery = sanitizeObject(queryToUse);
    }
    
    if (paramsToUse && typeof paramsToUse === 'object') {
      req.cleanParams = sanitizeObject(paramsToUse);
    }
    
    // Body can be directly modified since it's not read-only
    if (req.body) {
      req.body = sanitizeObject(req.body);
    }
    
    next();
  } catch (error) {
    console.error('Error in sanitizeInput middleware:', error);
    next(error);
  }
};

// MongoDB injection sanitization utility
function sanitizeMongoInjection(obj: any): any {
  if (typeof obj !== 'object' || obj === null) return obj;

  if (Array.isArray(obj)) {
    return obj.map(sanitizeMongoInjection);
  }

  const sanitized: Record<string, any> = {};
  for (const [key, value] of Object.entries(obj)) {
    // Remove MongoDB operators and dangerous characters from keys
    const cleanKey = typeof key === 'string' 
      ? key.replace(/^\$/, '').replace(/\./g, '_') 
      : key;
    
    // Recursively sanitize values
    if (typeof value === 'object' && value !== null) {
      sanitized[cleanKey] = sanitizeMongoInjection(value);
    } else {
      sanitized[cleanKey] = value;
    }
  }

  return sanitized;
}

// XSS cleaning utility function
function cleanXSS(obj: any): any {
  if (typeof obj !== 'object' || obj === null) {
    return typeof obj === 'string' ? cleanXSSString(obj) : obj;
  }

  if (Array.isArray(obj)) {
    return obj.map(cleanXSS);
  }

  const cleaned: Record<string, any> = {};
  for (const [key, value] of Object.entries(obj)) {
    cleaned[key] = typeof value === 'string' ? cleanXSSString(value) : cleanXSS(value);
  }

  return cleaned;
}

// XSS string cleaning function
function cleanXSSString(str: string): string {
  if (typeof str !== 'string') return str;
  
  return str
    // Remove script tags
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
    // Remove iframe tags
    .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '')
    // Remove object and embed tags
    .replace(/<(object|embed)[^>]*>.*?<\/(object|embed)>/gi, '')
    // Remove javascript: and vbscript: protocols
    .replace(/javascript:/gi, 'removed:')
    .replace(/vbscript:/gi, 'removed:')
    // Remove on* event handlers
    .replace(/\bon\w+\s*=/gi, 'data-removed=')
    // Remove style attributes (can contain expressions)
    .replace(/\bstyle\s*=/gi, 'data-style=')
    // Remove expression() from CSS
    .replace(/expression\s*\(/gi, 'removed(');
}

// General sanitization utility
function sanitizeObject(obj: any): any {
  if (typeof obj !== 'object' || obj === null) return obj;

  if (Array.isArray(obj)) {
    return obj.map(sanitizeObject);
  }

  const sanitized: Record<string, any> = {};
  for (const [key, value] of Object.entries(obj)) {
    // Clean the key
    const cleanKey = typeof key === 'string' 
      ? key.replace(/[.$]/g, '_').replace(/^\$/, '') 
      : key;
    
    // Clean the value
    if (typeof value === 'string') {
      sanitized[cleanKey] = cleanXSSString(value)
        .replace(/[<>'"]/g, '') // Remove potentially dangerous characters
        .trim();
    } else {
      sanitized[cleanKey] = sanitizeObject(value);
    }
  }

  return sanitized;
}

// Helper function for route handlers to access clean data
export const getCleanQuery = (req: Request): any => {
  return req.cleanQuery || {};
};

export const getCleanParams = (req: Request): any => {
  return req.cleanParams || {};
};