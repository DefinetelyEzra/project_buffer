from flask import Blueprint
from services.scheduler import manual_trigger  # Import service logic

scheduler_bp = Blueprint('scheduler', __name__)

@scheduler_bp.route('/recurrence/trigger', methods=['POST'])
def manual_trigger_route():
    return manual_trigger()  # Delegate to service