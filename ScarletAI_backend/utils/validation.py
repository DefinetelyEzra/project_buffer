from dateutil.parser import parse
from werkzeug.exceptions import BadRequest
import pytz, imghdr
from models import Task, TaskPriority
import datetime
from dateutil.parser import parse
from dateutil.rrule import rrule
from cerberus import Validator
import pyotp

INVALID_DATETIME_MESSAGE = "Invalid datetime format"
INVALID_IMAGE_MESSAGE = "Invalid image file"
INVALID_TOTP_MESSAGE = "Invalid or expired TOTP code"

class CustomValidator(Validator):
    def __init__(self, *args, **kwargs):
        coercers = kwargs.pop('coercers', {})
        coercers['datetime'] = self._coerce_datetime
        coercers['integer']  = self._coerce_int
        super().__init__(*args, coercers=coercers, **kwargs)
        
    def _coerce_datetime(self, value):
        try:
            return parse(value).astimezone(pytz.utc)
        except (ValueError, TypeError, OverflowError):
            raise ValueError(INVALID_DATETIME_MESSAGE)
    
    def _coerce_int(self, value):
        try:
            return int(value)
        except (ValueError):
            raise ValueError("Must be an integer")

validator = CustomValidator(require_all=False, allow_unknown=True)

def validate_recurrence_pattern(self, recurrence_pattern, field, value):
    """
    Custom Cerberus rule:
    {'recurrence_pattern': {'recurrence_pattern': True}}
    """
    if value: 
        try:
            rrule.from_rrule(value)  # Use rrule.from_rrule to parse
        except Exception:
            self._error(field, "Invalid RFC‑5545 recurrence_pattern")

def validate_task_data(data):
    data.pop('tags', None)  # Remove 'tags' if it exists 

    schema = {
        'title': {'type': 'string', 'required': True},
        'priority': {
            'type': 'string',
            'allowed': [p.value for p in TaskPriority],
            'default': TaskPriority.MEDIUM.value
        },
        'deadline': {
            'type': 'datetime',
            'coerce': 'parse_datetime',
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
        'recurrence_pattern': {
            'type': 'string',
            'nullable': True,
            'maxlength': 200,
            'recurrence_pattern': True
        }
    }

    if not validator.validate(data, schema):
        raise BadRequest(description=validator.errors)

def validate_datetime(value):
    try:
        return parse(value).astimezone(pytz.utc)
    except (ValueError, TypeError):
        raise BadRequest(INVALID_DATETIME_MESSAGE)

def validate_task_dependencies(dependency_id):
    task = Task.query.get(dependency_id)
    if not task:
        raise BadRequest("Invalid task dependency")
    if task.is_completed:
        raise BadRequest("Cannot depend on completed task")

def parse_datetime(value):
    # Return None if value is empty
    if not value or value == "":
        return None
    try:
        return parse(value).astimezone(pytz.utc)
    except (ValueError, TypeError, OverflowError):
        raise ValueError(INVALID_DATETIME_MESSAGE)
    
def validate_image_file(file_storage):
    header = file_storage.read(512)
    file_storage.seek(0)
    kind = imghdr.what(None, header)
    if kind not in ('jpeg','png','gif'):
        raise BadRequest(INVALID_IMAGE_MESSAGE)

def validate_totp(secret, code):
    totp = pyotp.TOTP(secret)
    if not totp.verify(code, valid_window=1):
        raise BadRequest(INVALID_TOTP_MESSAGE)