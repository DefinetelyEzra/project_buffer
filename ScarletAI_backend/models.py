from datetime import datetime, timezone, timedelta 
from dateutil.relativedelta import relativedelta
from extensions import db
from werkzeug.security import generate_password_hash, check_password_hash
from sqlalchemy.dialects.postgresql import JSONB, ARRAY
from enum import Enum
from sqlalchemy.dialects.postgresql import ENUM
from sqlalchemy import CheckConstraint, func, event, inspect
import logging
import pyotp
import calendar

# Define foreign key constants
USER_ID_FK = 'scarlet.users.id'
TASK_ID_FK = 'scarlet.tasks.id'
PROJECT_ID_FK = 'scarlet.projects.id'

logger = logging.getLogger(__name__)

class TaskPriority(Enum):
    HIGH = 'high'
    MEDIUM = 'medium'
    LOW = 'low'

class RecurrencePattern(Enum):
    DAILY = 'daily'
    WEEKLY = 'weekly'
    MONTHLY = 'monthly'
    YEARLY = 'yearly'

recurrence_pattern_enum = ENUM(
    *[pattern.value for pattern in RecurrencePattern], 
    name='recurrence_pattern_enum',
    schema='scarlet',
    create_type=True
)

class User(db.Model):
    __tablename__ = 'users'
    __table_args__ = (
        db.Index('idx_user_username', 'username'),
        db.Index('idx_user_email', 'email'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    password_hash = db.Column(db.String(256), nullable=False)
    phone_number = db.Column(db.String(25))
    email = db.Column(db.String(120), unique=True, nullable=True)
    timezone = db.Column(db.String(50), default='UTC', nullable=False)
    preferences = db.Column(JSONB, default={
        'work_hours': {'start': '09:00', 'end': '17:00'},
        'break_duration': 15,
        'pomodoro_cycle': 25,
        'preferred_energy_level': 7,
    })
    created_at = db.Column(db.DateTime(timezone=True), nullable=False, server_default=db.func.now())
    last_login = db.Column(db.DateTime(timezone=True), nullable=True)
    avatar_url            = db.Column(db.String, nullable=True)
    email_confirmed       = db.Column(db.Boolean, default=False, nullable=False)
    email_confirm_token   = db.Column(db.String(128), nullable=True)
    phone_confirmed       = db.Column(db.Boolean, default=False, nullable=False)
    phone_confirm_token   = db.Column(db.String(128), nullable=True)
    totp_secret           = db.Column(db.String(32), nullable=True)
    is_2fa_enabled        = db.Column(db.Boolean, default=False, nullable=False)

    # Relationships
    tasks = db.relationship('Task', backref='owner', lazy=True)
    behaviors = db.relationship('UserBehavior', backref='user', lazy=True)
    projects = db.relationship('Project', backref='creator', lazy=True)
    time_blocks = db.relationship('TimeBlock', backref='user', lazy='dynamic')

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash, password)
    
    def generate_email_token(self):
        token = pyotp.random_base32()
        self.email_confirm_token = token
        return token

    def generate_phone_token(self):
        token = pyotp.random_base32()
        self.phone_confirm_token = token
        return token

    def to_dict(self):
        return {
            'id': self.id,
            'username': self.username,
            'email': self.email,
            'timezone': self.timezone,
            'preferences': self.preferences,
            'created_at': self.created_at.isoformat(),
            'last_login': self.last_login.isoformat() if self.last_login else None,
        }

# association table for Task↔Tag
task_tags = db.Table(
    'task_tags',
    db.Column('task_id', db.Integer, db.ForeignKey(TASK_ID_FK), primary_key=True),
    db.Column('tag_id', db.Integer, db.ForeignKey('scarlet.tags.id'), primary_key=True),
    schema='scarlet'
)

class Task(db.Model):
    __tablename__ = 'tasks'
    __table_args__ = (
        CheckConstraint(
            "priority IN ('high', 'medium', 'low')", 
            name='valid_priority'),
        CheckConstraint(
            "energy_level BETWEEN 1 AND 10", 
            name='valid_energy_level'),
        CheckConstraint(
            "recurrence_pattern IN ('daily', 'weekly', 'monthly', 'yearly')",
            name='valid_recurrence_pattern'),
        db.Index('idx_task_user_id', 'user_id'),
        db.Index('idx_task_deadline', 'deadline'),
        db.Index('idx_task_priority', 'priority'),
        db.Index('ix_tasks_user_deadline', 'user_id', 'deadline'),
        db.Index('ix_tasks_priority_status', 'priority', 'is_completed'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(255), nullable=False)
    description = db.Column(db.Text, nullable=True)
    priority = db.Column(
        db.String(6),
        default=TaskPriority.MEDIUM.value,
        nullable=False,
        server_default=TaskPriority.MEDIUM.value
    )
    deadline = db.Column(db.DateTime(timezone=True), nullable=True,index=True)
    recurring = db.Column(db.Boolean, default=False, nullable=False)
    recurrence_pattern = db.Column(recurrence_pattern_enum, nullable=True)
    next_due_date = db.Column(db.DateTime(timezone=True), nullable=True, index=True)
    last_recurred_at = db.Column(db.DateTime(timezone=True), nullable=True)
    is_completed = db.Column(db.Boolean, default=False)
    completed_at = db.Column(db.DateTime(timezone=True), nullable=True)
    deadline_notified = db.Column(db.Boolean, nullable=False, default=False, server_default=db.text('false')) #prevents repeat reminders
    dependency_id = db.Column(db.Integer, db.ForeignKey(TASK_ID_FK), nullable=True)
    user_id = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=False)
    project_id = db.Column(db.Integer, db.ForeignKey(PROJECT_ID_FK), nullable=True)
    shared_with = db.Column(ARRAY(db.Integer), default=[], nullable=True)
    energy_level = db.Column(db.Integer, nullable=True)
    context = db.Column(db.String(100), nullable=True)
    deleted_at            = db.Column(db.DateTime(timezone=True), nullable=True, index=True)
    permanently_deleted   = db.Column(db.Boolean, nullable=False, server_default='false', default=False)

    # Relationships
    tags = db.relationship('Tag', secondary=task_tags, back_populates='tasks', lazy='selectin')
    subtasks = db.relationship('Subtask', backref='task', lazy=True)
    behaviors = db.relationship('TaskBehavior', backref='task', lazy=True)
    permissions = db.relationship('TaskPermission', backref='task', lazy=True)
    time_blocks = db.relationship('TimeBlock', backref='task', lazy='dynamic')

    def to_dict(self):
        return {
            'id': self.id,
            'title': self.title,
            'description': self.description,
            'priority': self.priority,
            'deadline': self.deadline.isoformat() if self.deadline else None,
            'recurring': self.recurring,
            'recurrence_pattern': self.recurrence_pattern,
            'next_due_date': self.next_due_date.isoformat() if self.next_due_date else None,
            'last_recurred_at': self.last_recurred_at.isoformat() if self.last_recurred_at else None,
            'is_completed': self.is_completed,
            'completed_at': self.completed_at.isoformat() if self.completed_at else None,
            'dependency_id': self.dependency_id,
            'user_id': self.user_id,
            'project_id': self.project_id,
            'shared_with': self.shared_with,
            'energy_level': self.energy_level,
            'context': self.context,
            'deleted_at': self.deleted_at.isoformat() if self.deleted_at else None,
            'permanently_deleted': self.permanently_deleted,
            'tags': [t.to_dict() for t in self.tags]
        }
    
    def next_occurrence(self):
        """Calculate the next occurrence date based on recurrence pattern."""
        if not self.recurring or not self.recurrence_pattern or not self.deadline:
            return None
            
        now = datetime.now(timezone.utc)
        base = self.next_due_date or self.deadline
        
        # If base is in the past, start from now
        if base < now:
            base = now
        
        # Parse the recurrence pattern
        pattern = self.recurrence_pattern.lower()
        
        if 'daily' in pattern:
            return base + timedelta(days=1)
        elif 'weekly' in pattern:
            return base + timedelta(days=7)
        elif 'biweekly' in pattern:
            return base + timedelta(days=14)
        elif 'monthly' in pattern:
            # Add one month to the base date
            next_month = base.month + 1
            next_year = base.year
            if next_month > 12:
                next_month = 1
                next_year += 1
            
            # Attempt to create the same day in the next month
            try:
                return base.replace(year=next_year, month=next_month)
            except ValueError:
                # Handle cases like Jan 31 -> Feb 28/29
                last_day = calendar.monthrange(next_year, next_month)[1]
                return base.replace(year=next_year, month=next_month, day=min(base.day, last_day))
        elif 'yearly' in pattern:
            return base.replace(year=base.year + 1)
        else:
            # Default fallback - add a week
            return base + timedelta(days=7)

    @classmethod
    def due_for_recurrence(cls, now_utc):
            """
            Get tasks due for recurrence using database query
            """
            return cls.query.filter(
                cls.recurring == True,
                cls.next_due_date <= now_utc,
                cls.is_completed == False
            ).all()

@event.listens_for(Task, 'before_update')
def set_completed_at_on_status_change(mapper, connection, target):
    """
    Automatically updates completed_at when is_completed changes.
    Handled at the model level for all ORM updates.
    """
    try:
        # Ensure we're acting on a Task instance
        if not isinstance(target, Task):
            return

        # Inspect the 'is_completed' field for changes
        hist = inspect(target).attrs.is_completed.history

        # Transition: False → True
        if hist.has_changes() and hist.deleted == [False] and hist.added == [True]:
            target.completed_at = datetime.now(timezone.utc)
        
        # Transition: True → False
        elif hist.has_changes() and hist.deleted == [True] and hist.added == [False]:
            target.completed_at = None

    except Exception as e:
        logger.error(
            f"Error updating completed_at for Task {target.id}: {str(e)}",
            exc_info=True
        )
        raise  # Re-raise to abort the transaction

class Subtask(db.Model):
    __tablename__ = 'subtasks'
    __table_args__ = (
        db.Index('idx_subtask_task_id', 'task_id'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(255), nullable=False)
    description = db.Column(db.Text, nullable=True)
    is_completed = db.Column(db.Boolean, default=False)
    task_id = db.Column(db.Integer, db.ForeignKey(TASK_ID_FK), nullable=False)
    order = db.Column(db.Integer, nullable=False)
    deadline = db.Column(db.DateTime, nullable=True)

    def to_dict(self):
        return {
            'id': self.id,
            'title': self.title,
            'description': self.description,
            'is_completed': self.is_completed,
            'task_id': self.task_id,
            'order': self.order,
            'deadline': self.deadline.isoformat() if self.deadline else None,
        }

class Tag(db.Model):
    __tablename__ = 'tags'
    __table_args__ = ({'schema':'scarlet'},)

    id         = db.Column(db.Integer, primary_key=True)
    user_id    = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=False, index=True)
    name       = db.Column(db.String(50), nullable=False)
    created_at = db.Column(db.DateTime(timezone=True), server_default=func.now(), nullable=False)

    # backref from Task
    tasks = db.relationship(
        'Task',
        secondary=task_tags,
        back_populates='tags',
        lazy='dynamic'
    )

    __table_args__ = (
        CheckConstraint("char_length(name) > 0", name='valid_tag_name'),
        db.Index('idx_tag_user_name', 'user_id', 'name', unique=True),
        {'schema': 'scarlet'}
    )

    def to_dict(self):
        return {'id': self.id, 'name': self.name}

class UserBehavior(db.Model):
    __tablename__ = 'user_behaviors'
    __table_args__ = (
        CheckConstraint(
            "energy_level BETWEEN 1 AND 10", 
            name='valid_behavior_energy_level'),
        db.Index('idx_user_behavior_user_id', 'user_id'),
        db.Index('idx_user_behavior_timestamp', 'timestamp'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=False)
    timestamp = db.Column(db.DateTime, default=datetime.now)
    activity_type = db.Column(db.String(50), nullable=False)
    task_id = db.Column(db.Integer, db.ForeignKey(TASK_ID_FK), nullable=True)
    energy_level = db.Column(db.Integer, nullable=True)
    mood = db.Column(db.String(50), nullable=True)

    def to_dict(self):
        return {
            'id': self.id,
            'user_id': self.user_id,
            'timestamp': self.timestamp.isoformat(),
            'activity_type': self.activity_type,
            'task_id': self.task_id,
            'energy_level': self.energy_level,
            'mood': self.mood,
        }

class TaskBehavior(db.Model):
    __tablename__ = 'task_behaviors'
    __table_args__ = (
        db.Index('idx_task_behavior_task_id', 'task_id'),
        db.Index('idx_task_behavior_timestamp', 'timestamp'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    task_id = db.Column(db.Integer, db.ForeignKey(TASK_ID_FK), nullable=False)
    timestamp = db.Column(db.DateTime, default=datetime.now)
    status = db.Column(db.String(50), nullable=False)
    changes = db.Column(JSONB, nullable=True)

    def to_dict(self):
        return {
            'id': self.id,
            'task_id': self.task_id,
            'timestamp': self.timestamp.isoformat(),
            'status': self.status,
            'changes': self.changes,
        }

class Project(db.Model):
    __tablename__ = 'projects'
    __table_args__ = (
        db.Index('idx_project_created_by', 'created_by'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)
    description = db.Column(db.Text, nullable=True)
    created_by = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now)
    tasks = db.relationship('Task', backref='project', lazy=True)

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'description': self.description,
            'created_by': self.created_by,
            'created_at': self.created_at.isoformat(),
        }

class TaskPermission(db.Model):
    __tablename__ = 'task_permissions'
    __table_args__ = (
        db.Index('idx_task_permission_task_id', 'task_id'),
        db.Index('idx_task_permission_user_id', 'user_id'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    task_id = db.Column(db.Integer, db.ForeignKey(TASK_ID_FK), nullable=False)
    user_id = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=False)
    permission_level = db.Column(db.String(50), default='view', nullable=False)

    def to_dict(self):
        return {
            'id': self.id,
            'task_id': self.task_id,
            'user_id': self.user_id,
            'permission_level': self.permission_level,
        }

class TimeBlock(db.Model):
    __tablename__ = 'time_blocks'
    __table_args__ = (
        db.Index('idx_timeblock_user_start_end', 'user_id', 'start', 'end'),
        {'schema': 'scarlet'}
    )

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=False)
    task_id = db.Column(db.Integer, db.ForeignKey(TASK_ID_FK), nullable=True)
    start = db.Column(db.DateTime(timezone=True), nullable=False)
    end   = db.Column(db.DateTime(timezone=True), nullable=False)
    created_at = db.Column(db.DateTime(timezone=True), nullable=False, server_default=db.func.now())

    def to_dict(self):
        return {
            'id': self.id,
            'user_id': self.user_id,
            'task_id': self.task_id,
            'start': self.start.isoformat(),
            'end': self.end.isoformat(),
            'created_at': self.created_at.isoformat(),
            'task': self.task.to_dict() if self.task else None,
        }

class Survey(db.Model):
    __tablename__ = 'surveys'
    __table_args__ = (
        CheckConstraint(
            "profile->>'energy_level' ~ '^[1-9]$|^10$'", 
            name='valid_survey_energy_level'),
        {'schema': 'scarlet'}
    )
    
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey(USER_ID_FK), nullable=True)
    profile = db.Column(JSONB, nullable=False, default=dict)
    task_preferences = db.Column(JSONB, nullable=False, default=dict)
    scheduling = db.Column(JSONB, nullable=False, default=dict)
    analytics = db.Column(JSONB, nullable=False, default=dict)
    integrations = db.Column(JSONB, nullable=False, default=dict)
    created_at = db.Column(db.DateTime, default=datetime.now)

class TokenBlocklist(db.Model):
    __tablename__ = 'tokenblocklist'
    __table_args__ = {'schema': 'scarlet'} 

    id = db.Column(db.Integer, primary_key=True)
    jti = db.Column(db.String(36), nullable=False, index=True)
    token_type = db.Column(db.String(10), nullable=False)
    user_id = db.Column(db.String(50), nullable=False)
    revoked_at = db.Column(db.DateTime, nullable=False, default=lambda: datetime.now(timezone.utc))