from datetime import datetime, timedelta
import pytz
from models import User

def convert_to_utc(user_time, user_timezone):
    """Convert user local time to UTC"""
    tz = pytz.timezone(user_timezone)
    local_dt = tz.localize(user_time)
    return local_dt.astimezone(pytz.utc).replace(tzinfo=None)

def get_user_timezone(user_id):
    """Get user's timezone from database"""
    user = User.query.get(user_id)
    return user.timezone if user else 'UTC'

def format_time_range(start, end, timezone):
    """Generate time blocks considering user preferences"""
    return {
        'start': start.astimezone(timezone).isoformat(),
        'end': end.astimezone(timezone).isoformat()
    }