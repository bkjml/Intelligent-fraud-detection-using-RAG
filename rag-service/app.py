import json
from typing import Dict, List, Any
from fastapi import FastAPI, Body, status, Response
from pydantic import BaseModel, Field

# --- 1. CONFIGURATION: Static Knowledge Base (Policy Context) ---

# The knowledge base uses policy/flag keywords as keys for exact lookup.
KNOWLEDGE_DB: Dict[str, str] = {
    "AMOUNT_HIGH": "High transaction amounts are often correlated with account takeover or mule activity.",
    "VELOCITY_REAPPLY": "Applying for a loan multiple times within a short period suggests rule evasion or high risk, relating to the 'second time within 24 hours' rule.",
    "IDENTITY_MISMATCH": "A discrepancy with NID or TIN registration suggests identity fraud or synthetic identity attempts.",
    "ACTIVE_LOAN_CHECK": "The presence of an existing active loan often correlates with debt stacking and solvency risk.",
    "NEW_DEVICE_LOGIN": "A new device being used to log in or transact increases the probability of fraud, especially if geolocation changes.",
    "AI_ANOMALY": "The AI Anomaly Detection Model has flagged a significant risk driver that is too subtle for manual rules."
}

# Mapping common rule flags (or parts of them) to the official KNOWLEDGE_DB keys.
FLAG_KEYWORD_MAP: Dict[str, str] = {
    "amount": "AMOUNT_HIGH",
    "reapply": "VELOCITY_REAPPLY",
    "24 hours": "VELOCITY_REAPPLY",
    "tin": "IDENTITY_MISMATCH",
    "nid": "IDENTITY_MISMATCH",
    "kyc": "IDENTITY_MISMATCH",
    "active loan": "ACTIVE_LOAN_CHECK",
    "device": "NEW_DEVICE_LOGIN",
}


# --- 2. PYDANTIC MODELS (Input/Output) ---

class RagRequest(BaseModel):
    """Input payload containing all hybrid detection results."""
    applicantId: str = Field(..., description="Unique ID for the applicant.")
    attributes: Dict[str, Any] = Field({}, description="Additional application attributes (optional).")
    ruleFlags: List[str] = Field(..., description="Flags triggered by the Traditional Rules-Based Engine.")
    aiScore: float = Field(..., description="The overall anomaly score from the AI-Based Engine (0.0 to 1.0).", ge=0.0, le=1.0)
    
    # Optional field to pass the top features from the previous step's 'result' map
    topFeatures: Dict[str, float] = Field({}, description="The top N features impacting the AI score.")


class RagResponse(BaseModel):
    """The synthesized reasoning output."""
    reasoning: str = Field(..., description="The final, synthesized explanation for the risk decision.")
    riskCategory: str = Field(..., description="The categorized risk level: LOW, MEDIUM, or HIGH.")
    confidence: float = Field(..., description="The AI anomaly score, rounded for presentation.")


# --- 3. CORE LOGIC FUNCTIONS ---

def retrieve_knowledge(rule_flags: List[str], ai_score: float) -> str:
    """
    (R)etrieval: Selects relevant policy context based on triggered rules and AI score threshold.
    """
    
    retrieved_keys = set()
    
    # 1. Match Rule Flags to Policy Context
    for flag in rule_flags:
        # Normalize the flag for comparison
        flag_lower = flag.lower()
        
        for keyword, db_key in FLAG_KEYWORD_MAP.items():
            if keyword in flag_lower:
                retrieved_keys.add(db_key)
                # Found a match, move to the next flag
                break
            
    # 2. Match AI Score to Generic Context
    # Use a clear threshold for an AI-only decision flag
    if ai_score >= 0.6:
        retrieved_keys.add("AI_ANOMALY")

    # Retrieve the actual knowledge snippets
    context = [KNOWLEDGE_DB[key] for key in retrieved_keys]
    
    # Format the context into a single string
    return "\n".join(context)

# --- 3. CORE LOGIC FUNCTIONS (Modified) ---

