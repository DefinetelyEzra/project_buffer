from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from datetime import datetime, timezone, timedelta
from sqlalchemy.exc import SQLAlchemyError
from extensions import db
from models import Task, TaskPriority, User, Tag, TimeBlock
from utils.decorators import validate_json, task_ownership_required
from utils.validation import parse_datetime

tasks_bp = Blueprint('tasks', __name__)
DATABASE_ERROR_MESSAGE = "Database error"

@tasks_bp.route('/', methods=['POST'])
@jwt_required()
@validate_json({
    'title': {'type': 'string', 'required': True, 'empty': False, 'minlength': 1},
    'priority': {'type': 'string', 'allowed': [p.value for p in TaskPriority]},
    'deadline': {
        'type': 'datetime',
        'coerce': parse_datetime, 
        'nullable': True
    },
    'tag_ids': {
        'type': 'list',
        'schema': {'type': 'integer', 'coerce': int},
        'nullable': True,
        'default': []
    },
    'description': {
        'type': 'string',
        'nullable': True,
        'default': '',
        'maxlength': 600
    },
    'recurring': {
        'type': 'boolean',
        'required': False,
        'default': False
    },
    'recurrence_pattern': {
        'type': 'string',
        'required': False,
        'nullable': True,
        'maxlength': 200
    }
})
def create_task():
    try:
        user_id = get_jwt_identity()
        data = request.normalized_json

        # if recurring, initialize next_due_date to the first deadline
        if data.get('recurrence_pattern') and data.get('deadline'):
            data['next_due_date'] = data['deadline']

        # Normalize recurrence: if a pattern was passed, set recurring=True
        data['recurring'] = bool(data.get('recurrence_pattern'))

        # Extract tag_ids before constructing the Task
        tag_ids = data.pop('tag_ids', [])

        # Remove timezone info if deadline exists
        if 'deadline' in data and data['deadline']:
            if isinstance(data['deadline'], str):
                data['deadline'] = parse_datetime(data['deadline'])

        # Create the task object
        task = Task(user_id=user_id, **data)

        # Attach any tags
        if tag_ids:
            tag_objs = Tag.query.filter(
                Tag.user_id == user_id,
                Tag.id.in_(tag_ids)
            ).all()
            task.tags = tag_objs

        # Initialize next due date for recurring tasks
        if task.recurring:
            task.next_due_date = task.next_occurrence()

        db.session.add(task)
        db.session.commit()

        return jsonify(task.to_dict()), 201
    except SQLAlchemyError as e:
        db.session.rollback()
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500
    except Exception as e:
        return jsonify(error="Server error", message=str(e)), 500

@tasks_bp.route('/', methods=['GET'])
@jwt_required()
def get_tasks():
    try:
        user_id = get_jwt_identity()
        # exclude soft-deleted
        tasks = Task.query \
            .filter_by(user_id=user_id, permanently_deleted=False) \
            .filter(Task.deleted_at.is_(None)) \
            .order_by(Task.priority.desc(), Task.deadline.asc()) \
            .all()
        return jsonify([t.to_dict() for t in tasks]), 200
    except SQLAlchemyError as e:
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500

@tasks_bp.route('/<int:task_id>', methods=['GET'])
@jwt_required()
@task_ownership_required
def get_task(task_id):
    try:
        task = Task.query.get_or_404(task_id)
        return jsonify(task.to_dict()), 200
    except SQLAlchemyError as e:
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500

@tasks_bp.route('/<int:task_id>', methods=['PUT'])
@jwt_required()
@task_ownership_required
@validate_json({
    'title': {'type': 'string', 'required': False, 'empty': False}, 
    'description': {'type': 'string', 'required': False, 'nullable': True},
    'is_completed':{'type': 'boolean', 'required': False},
    'priority': {
        'type': 'string', 
        'allowed': [p.value for p in TaskPriority],
        'default': TaskPriority.MEDIUM.value
    },
    'deadline': {
        'type': 'datetime',
        'coerce': parse_datetime, 
        'nullable': True
    },
    'tag_ids': {
        'type': 'list',
        'schema': {'type': 'integer', 'coerce': int},
        'nullable': True
    },
    'recurring': {
        'type': 'boolean', 
        'required': False
    },
    'recurrence_pattern': {
        'type': 'string', 
        'required': False, 
        'nullable': True, 
        'maxlength': 200
    }
})
def update_task(task_id):
    data = request.normalized_json
    task = Task.query.get_or_404(task_id)

    try:
        # Handle is_completed status change for recurring tasks first
        was_completed = task.is_completed
        is_completed_changed = 'is_completed' in data and data['is_completed'] != was_completed
        
        if is_completed_changed and data['is_completed'] and task.recurring:
            # Task is being marked as complete
            task.is_completed = True  # Mark as complete temporarily
            task.last_recurred_at = datetime.now(timezone.utc)
            
            # Calculate next occurrence
            new_next_due = task.next_occurrence()
            
            # For recurring tasks, we create a new "instance" by resetting is_completed
            # and updating the next_due_date
            if new_next_due:
                task.is_completed = False  # Reset to incomplete for next occurrence
                task.next_due_date = new_next_due
        else:
            # Normal update flow
            _process_recurrence(task, data)
            _process_deadline(task, data)
            _update_simple_fields(task, data, ['description', 'title', 'priority', 'is_completed'])

        if 'tag_ids' in data:
            tag_objs = Tag.query.filter(
                Tag.user_id==get_jwt_identity(),
                Tag.id.in_(data['tag_ids'])
            ).all()
            task.tags = tag_objs

        db.session.commit()
        return jsonify(task.to_dict()), 200

    except Exception as e:
        db.session.rollback()
        return jsonify(error="Update failed", message=str(e)), 400

