from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import jwt_required, get_jwt_identity,  get_jwt, create_access_token
from extensions import db, avatars
from models import User
from utils.validation import validate_datetime, validate_image_file, validate_totp
from werkzeug.security import generate_password_hash
from werkzeug.utils import secure_filename
from sqlalchemy.exc import IntegrityError
from services.email import send_confirmation_link
import pyotp
import datetime

ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}
user_bp = Blueprint('user', __name__)

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

@user_bp.route('/me', methods=['GET'])
@jwt_required()
def get_profile():
    user_id = get_jwt_identity()
    user = User.query.get_or_404(user_id)
    return jsonify(user.to_dict()), 200

@user_bp.route('/me', methods=['PATCH'])
@jwt_required()
def update_profile():
    user_id = get_jwt_identity()
    data = request.get_json() or {}
    user = User.query.get_or_404(user_id)

    # Username
    username = data.get('username')
    if username is not None:
        user.username = username.strip()

    # Allowed fields
    email   = data.get('email')
    phone   = data.get('phone_number')
    tz      = data.get('timezone')
    prefs   = data.get('preferences', {})

    if email is not None:
        user.email = email.strip().lower()
    if phone is not None:
        user.phone_number = phone.strip()
    if tz is not None:
        # (you may want to validate against pytz.common_timezones)
        user.timezone = tz

    # Merge JSONB preferences
    if prefs:
        user.preferences = {**user.preferences, **prefs}

    try:
        db.session.commit()
        return jsonify(user.to_dict()), 200
    except IntegrityError as e:
        db.session.rollback()
        return jsonify(error="Username or email already in use"), 400
    except Exception as e:
        db.session.rollback()
        return jsonify(error="Update failed", message=str(e)), 500

@user_bp.route('/me/password', methods=['POST'])
@jwt_required()
def change_password():
    data = request.get_json() or {}
    current = data.get('current_password','')
    new      = data.get('new_password','')
    confirm  = data.get('confirm_password','')

    if not new or new != confirm:
        return jsonify(error="New passwords must match"), 400

    user = User.query.get_or_404(get_jwt_identity())
    if not user.check_password(current):
        return jsonify(error="Current password incorrect"), 401

    # (strength rules to be enforced here)
    user.set_password(new)
    db.session.commit()
    return jsonify(message="Password updated"), 200

@user_bp.route('/me/avatar', methods=['POST'])
@jwt_required()
def upload_avatar():
    user_id = get_jwt_identity()
    user = User.query.get(user_id)

    if 'avatar' not in request.files:
        return jsonify({'error': 'No file part'}), 400

    file = request.files['avatar']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    if file and allowed_file(file.filename):
        # Use a simpler filename to avoid path issues
        # Fix: Import datetime and use datetime.now().timestamp() instead of time.time()
        import datetime
        timestamp = int(datetime.datetime.now().timestamp())
        filename = secure_filename(f"user_{user_id}_{timestamp}.{file.filename.rsplit('.', 1)[1].lower()}")
        
        try:
            # Use the avatars UploadSet to save the file
            filename = avatars.save(file, name=filename)
            
            # Get the URL
            avatar_url = avatars.url(filename)
            
            # Remove the domain prefix if it exists (to make it relative)
            if avatar_url.startswith('http'):
                avatar_url = '/' + '/'.join(avatar_url.split('/', 3)[3:])
            
            user.avatar_url = avatar_url
            db.session.commit()
            
            return jsonify({'message': 'Avatar uploaded successfully', 'avatar_url': user.avatar_url}), 200
        except Exception as e:
            return jsonify({'error': f'Upload failed: {str(e)}'}), 500

    return jsonify({'error': 'Invalid file type'}), 400

@user_bp.route('/me/email/verify', methods=['POST'])
@jwt_required()
def request_email_verify():
    data = request.get_json() or {}
    new = data.get('email')
    if not new:
        return jsonify(error="Email required"),400
    user = User.query.get_or_404(get_jwt_identity())
    user.email = new.lower()
    token = user.generate_email_token()
    send_confirmation_link(user,'email',token)
    db.session.commit()
    return jsonify(message="Confirmation sent"),200

@user_bp.route('/me/email/confirm', methods=['POST'])
def confirm_email():
    data = request.get_json() or {}
    token = data.get('token')
    user = User.query.filter_by(email_confirm_token=token).first_or_404()
    user.email_confirmed = True
    user.email_confirm_token = None
    db.session.commit()
    return jsonify(message="Email confirmed"),200

@user_bp.route('/me/phone/verify', methods=['POST'])
@jwt_required()
def request_phone_verify():
    data = request.get_json() or {}
    new = data.get('phone_number')
    if not new:
        return jsonify(error="Phone required"),400
    user = User.query.get_or_404(get_jwt_identity())
    user.phone_number = new
    token = user.generate_phone_token()
    send_confirmation_link(user,'phone',token)
    db.session.commit()
    return jsonify(message="Confirmation sent"),200

@user_bp.route('/me/phone/confirm', methods=['POST'])
def confirm_phone():
    data = request.get_json() or {}
    token = data.get('token')
    user = User.query.filter_by(phone_confirm_token=token).first_or_404()
    user.phone_confirmed = True
    user.phone_confirm_token = None
    db.session.commit()
    return jsonify(message="Phone confirmed"),200

@user_bp.route('/me/2fa/setup', methods=['POST'])
@jwt_required()
def setup_2fa():
    user = User.query.get_or_404(get_jwt_identity())
    secret = pyotp.random_base32()
    user.totp_secret = secret
    db.session.commit()
    uri = pyotp.totp.TOTP(secret).provisioning_uri(user.username, issuer_name="ScarletAI")
    return jsonify(otp_uri=uri),200

@user_bp.route('/me/2fa/verify', methods=['POST'])
@jwt_required()
def verify_2fa():
    data = request.get_json() or {}
    code = data.get('code')
    user = User.query.get_or_404(get_jwt_identity())
    validate_totp(user.totp_secret, code)
    user.is_2fa_enabled = True
    db.session.commit()
    return jsonify(message="2FA enabled"),200

@user_bp.route('/me/2fa', methods=['DELETE'])
@jwt_required()
def disable_2fa():
    user = User.query.get_or_404(get_jwt_identity())
    user.is_2fa_enabled = False
    user.totp_secret = None
    db.session.commit()
    return jsonify(message="2FA disabled"),200