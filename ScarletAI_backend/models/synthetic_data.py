import pandas as pd
import numpy as np
from datetime import datetime, timedelta

# Seeded random generator for reproducibility
_SEED = 42
_RNG = np.random.default_rng(_SEED)

def generate_tasks(num_tasks: int = 1500, rng: np.random.Generator = _RNG) -> pd.DataFrame:
    """
    Generate synthetic task data with realistic patterns and constraints
    """
    tasks = []
    
    for _ in range(num_tasks):
        # Priority distribution (20% High, 50% Medium, 30% Low)
        priority = rng.choice(
            ['High', 'Medium', 'Low'],
            p=[0.2, 0.5, 0.3]
        )
        
        # Fix: Convert numpy int to Python native int
        deadline_days = int(rng.integers(1, 15))
        deadline = datetime.now() + timedelta(days=deadline_days)
        
        # Energy level (normal distribution centered at 6)
        energy_level = int(np.clip(rng.normal(6, 2), 1, 10))
        
        # Productivity score (energy-dependent + random factor)
        productivity_score = np.clip(
            0.4 * (energy_level / 10) + 0.6 * rng.uniform(),
            0.1, 1.0
        )
        
        # Context-aware features
        context = rng.choice(['Work', 'Personal', 'Study', 'Errands'])
        tags = rng.choice(
            ['Urgent', 'Important', 'Routine', 'Creative', 'Meeting', 'Deep Work'],
            size=int(rng.integers(1, 4)),  # Convert to Python int
            replace=False
        )
        
        tasks.append({
            'priority': priority,
            'deadline': deadline.strftime('%Y-%m-%d %H:%M'),
            'energy_level': energy_level,
            'productivity_score': round(productivity_score, 2),
            'context': context,
            'tags': ','.join(tags),
            'completion_probability': float(rng.uniform(0.3, 0.95)),
            'time_of_day': rng.choice(['Morning', 'Afternoon', 'Evening', 'Night']),
            'task_complexity': rng.choice(['Simple', 'Moderate', 'Complex']),
            'required_focus': int(rng.choice([1, 2, 3])),  # Convert to Python int
            'previous_task_impact': float(rng.uniform(0, 1))
        })
    
    return pd.DataFrame(tasks)

def generate_users(num_users: int = 100, rng: np.random.Generator = _RNG) -> pd.DataFrame:
    """
    Generate synthetic user data with realistic work patterns
    """
    users = []
    
    for user_id in range(1, num_users + 1):
        # Work hours with normal distribution
        work_start = int(np.clip(rng.normal(8.5, 1.5), 6, 10))
        work_end = int(np.clip(
            work_start + rng.normal(8.5, 1), 
            work_start + 6, 
            23
        ))
        
        # Energy preference
        preferred_energy = int(np.clip(
            10 - (work_end - work_start)/2 + rng.uniform(-1, 1),
            1, 10
        ))
        
        users.append({
            'user_id': user_id,
            'work_start': work_start,
            'work_end': work_end,
            'timezone': rng.choice(['UTC', 'PST', 'EST', 'CET']),
            'preferred_energy_level': preferred_energy,
            'workstyle': rng.choice(['Morning Person', 'Night Owl', 'Flexible']),
            'stress_tolerance': rng.uniform(0.3, 0.9),
            'recovery_rate': rng.uniform(0.1, 0.8)
        })
    
    return pd.DataFrame(users)

def generate_dataset() -> pd.DataFrame:
    """
    Main function to generate and merge synthetic dataset
    """
    # Generate base datasets
    tasks = generate_tasks(1500, _RNG)
    users = generate_users(100, _RNG)

    # Create user-task assignments with power law distribution
    probs = np.linspace(0.5, 1.5, len(users))
    probs /= probs.sum()
    
    tasks['user_id'] = _RNG.choice(
        users['user_id'],
        size=len(tasks),
        p=probs
    )

    # Merge with validation
    merged = pd.merge(
        tasks,
        users,
        on='user_id',
        how='left',
        validate='many_to_one'
    )

    # Add derived features
    merged['energy_diff'] = merged['energy_level'] - merged['preferred_energy_level']
    
    # Time slot matching feature
    time_mapping = {
        'Morning': (8, 11),
        'Afternoon': (12, 17),
        'Evening': (18, 21),
        'Night': (22, 23)
    }
    merged['time_slot_match'] = merged.apply(
        lambda row: 1 if (row['work_start'] <= time_mapping[row['time_of_day']][0]) and
                        (row['work_end'] >= time_mapping[row['time_of_day']][1])
                     else 0,
        axis=1
    )

    return merged

if __name__ == '__main__':
    dataset = generate_dataset()
    dataset.to_csv('synthetic_productivity_data.csv', index=False)
    print("Dataset generated with shape:", dataset.shape)