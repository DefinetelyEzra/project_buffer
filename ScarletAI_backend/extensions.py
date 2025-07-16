import werkzeug
from werkzeug.utils import secure_filename
from werkzeug.datastructures import FileStorage

# make werkzeug.secure_filename and werkzeug.FileStorage available
werkzeug.secure_filename = secure_filename
werkzeug.FileStorage    = FileStorage

from flask_sqlalchemy import SQLAlchemy
from flask_jwt_extended import JWTManager
from flask_socketio import SocketIO
from flask_uploads import UploadSet, IMAGES, configure_uploads
from flask_migrate import Migrate
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address

db = SQLAlchemy()
jwt = JWTManager()
socketio = SocketIO()
migrate = Migrate()

limiter = Limiter(key_func=get_remote_address)

# for avatar uploads
avatars = UploadSet('avatars', IMAGES)

def init_uploads(app):
    # store uploads under /static/uploads/avatars
    app.config['UPLOADED_AVATARS_DEST'] = 'static/uploads/avatars'
    configure_uploads(app, avatars)