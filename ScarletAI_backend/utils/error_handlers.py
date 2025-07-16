from flask import jsonify
from werkzeug.exceptions import BadRequest, Unauthorized, Forbidden, NotFound, InternalServerError
from sqlalchemy.exc import SQLAlchemyError, IntegrityError
from datetime import datetime
import logging
import pytz
from extensions import db

logger = logging.getLogger(__name__)

class ApiError(Exception):
    def __init__(self, message, status_code=400, error_type='api_error', payload=None):
        super().__init__()
        self.message = message
        self.status_code = status_code
        self.error_type = error_type
        self.payload = payload
        self.timestamp = datetime.now(pytz.utc).isoformat()

    def to_dict(self):
        rv = {
            'type': self.error_type,
            'message': self.message,
            'timestamp': self.timestamp
        }
        if self.payload:
            rv.update(self.payload)
        return rv

class ValidationError(ApiError):
    def __init__(self, errors, message="Validation failed"):
        super().__init__(
            message=message,
            status_code=400,
            error_type='validation_error',
            payload={'errors': errors}
        )

def register_error_handlers(app):
    @app.errorhandler(ApiError)
    def handle_api_error(error):
        response = jsonify(error.to_dict())
        response.status_code = error.status_code
        logger.error(f"API Error: {error.message}")
        return response

    @app.errorhandler(400)
    def handle_bad_request(error):
        timestamp = datetime.now(pytz.utc).isoformat()
        return jsonify({
            'type': 'bad_request',
            'message': error.description,
            'timestamp': timestamp
        }), 400

    @app.errorhandler(401)
    def handle_unauthorized(error):
        timestamp = datetime.now(pytz.utc).isoformat()
        return jsonify({
            'type': 'unauthorized',
            'message': 'Authentication required',
            'timestamp': timestamp
        }), 401

    @app.errorhandler(403)
    def handle_forbidden(error):
        timestamp = datetime.now(pytz.utc).isoformat()
        return jsonify({
            'type': 'forbidden',
            'message': 'Insufficient permissions',
            'timestamp': timestamp
        }), 403

    @app.errorhandler(404)
    def handle_not_found(error):
        timestamp = datetime.now(pytz.utc).isoformat()
        return jsonify({
            'type': 'not_found',
            'message': 'Resource not found',
            'timestamp': timestamp
        }), 404

    @app.errorhandler(SQLAlchemyError)
    def handle_db_error(error):
        timestamp = datetime.now(pytz.utc).isoformat()
        logger.critical(f"Database error: {str(error)}")
        db.session.rollback()
        return jsonify({
            'type': 'database_error',
            'message': 'A database error occurred',
            'timestamp': timestamp
        }), 500

    @app.errorhandler(Exception)
    def handle_generic_error(error):
        timestamp = datetime.now(pytz.utc).isoformat()
        logger.exception("Unhandled exception occurred")
        return jsonify({
            'type': 'internal_error',
            'message': 'An unexpected error occurred',
            'timestamp': timestamp
        }), 500