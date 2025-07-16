"""Duration Overhaul

Revision ID: 19dea1768db8
Revises: b92b4fab7620
Create Date: 2025-04-13 21:06:18.180805

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision = '19dea1768db8'
down_revision = 'b92b4fab7620'
branch_labels = None
depends_on = None


def upgrade():
    # Drop duration column from tasks table
    op.drop_column('tasks', 'duration', schema='scarlet')
    
    # Drop duration column from user_behaviors table
    op.drop_column('user_behaviors', 'duration', schema='scarlet')


def downgrade():
    # Recreate duration columns
    op.add_column('user_behaviors', 
        sa.Column('duration', sa.FLOAT(), autoincrement=False, nullable=True),
        schema='scarlet'
    )
    op.add_column('tasks',
        sa.Column('duration', sa.FLOAT(), autoincrement=False, nullable=True),
        schema='scarlet'
    )
    # ### end Alembic commands ###
