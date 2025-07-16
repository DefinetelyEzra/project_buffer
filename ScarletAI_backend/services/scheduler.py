import os
import logging
import pytz
from flask import jsonify
from datetime import datetime, timedelta
from dateutil.relativedelta import relativedelta
from extensions import db
from models import Task, User
from sqlalchemy.exc import SQLAlchemyError
from apscheduler.schedulers.background import BackgroundScheduler
from services.notifications import check_upcoming_deadlines

logger = logging.getLogger(__name__)

def generate_recurring_tasks():
    now = datetime.now(pytz.utc)
    try:
        all_recurring = Task.due_for_recurrence(now)
        new_tasks = []
        updated_parents = []

        for task in all_recurring:
            nxt = task.next_occurrence()
            if nxt and nxt <= now:
                # schedule the next parent run
                task.last_recurred_at = now
                task.next_due_date     = nxt
                updated_parents.append(task)

                # clone for the upcoming deadline
                next_after = {
                'daily':   timedelta(days=1),
                'weekly':  timedelta(weeks=1),
                'monthly': relativedelta(months=+1),
                'yearly':  relativedelta(years=+1)
                }[task.recurrence_pattern]
                clone = Task(
                    title=task.title,
                    description=task.description,
                    priority=task.priority,
                    deadline=nxt,
                    recurring=task.recurring,
                    recurrence_pattern=task.recurrence_pattern,
                    user_id=task.user_id,
                    project_id=task.project_id,
                    tags=task.tags,
                    dependency_id=task.dependency_id,
                    context=task.context,
                    next_due_date=nxt + next_after
                )
                new_tasks.append(clone)

        if updated_parents or new_tasks:
            # Add clones
            for c in new_tasks:
                db.session.add(c)
            # Parents are already in the session, so their in‑memory changes will flush
            db.session.commit()

            logger.info(
                f"Created {len(new_tasks)} new tasks and updated {len(updated_parents)} "
                f"parent tasks at {now}"
            )

    except SQLAlchemyError:
        db.session.rollback()
        logger.error("DB error generating recurring tasks", exc_info=True)
    except Exception:
        logger.error("Unexpected error in generate_recurring_tasks()", exc_info=True)

def start_scheduler():
    if not hasattr(start_scheduler, "initialized"):  
        sched = BackgroundScheduler(timezone=pytz.utc)
        sched.add_job(
            'services.scheduler:generate_recurring_tasks',
            'interval', hours=1, id='recurrence_job'
        )
        sched.add_job(
            'services.notifications:check_upcoming_deadlines',
            'interval', minutes=30, id='deadline_reminder_job'
        )
        sched.start()
        logger.info("Recurring‑task scheduler started.")
        start_scheduler.initialized = True 

def suggest_schedule(goal, deadline, duration, preferences):
    return {
        "goal": goal,
        "deadline": deadline,
        "duration": duration,
        "preferences": preferences,
        "blocks": [{
            "start": datetime.now(pytz.utc).isoformat(),
            "end": (datetime.now(pytz.utc) + timedelta(minutes=duration or 30)).isoformat(),
            "task": goal or "Unnamed"
        }]
    }

def manual_trigger():
    try:
        generate_recurring_tasks()
        return jsonify(message="Recurrence job triggered"), 200
    except Exception as error:  # Renamed for consistency
        logger.error("Manual trigger failed: %s", str(error), exc_info=True)
        return jsonify(error="Trigger failed", message=str(error)), 500