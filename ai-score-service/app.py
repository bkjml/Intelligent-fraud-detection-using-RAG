import joblib
import pandas as pd
from typing import Dict, Any
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import numpy as np # Needed for array handling from predict_proba

# --- Configuration ---
# Set the number of top features you want to return
TOP_N_FEATURES = 5 

# --- Model Loading ---
try:
    # Load the pre-trained model
    model = joblib.load("models/lgb_model.pkl")
    print("Model loaded successfully.")
    # Check if the model has the predict_proba method, which is necessary for probability output
    if not hasattr(model, 'predict_proba'):
        raise AttributeError("Loaded model does not have a 'predict_proba' method. Is it a classifier?")
except FileNotFoundError:
    print("ERROR: Model file 'models/lgb_model.pkl' not found.")
    model = None
except AttributeError as e:
    print(f"MODEL ERROR: {e}")
    model = None
except Exception as e:
    print(f"ERROR during model loading: {e}")
    model = None

# --- Application Initialization ---
app = FastAPI(
    title="LightGBM Probability Scoring Microservice",
    description=f"Scores requests and returns the probability of the positive class and the top {TOP_N_FEATURES} features.",
    version="1.2.0"
)

# --- Request Data Model (Input) ---
class FeaturePayload(BaseModel):
    """Schema for the feature dictionary, including Time, Amount, and V1-V28."""
    features: Dict[str, float] = Field(
        ...,
        description="A dictionary containing all input features."
    )
    
# --- Response Data Model (Output) ---
class AiScoreResponse(BaseModel):
    """Schema for the model scoring response."""
    score: float = Field(
        ...,
        description="The probability (0.0 to 1.0) of the positive class."
    )
    result: Dict[str, float] = Field(
        ...,
        description=f"The top {TOP_N_FEATURES} features ranked by their absolute magnitude."
    )
    
# --- API Endpoint ---

@app.post("/score", response_model=AiScoreResponse)
def score_request(payload: FeaturePayload):
    """
    Scores the provided features and returns the probability score and the top features by magnitude.
    """
    
    if model is None:
        raise HTTPException(status_code=503, detail="Model is not loaded or does not support probability prediction. Service unavailable.")

    features_dict = payload.features
    
    # 1. Prepare data for the model
    try:
        data_df = pd.DataFrame([features_dict])
    except Exception as e:
        raise HTTPException(
            status_code=422, 
            detail=f"Could not convert features to DataFrame. Error: {e}"
        )

    # 2. Make prediction using predict_proba
    try:
        # Use .predict_proba() and select the probability of the positive class (index 1)
        # This will return a probability score between 0.0 and 1.0
        probabilities = model.predict_proba(data_df)
        # For LightGBM classification, use predict_proba and take the second column (Class 1 probability)
        
        # Check if the output shape is correct (2 columns for binary classification)
        if probabilities.shape[1] < 2:
            raise ValueError("Model predict_proba did not return a 2-column array. Check model type.")
            
        prediction_score = probabilities[0, 1] # Probability of the positive class (index 1)
        
    except Exception as e:
        raise HTTPException(
            status_code=500, 
            detail=f"Probability prediction failed. Ensure feature names match model training columns. Error: {e}"
        )

    # 3. Select Top Features by Magnitude
    feature_list = list(features_dict.items())
    
    # Sort the list based on the absolute value (magnitude) of the feature value, descending
    sorted_features = sorted(feature_list, key=lambda item: abs(item[1]), reverse=True)
    
    # Take the top N features
    top_n_features_list = sorted_features[:TOP_N_FEATURES]
    
    # Convert the top N features list back to a dictionary for the response
    top_n_features_dict = dict(top_n_features_list)

    # 4. Format the response
    response_data = AiScoreResponse(
        score=float(prediction_score),
        result=top_n_features_dict
    )

    return response_data