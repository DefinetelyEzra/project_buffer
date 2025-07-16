import os
import logging
from datetime import datetime, timedelta

import pytz
from dateutil import tz
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import Mail
from twilio.rest import Client

from extensions import db
from models import Task, User

logger = logging.getLogger(__name__)

# Pull credentials from env
SENDGRID_API_KEY       = os.getenv('SENDGRID_API_KEY')
NOTIFY_FROM_EMAIL      = os.getenv('NOTIFY_FROM_EMAIL', 'no-reply@scarlet.ai')
TWILIO_ACCOUNT_SID     = os.getenv('TWILIO_ACCOUNT_SID')
TWILIO_AUTH_TOKEN      = os.getenv('TWILIO_AUTH_TOKEN')
TWILIO_PHONE_NUMBER    = os.getenv('TWILIO_PHONE_NUMBER')

def send_deadline_email(user: User, task: Task):
    if not SENDGRID_API_KEY or not user.email:
        logger.warning(f"Skipping email (no key or no email) for user {user.id}")
        return

    try:
        sg = SendGridAPIClient(SENDGRID_API_KEY)
        # Convert to user local time
        user_tz = tz.gettz(user.timezone or 'UTC')
        local_due = task.deadline.astimezone(user_tz).strftime("%Y-%m-%d %H:%M %Z")
        subject = f"Reminder: Task “{task.title}” due soon"
        content = (
            f"Hello {user.username},\n\n"
            f"Your task “{task.title}” is due at {local_due}.\n\n"
            "— ScarletAI"
        )

        msg = Mail(
            from_email=NOTIFY_FROM_EMAIL,
            to_emails=user.email,
            subject=subject,
            plain_text_content=content
        )
        sg.send(msg)
        logger.info(f"Email reminder sent to {user.email} for task {task.id}")

    except Exception as e:
        logger.error(f"Error sending email to {user.email}: {e}", exc_info=True)


def send_deadline_sms(user: User, task: Task):
    if not TWILIO_ACCOUNT_SID or not user.phone_number:
        logger.debug(f"Skipping SMS (no Twilio or no phone) for user {user.id}")
        return

    try:
        client = Client(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
        user_tz   = tz.gettz(user.timezone or 'UTC')
        local_due = task.deadline.astimezone(user_tz).strftime("%Y-%m-%d %H:%M %Z")
        body = f"Reminder: “{task.title}” due at {local_due}"
        client.messages.create(
            body=body,
            from_=TWILIO_PHONE_NUMBER,
            to=user.phone_number
        )
        logger.info(f"SMS reminder sent to {user.phone_number} for task {task.id}")

    except Exception as e:
        logger.error(f"Error sending SMS to user {user.id}: {e}", exc_info=True)


def check_upcoming_deadlines():
    """
    Find all incomplete tasks whose deadline is 1h from now,
    have not yet been flagged, send notifications & mark them.
    """
    now = datetime.now(pytz.utc)
    window = now + timedelta(hours=1)

    try:
        tasks = Task.query.filter(
            Task.deadline.isnot(None),
            Task.deadline > now,
            Task.deadline <= window,
            Task.is_completed == False,
            Task.deadline_notified == False
        ).all()

        for task in tasks:
            user = User.query.get(task.user_id)
            if not user:
                continue

            send_deadline_email(user, task)
            send_deadline_sms(user, task)

            # mark so we don’t spam
            task.deadline_notified = True

        if tasks:
            db.session.commit()

    except Exception as e:
        db.session.rollback()
        logger.error(f"Notification job failed: {e}", exc_info=True)