def _process_recurrence(task, data):
    """
    1) If the client included recurrence_pattern (even null), write it back.
    2) Always recompute next_due_date based on the new `task.recurring` flag
    and any provided/new deadline.
    """
    recurrence_changed = _update_recurrence_pattern(task, data)

    if 'deadline' in data or recurrence_changed:
        is_recurring = task.recurring and task.recurrence_pattern
        if not is_recurring:
            task.next_due_date = None
            return

        if _handle_deadline_update(task, data):
            return

        if not task.next_due_date:
            task.next_due_date = task.next_occurrence()

def _update_recurrence_pattern(task, data):
    """Update task's recurrence pattern and return if it changed."""
    if 'recurrence_pattern' not in data:
        return False

    new_pattern = data['recurrence_pattern']
    old_pattern = task.recurrence_pattern
    task.recurrence_pattern = new_pattern
    task.recurring = bool(new_pattern)
    return old_pattern != new_pattern

def _handle_deadline_update(task, data):
    """Process deadline update and set next_due_date accordingly. Returns True if processed."""
    if 'deadline' not in data:
        return False

    deadline = data['deadline']
    if isinstance(deadline, str):
        deadline = parse_datetime(deadline)
    task.deadline = deadline

    now = datetime.now(timezone.utc)
    if task.deadline and task.deadline > now:
        task.next_due_date = task.deadline
    else:
        task.next_due_date = task.next_occurrence()
    return True

def _process_deadline(task, data):
    if 'deadline' in data:
        d = data['deadline']
        if isinstance(d, str):
            d = parse_datetime(d)
        task.deadline = d

def _update_simple_fields(task, data, fields):
    for field in fields:
        if field in data:
            setattr(task, field, data[field])

@tasks_bp.route('/<int:task_id>', methods=['DELETE'])
@jwt_required()
@task_ownership_required
def delete_task(task_id):
    try:
        task = Task.query.get_or_404(task_id)
        # soft-delete
        task.deleted_at = datetime.now(timezone.utc)
        db.session.commit()
        return jsonify(message="Task moved to bin"), 200
    except SQLAlchemyError as e:
        db.session.rollback()
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500

@tasks_bp.route('/deleted', methods=['GET'])
@jwt_required()
def get_deleted_tasks():
    """List tasks in trash (last 30 days, not yet permanently removed)."""
    try:
        user_id = get_jwt_identity()
        cutoff = datetime.now(timezone.utc) - timedelta(days=30)
        tasks = Task.query \
            .filter_by(user_id=user_id, permanently_deleted=False) \
            .filter(Task.deleted_at.isnot(None)) \
            .filter(Task.deleted_at >= cutoff) \
            .all()
        return jsonify([t.to_dict() for t in tasks]), 200
    except SQLAlchemyError as e:
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500

@tasks_bp.route('/<int:task_id>/restore', methods=['POST'])
@jwt_required()
@task_ownership_required
def restore_task(task_id):
    try:
        task = Task.query.get_or_404(task_id)
        if not task.deleted_at:
            return jsonify(error="Task is not in trash"), 400
        task.deleted_at = None
        db.session.commit()
        return jsonify(message="Task restored", task=task.to_dict()), 200
    except SQLAlchemyError as e:
        db.session.rollback()
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500

@tasks_bp.route('/<int:task_id>/permanent', methods=['DELETE'])
@jwt_required()
@task_ownership_required
def purge_task(task_id):
    try:
        task = Task.query.get_or_404(task_id)
        if not task.deleted_at:
            return jsonify(error="Task must be in trash first"), 400
            
        # Handle potential relationship issues by first deleting related time blocks
        # or setting their task_id to NULL
        try:
            TimeBlock.query.filter_by(task_id=task_id).update({TimeBlock.task_id: None})
            db.session.commit()
        except Exception as e:
            # If time_blocks.task_id doesn't exist, we can ignore this error
            db.session.rollback() 
            # Log the error but continue with task deletion
            print(f"Warning: Could not update time blocks: {str(e)}")
        
        # Now try to delete the task
        db.session.delete(task)
        db.session.commit()
        return jsonify(message="Task permanently deleted"), 200
    except SQLAlchemyError as e:
        db.session.rollback()
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500
    
#Notifications Routes    
@tasks_bp.route('/notifications/upcoming', methods=['GET'])
@jwt_required()
def get_upcoming_notifications():
    """
    Return all tasks with deadline_notified == False and due within next hour.
    """
    from datetime import datetime, timedelta, timezone
    now = datetime.now(timezone.utc)
    window = now + timedelta(hours=1)
    tasks = Task.query.filter(
        Task.deadline.isnot(None),
        Task.deadline > now,
        Task.deadline <= window,
        Task.is_completed == False,
        Task.deadline_notified == False
    ).all()
    return jsonify([t.to_dict() for t in tasks]), 200

@tasks_bp.route('/upcoming_recurring', methods=['GET'])
@jwt_required()
def get_upcoming_recurring():
    """
    Return all recurring tasks whose next_due_date is in the future.
    """
    try:
        user_id = get_jwt_identity()
        now = datetime.now(timezone.utc)
        tasks = Task.query \
            .filter(Task.user_id == user_id,
                    Task.recurring == True,
                    Task.next_due_date.isnot(None),
                    Task.next_due_date > now) \
            .order_by(Task.next_due_date.asc()) \
            .all()
        return jsonify([t.to_dict() for t in tasks]), 200
    except SQLAlchemyError as e:
        return jsonify(error=DATABASE_ERROR_MESSAGE, message=str(e)), 500