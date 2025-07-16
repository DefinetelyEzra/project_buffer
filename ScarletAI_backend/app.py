import os
import logging

from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from flask_jwt_extended import JWTManager, jwt_required, get_jwt_identity, verify_jwt_in_request
from flask_migrate import Migrate
from flask_socketio import SocketIO
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from flask_cors import CORS
from werkzeug.middleware.proxy_fix import ProxyFix
from dotenv import load_dotenv

# local imports
from extensions import db, jwt, migrate, limiter, init_uploads
from models import Survey, User
from auth import auth_bp
from routes.user import user_bp
from routes.tasks import tasks_bp 
from routes.tag_routes import tag_bp
from routes.scheduler_routes import scheduler_bp
import services.scheduler as scheduler_service          
from utils.error_handlers import register_error_handlers

load_dotenv()
logger = logging.getLogger(__name__)

# Define constants
FRONTEND_URL = "http://localhost:3000"
AVATAR_FOLDER = 'static/uploads/avatars'

def create_app():
    app = Flask(__name__)
    app.url_map.strict_slashes = False  
    app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1)
    
    # Configure avatar folder
    app.config['AVATAR_FOLDER'] = AVATAR_FOLDER
    os.makedirs(AVATAR_FOLDER, exist_ok=True)
    
    configure_cors(app)
    check_required_env_vars()
    app.config.update(get_flask_config())
    initialize_extensions(app)
    register_routes(app)
    register_error_handlers(app)
    
    # Define OPTIONS route within the app context
    @app.route('/api/users/me/avatar', methods=['OPTIONS'])
    def avatar_options():
        response = app.make_default_options_response()
        response.headers['Access-Control-Allow-Origin'] = FRONTEND_URL
        response.headers['Access-Control-Allow-Methods'] = 'POST, OPTIONS'
        response.headers['Access-Control-Allow-Headers'] = 'Content-Type, Authorization'
        response.headers['Access-Control-Allow-Credentials'] = 'true'
        return response

    return app, SocketIO(app, cors_allowed_origins="*")

# Helper functions
def configure_cors(app):
    CORS(app, 
        origins=[FRONTEND_URL], 
        supports_credentials=True,  # Essential for cookies
        allow_headers=["Content-Type", "Authorization"],
        methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        expose_headers=["Content-Type", "Authorization"])

def check_required_env_vars():
    required_vars = [
        'DATABASE_USER', 'DATABASE_PASSWORD', 'DATABASE_HOST',
        'DATABASE_PORT', 'DATABASE_NAME', 'JWT_SECRET_KEY'
    ]
    for var in required_vars:
        if not os.getenv(var):
            raise EnvironmentError(f"Missing {var}")

def get_flask_config():
    return {
        'SQLALCHEMY_DATABASE_URI': build_db_uri(),
        'SQLALCHEMY_TRACK_MODIFICATIONS': False,
        'SQLALCHEMY_ECHO': os.getenv('FLASK_ENV') == 'development',
        'JWT_SECRET_KEY': os.getenv('JWT_SECRET_KEY'),
        'JWT_TOKEN_LOCATION': ['headers', 'cookies'],
        'JWT_COOKIE_SECURE': True,
        'JWT_COOKIE_SAMESITE': 'none', #change to strick in production
        'JWT_COOKIE_CSRF_PROTECT': False,
        'JWT_ACCESS_COOKIE_PATH': '/api/',
        'JWT_REFRESH_COOKIE_PATH': '/auth/refresh',
        'JWT_COOKIE_DOMAIN': None, #explicitly stated as none for localhost
        'RATELIMIT_STORAGE_URI': os.getenv('RATELIMIT_STORAGE_URI', 'memory://'),
        'RATELIMIT_HEADERS_ENABLED': True,
    }

def build_db_uri():
    return (
        f"postgresql://{os.getenv('DATABASE_USER')}:{os.getenv('DATABASE_PASSWORD')}"
        f"@{os.getenv('DATABASE_HOST')}:{os.getenv('DATABASE_PORT')}"
        f"/{os.getenv('DATABASE_NAME')}"
    )

def initialize_extensions(app):
    db.init_app(app)
    jwt.init_app(app)
    scheduler_service.start_scheduler()
    migrate.init_app(app, db)
    limiter.init_app(app)
    # must configure Flask-Uploads before you call avatars.save(...)
    init_uploads(app)

def rate_limit_key():
    try:
        verify_jwt_in_request(optional=True)
        return str(get_jwt_identity()) or get_remote_address()
    except Exception:
        return get_remote_address()

def register_routes(app):
    app.register_blueprint(auth_bp, url_prefix='/auth')
    app.register_blueprint(user_bp, url_prefix='/api/users')
    app.register_blueprint(tasks_bp, url_prefix='/api/tasks')
    app.register_blueprint(tag_bp, url_prefix='/api/tags')
    app.register_blueprint(scheduler_bp, url_prefix='/api/scheduler')

    @app.route('/')
    def home():
        return jsonify({"message": "Welcome to ScarletAI API", "status": "operational"})

    @app.route('/schedule', methods=['POST'])
    @limiter.limit("10/minute")
    def schedule():
        return handle_schedule_request()

    @app.route('/survey', methods=['POST'])
    @jwt_required(optional=True)
    def save_survey():
        return handle_survey_submission()

def handle_schedule_request():
    payload = request.get_json() or {}
    try:
        plan = scheduler_service.suggest_schedule(
            goal=payload.get('goal'),
            deadline=payload.get('deadline'),
            duration=payload.get('duration'),
            preferences=payload.get('preferences', {})
        )
        return jsonify(plan), 200
    except Exception as e:
        logger.error("Scheduling error", exc_info=True)
        return jsonify(error="Scheduling failed", message=str(e)), 400

def handle_survey_submission():
    data = request.get_json() or {}
    user_id = get_jwt_identity()
    try:
        survey = create_survey(user_id, data)
        db.session.add(survey)
        db.session.commit()
        return jsonify(message="Saved", survey_id=survey.id), 201
    except Exception as e:
        db.session.rollback()
        logger.error(f"Survey save error: {e}", exc_info=True)
        return jsonify(error="Failed to save survey", message=str(e)), 500

def create_survey(user_id, data):
    return Survey(
        user_id=user_id,
        profile=data.get('profile', {}),
        task_preferences=data.get('taskPreferences', {}),
        scheduling=data.get('scheduling', {}),
        analytics=data.get('analytics', {}),
        integrations=data.get('integrations', {})
    )

def register_error_handlers(app):
    @app.after_request
    def add_cors_headers(response):
        if request.path.startswith(('/api/', '/auth/')) and 'Access-Control-Allow-Origin' not in response.headers:
            set_cors_headers(response)
        return response

def set_cors_headers(response):
    headers = {
        'Access-Control-Allow-Origin': FRONTEND_URL,
        'Access-Control-Allow-Credentials': 'true',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
    }
    for key, value in headers.items():
        response.headers[key] = value
    return response

def create_app_for_migrations():
    # used by flask-migrate CLI
    app, _ = create_app()
    return app

app, socketio = create_app()
migrate.init_app(app, db)