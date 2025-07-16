from flask import Blueprint, request, jsonify, make_response
from flask_jwt_extended import (
    create_access_token,
    create_refresh_token,
    jwt_required,
    get_jwt_identity,
    decode_token
)
from models import User, TokenBlocklist
from datetime import timedelta, datetime, timezone
from extensions import db, limiter

auth_bp = Blueprint('auth', __name__)

INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error"

@auth_bp.route('/register', methods=['POST'])
def register():
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'Request body must be JSON'}), 400

        username = data.get('username', '').strip()
        password = data.get('password', '').strip()

        if not username:
            return jsonify({'error': 'Username is required'}), 400
        if not password:
            return jsonify({'error': 'Password is required'}), 400

        if User.query.filter(db.func.lower(User.username) == username.lower()).first():
            return jsonify({'error': 'Username already exists'}), 400

        new_user = User(username=username)
        new_user.set_password(password)
        db.session.add(new_user)
        db.session.commit()

        return jsonify({'message': 'User registered successfully'}), 201
    except Exception as e:
        return jsonify({'error': INTERNAL_SERVER_ERROR_MESSAGE, 'message': str(e)}), 500

@auth_bp.route('/login', methods=['POST'])
def login():
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'Request body must be JSON'}), 400

        username = data.get('username', '').strip()
        password = data.get('password', '').strip()

        if not username or not password:
            return jsonify({'error': 'Username and password are required'}), 400

        user = User.query.filter(db.func.lower(User.username) == username.lower()).first()
        
        if not user or not user.check_password(password):
            return jsonify({'error': 'Invalid username or password'}), 401

        user_id = str(user.id)
        access_token = create_access_token(identity=user_id, expires_delta=timedelta(minutes=15))
        refresh_token = create_refresh_token(identity=user_id)

        response = jsonify({'access_token': access_token})
        # Set refresh token cookie with broader path and Lax SameSite
        response.set_cookie(
            'refresh_token', 
            refresh_token,
            httponly=True,
            secure=False,  # Set to True in production (requires HTTPS)
            samesite='Lax',  # Less restrictive, allows cross-site GET requests
            path='/',        # Ensure it's accessible for the entire site
            max_age=30 * 24 * 60 * 60  # 30 days
        )
        return response

    except Exception as e:
        db.session.rollback()
        return jsonify({
            'error': 'Internal server error',
            'message': f'Authentication process failed: {str(e)}'
        }), 500


def add_token_to_database(token, identity):
    # Decode the token using flask_jwt_extended's decode_token function
    decoded_token = decode_token(token)
    jti = decoded_token.get('jti')
    token_type = decoded_token.get('type')

    db.session.add(TokenBlocklist(
        jti=jti,
        token_type=token_type,
        user_id=identity,
        revoked_at=datetime.now(timezone.utc)
    ))
    db.session.commit()


@auth_bp.route('/logout', methods=['DELETE'])
def logout():
    try:
        # Get the refresh token from the cookie
        refresh_token = request.cookies.get('refresh_token')
        if not refresh_token:
            return jsonify({'error': 'Refresh token missing'}), 401

        # Decode the refresh token to get user identity
        decoded_token = decode_token(refresh_token)
        identity = decoded_token.get('sub')

        # Add the refresh token to the blocklist
        add_token_to_database(refresh_token, identity)

        # Clear the refresh token cookie
        response = jsonify({'message': 'Successfully logged out'})
        response.set_cookie(
            'refresh_token',
            '',
            expires=0,
            httponly=True,
            secure=False,  # Set to True in production
            samesite='Strict'
        )
        return response

    except Exception as e:
        return jsonify({
            'error': 'Logout failed',
            'message': str(e)
        }), 500


@auth_bp.route('/refresh', methods=['POST'])
@limiter.exempt
def refresh():
    print(f"Refresh request cookies: {request.cookies}")
    try:
        # Get the refresh token from the cookie
        refresh_token = request.cookies.get('refresh_token')
        if not refresh_token:
            return jsonify({ "error": "Refresh token missing", "code": "REFRESH_FAILED" }), 401

        # Manually decode and validate the token rather than using the decorator
        try:
            decoded_token = decode_token(refresh_token)
            if decoded_token['type'] != 'refresh':
                return jsonify({"error": "Invalid token type", "code": "REFRESH_FAILED"}), 401
                
            # Check if token is in blocklist
            jti = decoded_token.get('jti')
            if TokenBlocklist.query.filter_by(jti=jti).first():
                return jsonify({"error": "Token revoked", "code": "REFRESH_FAILED"}), 401
                
            identity = decoded_token['sub']
        except Exception as e:
            return jsonify({"error": "Invalid token", "code": "REFRESH_FAILED", "message": str(e)}), 401

        new_access_token = create_access_token(identity=identity)
        return jsonify({'access_token': new_access_token}), 200

    except Exception as e:
        db.session.rollback()
        return jsonify({
            'error': 'Token refresh failed',
            'code': 'REFRESH_FAILED',
            'message': str(e)
        }), 401

@auth_bp.route('/mood', methods=['POST'])
@jwt_required()
def track_mood():
    try:
        data = request.get_json()
        mood = data.get('mood', '').strip()
        if not mood:
            return jsonify({'error': 'Mood is required'}), 400

        user_id = get_jwt_identity()
        # Save mood to the database or perform analytics
        print(f"User {user_id} tracked mood: {mood}")

        return jsonify({'message': 'Mood tracked successfully'}), 200
    except Exception as e:
        return jsonify({'error': INTERNAL_SERVER_ERROR_MESSAGE, 'message': str(e)}), 500