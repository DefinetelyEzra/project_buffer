import os
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import Mail

SENDGRID_API_KEY = os.getenv('SENDGRID_API_KEY')
FROM_EMAIL = os.getenv('NOTIFY_FROM_EMAIL', 'no-reply@scarlet.ai')

def send_email(to_email: str, subject: str, content: str):
    if not SENDGRID_API_KEY:
        raise RuntimeError("SendGrid API key not configured")
    msg = Mail(
        from_email=FROM_EMAIL,
        to_emails=to_email,
        subject=subject,
        plain_text_content=content
    )
    sg = SendGridAPIClient(SENDGRID_API_KEY)
    sg.send(msg)

def send_confirmation_link(user, field: str, token: str):
    # field is "email" or "phone"
    target = getattr(user, field)
    link = f"{os.getenv('FRONTEND_URL')}/confirm-{field}?token={token}"
    subj = f"Confirm your {field}"
    body = f"Hello {user.username}, click to confirm your {field}: {link}"
    send_email(target, subj, body)
