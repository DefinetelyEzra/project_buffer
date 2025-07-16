import os
import json
from datetime import datetime
from dotenv import load_dotenv
from sqlalchemy import create_engine, Column, Integer, DateTime, ForeignKey, JSON
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# Load environment variables from .env file if present
load_dotenv()

# Check required environment variables
required_env_vars = [
    'DATABASE_USER', 'DATABASE_PASSWORD', 'DATABASE_HOST',
    'DATABASE_PORT', 'DATABASE_NAME', 'JWT_SECRET_KEY'
]

for var in required_env_vars:
    if not os.getenv(var):
        raise EnvironmentError(f"Environment variable {var} is missing.")

DB_URI = f"postgresql://{os.getenv('DATABASE_USER')}:{os.getenv('DATABASE_PASSWORD')}@" \
         f"{os.getenv('DATABASE_HOST')}:{os.getenv('DATABASE_PORT')}/{os.getenv('DATABASE_NAME')}"

engine = create_engine(DB_URI)
Session = sessionmaker(bind=engine)
Base = declarative_base()

class Survey(Base):
    __tablename__ = 'surveys'
    __table_args__ = {'schema': 'scarlet'}
    
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey('scarlet.users.id'), nullable=True)
    profile = Column(JSON, nullable=False, default=dict)
    task_preferences = Column(JSON, nullable=False, default=dict)
    scheduling = Column(JSON, nullable=False, default=dict)
    analytics = Column(JSON, nullable=False, default=dict)
    integrations = Column(JSON, nullable=False, default=dict)
    created_at = Column(DateTime, default=datetime.utcnow)

def export_surveys_to_jsonb(filename='surveys_export.jsonb'):
    """Export all survey records to a JSONB-formatted text file"""
    session = Session()
    try:
        surveys = session.query(Survey).all()
        
        with open(filename, 'w', encoding='utf-8') as f:
            for survey in surveys:
                # Convert SQLAlchemy object to dictionary
                record = {
                    'id': survey.id,
                    'user_id': survey.user_id,
                    'profile': survey.profile,
                    'task_preferences': survey.task_preferences,
                    'scheduling': survey.scheduling,
                    'analytics': survey.analytics,
                    'integrations': survey.integrations,
                    'created_at': survey.created_at.isoformat() if survey.created_at else None
                }
                # Write each record as a separate JSON line
                f.write(json.dumps(record) + '\n')
                
        print(f"Successfully exported {len(surveys)} records to {filename}")
        
    finally:
        session.close()

if __name__ == '__main__':
    export_surveys_to_jsonb()