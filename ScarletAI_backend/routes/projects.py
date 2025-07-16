from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from models import Project, Task, User
from utils.decorators import validate_json
from extensions import db
from sqlalchemy.exc import SQLAlchemyError

projects_bp = Blueprint('projects', __name__)

@projects_bp.route('/projects', methods=['POST'])
@jwt_required()
@validate_json({
    'name': {'type': 'string', 'required': True},
    'description': {'type': 'string'}
})
def create_project():
    try:
        user_id = get_jwt_identity()
        data = request.get_json()
        
        project = Project(
            name=data['name'],
            description=data.get('description'),
            created_by=user_id
        )
        
        db.session.add(project)
        db.session.commit()
        
        return jsonify(project.to_dict()), 201
    except SQLAlchemyError as e:
        db.session.rollback()
        return jsonify(error="Database error", message=str(e)), 500

# Add project-task association endpoints