def generate_reasoning_and_risk(context: str, request: RagRequest) -> RagResponse:
    """
    (G)eneration: Synthesizes the retrieved context and hybrid scores into a final decision,
    falling back to listing raw rule flags when RAG context is unavailable.
    """
    
    num_rules_triggered = len(request.ruleFlags)
    ai_score = request.aiScore
    confidence = round(ai_score, 4)
    
    # --- Context Determination ---
    # Determine the primary explanation source: RAG context or raw flags.
    if context:
        # Use the first sentence of the RAG context for a summary
        summary_context = context.split('.')[0]
        context_source = "RAG policy context"
    elif num_rules_triggered > 0:
        # Fallback: Use the triggered rule flags as the explanation
        severity = "Severe" if ai_score >= 0.7 or num_rules_triggered >= 2 else "Moderate"
        flag_list = ", ".join(request.ruleFlags)
        summary_context = f"Triggered {severity} rule flags: {flag_list}"
        context_source = "Rule Flags"
    else:
        # No RAG context and no rules, only AI anomaly (or low risk)
        summary_context = "AI anomaly detected (no specific rule match)."
        context_source = "AI Anomaly"


    # --- Hybrid Decision Logic ---
    
    # High Risk: Strong signal from either engine
    if ai_score >= 0.7 or num_rules_triggered >= 2:
        risk_category = "HIGH"
        
        reasoning = (
            f"**HIGH RISK ({confidence*100:.2f}%)**: Immediate manual review/block required. "
            f"Risk is severe due to multiple Rule Flags ({num_rules_triggered}) and/or a high AI Anomaly Score ({ai_score:.4f}). "
            f"Key risk drivers (Source: {context_source}): {summary_context}. The top features impacting the AI score were: {list(request.topFeatures.keys())}."
        )
        
    # Medium Risk: Moderate signal, requires analyst intervention
    elif ai_score >= 0.4 or num_rules_triggered >= 1:
        risk_category = "MEDIUM"
        
        reasoning = (
            f"**MEDIUM RISK ({confidence*100:.2f}%)**: Requires manual review by an analyst. "
            f"The risk is moderate, triggered by {'Rule Flags' if num_rules_triggered > 0 else 'a moderate AI anomaly'}. "
            f"Context (Source: {context_source}): {summary_context}. Review top features: {list(request.topFeatures.keys())}."
        )
        
    # Low Risk: Safe to approve
    else:
        risk_category = "LOW"
        reasoning = (
            f"**LOW RISK ({confidence*100:.2f}%)**: No significant fraud indicators detected by the hybrid system. Transaction approved."
        )
        
    # Return the full RagResponse object
    return RagResponse(
        reasoning=reasoning,
        riskCategory=risk_category,
        confidence=confidence
    )


# --- 4. FASTAPI ENDPOINT ---

app = FastAPI(
    title="Optimized Hybrid Fraud Reasoning Service", 
    description="A deterministic, low-latency service for fraud decision synthesis.",
    version="1.2"
)

@app.post("/explain", response_model=RagResponse, status_code=status.HTTP_200_OK)
async def reason_orchestrator(request: RagRequest = Body(
    ..., 
    example={
        "applicantId": "A123456",
        "attributes": {"loan_type": "Personal", "ip_country": "US"},
        "ruleFlags": ["REAPPLY_VELOCITY_24_HOURS", "AMOUNT_OVER_10K"],
        "aiScore": 0.825,
        "topFeatures": {"V14": 0.4550, "V4": 0.4263, "Amount": 7.8244}
    }
)):
    """
    The main orchestrator that synthesizes Rule Flags and AI Scores into a final reasoning.
    """
    
    # 1. RETRIEVAL (RAG Concept): Look up relevant business context
    # Note: We pass aiScore directly as we don't use 'attributes' for the AI score check.
    context = retrieve_knowledge(request.ruleFlags, request.aiScore)
    
    # 2. GENERATION: Synthesize the final decision and reasoning
    response_obj = generate_reasoning_and_risk(context, request)
    
    return response_obj