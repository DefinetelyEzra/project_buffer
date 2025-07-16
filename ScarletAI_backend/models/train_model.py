from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.model_selection import train_test_split, RandomizedSearchCV, StratifiedKFold
from sklearn.metrics import classification_report, accuracy_score, roc_auc_score, f1_score
from sklearn.preprocessing import OneHotEncoder, StandardScaler, OrdinalEncoder
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.feature_selection import SelectFromModel
import pandas as pd
import numpy as np
import joblib
from datetime import datetime
from tempfile import mkdtemp

# Custom transformer with proper parameter handling
class TagSplitter(BaseEstimator, TransformerMixin):
    def fit(self, X, y=None): 
        return self
    
    def transform(self, X):
        return X.str.split(',')

def load_data(filepath):
    data = pd.read_csv(filepath)
    data['deadline'] = pd.to_datetime(data['deadline'])
    data['deadline_delta'] = (data['deadline'] - datetime.now()).dt.days
    data['time_slot_match'] = data['time_slot_match'].astype(int)
    return data

def prepare_experiment(data):
    features = [
        'priority', 'deadline_delta', 'energy_level', 'productivity_score',
        'duration', 'context', 'tags', 'completion_probability',
        'time_of_day', 'task_complexity', 'required_focus', 'previous_task_impact',
        'work_start', 'work_end', 'preferred_energy_level', 'energy_diff', 'workload',
        'time_slot_match'
    ]
    target = 'task_completed'
    
    missing = set(features + [target]) - set(data.columns)
    if missing:
        raise ValueError(f"Missing columns: {missing}")
    
    return data[features], data[target]

def build_preprocessor():
    numeric_features = [
        'deadline_delta', 'energy_level', 'productivity_score', 'duration',
        'completion_probability', 'required_focus', 'previous_task_impact',
        'work_start', 'work_end', 'preferred_energy_level', 'energy_diff',
        'workload', 'time_slot_match'
    ]
    
    categorical_features = ['context', 'time_of_day', 'task_complexity']
    
    return ColumnTransformer([
        ('numeric', StandardScaler(), numeric_features),
        ('priority', OrdinalEncoder(categories=[['Low', 'Medium', 'High']]), ['priority']),
        ('tags', Pipeline([
            ('split', TagSplitter()),
            ('mlb', OneHotEncoder(sparse_output=False, handle_unknown='ignore'))
        ]), ['tags']),
        ('categorical', OneHotEncoder(handle_unknown='ignore'), categorical_features)
    ])

def train_model(features, target):
    # Create cached pipeline with proper memory handling
    cachedir = mkdtemp()
    
    # Split data with consistent random state
    x_train, x_test, y_train, y_test = train_test_split(
        features, 
        target, 
        test_size=0.2, 
        stratify=target, 
        random_state=42  # Added explicit seed
    )
    
    pipeline = Pipeline([
        ('preprocessor', build_preprocessor()),
        ('feature_selector', SelectFromModel(
            RandomForestClassifier(n_estimators=100, random_state=42)  # Added seed
        )),
        ('classifier', GradientBoostingClassifier(random_state=42))  # Added seed
    ], memory=cachedir)  # Proper memory argument
    
    param_dist = {
        'classifier__n_estimators': [100, 200, 300],
        'classifier__learning_rate': [0.01, 0.05, 0.1],
        'classifier__max_depth': [3, 5, 7],
        'classifier__subsample': [0.8, 0.9, 1.0],
        'classifier__min_samples_split': [2, 5, 10]
    }
    
    search = RandomizedSearchCV(
        pipeline,
        param_dist,
        n_iter=50,
        cv=StratifiedKFold(5, random_state=42, shuffle=True),  # Added seed
        scoring=['accuracy', 'f1', 'roc_auc'],
        refit='roc_auc',
        n_jobs=-1,
        verbose=2,
        random_state=42  # Added seed
    )
    
    search.fit(x_train, y_train)
    
    best_model = search.best_estimator_
    y_pred = best_model.predict(x_test)
    y_proba = best_model.predict_proba(x_test)[:, 1]
    
    print(f"Best Model: {search.best_params_}")
    print(f"Accuracy: {accuracy_score(y_test, y_pred):.3f}")
    print(f"F1 Score: {f1_score(y_test, y_pred):.3f}")
    print(f"ROC AUC: {roc_auc_score(y_test, y_proba):.3f}")
    print("\nClassification Report:\n", classification_report(y_test, y_pred))
    
    return best_model

if __name__ == '__main__':
    data = load_data('synthetic_productivity_data.csv')
    features, target = prepare_experiment(data)
    model = train_model(features, target)
    joblib.dump(model, 'optimized_scarlet_model.pkl')
    print("Model saved to 'optimized_scarlet_model.pkl'")