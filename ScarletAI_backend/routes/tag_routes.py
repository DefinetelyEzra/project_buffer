from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from extensions import db
from models import Tag

tag_bp = Blueprint('tags', __name__)

@tag_bp.route('/', methods=['GET'])
@jwt_required()
def list_tags():
    user_id = get_jwt_identity()
    tags = Tag.query.filter_by(user_id=user_id).order_by(Tag.name).all()
    return jsonify([t.to_dict() for t in tags]), 200

@tag_bp.route('/', methods=['POST'])
@jwt_required()
def create_tag():
    data = request.get_json() or {}
    name = data.get('name', '').strip()
    if not name:
        return jsonify(error="Tag name required"), 400
    user_id = get_jwt_identity()
    # enforce uniqueness per user
    if Tag.query.filter_by(user_id=user_id, name=name).first():
        return jsonify(error="Tag already exists"), 400
    tag = Tag(user_id=user_id, name=name)
    db.session.add(tag)
    db.session.commit()
    return jsonify(tag.to_dict()), 201

@tag_bp.route('/<int:tag_id>', methods=['DELETE'])
@jwt_required()
def delete_tag(tag_id):
    user_id = get_jwt_identity()
    tag = Tag.query.filter_by(id=tag_id, user_id=user_id).first_or_404()
    db.session.delete(tag)
    db.session.commit()
    return jsonify(message="Tag deleted"), 200
