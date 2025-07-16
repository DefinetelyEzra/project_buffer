from functools import wraps
from flask import request, jsonify
from flask_jwt_extended import verify_jwt_in_request, get_jwt_identity
from models import Task, Project
from cerberus import Validator

def task_ownership_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        verify_jwt_in_request()
        task_id = kwargs.get('task_id')
        user_id = get_jwt_identity()
        
        try:
            user_id = int(user_id)
        except (ValueError, TypeError):
            return jsonify(error="Unauthorized access"), 403
        
        task = Task.query.get_or_404(task_id)
        if task.user_id != user_id:
            return jsonify(error="Unauthorized access"), 403
            
        return fn(*args, **kwargs)
    return wrapper

def project_ownership_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        verify_jwt_in_request()
        project_id = kwargs.get('project_id')
        user_id = get_jwt_identity()
        
        project = Project.query.get_or_404(project_id)
        if project.created_by != user_id:
            return jsonify(error="Unauthorized access to project"), 403
            
        return fn(*args, **kwargs)
    return wrapper

def validate_json(schema):
    """JSON validation decorator using Cerberus"""
    def decorator(f):
        @wraps(f)
        def wrapper(*args, **kwargs):
            data = request.get_json()
            if not data:
                return jsonify(error="Request must be JSON"), 400
                
            v = Validator(schema)
            if not v.validate(data):
                return jsonify(error="Validation failed", errors=v.errors), 400
                
            # Store the normalized (coerced) document on the request
            request.normalized_json = v.document
                
            return f(*args, **kwargs)
        return wrapper
    return decorator