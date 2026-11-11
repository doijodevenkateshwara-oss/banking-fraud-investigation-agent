---
documentId: POL-FRD-009
title: Fraud Escalation Evidence Checklist
version: 2.1
effectiveDate: 2026-09-15
owner: Financial Crime Controls
classification: SYNTHETIC_TRAINING
status: ACTIVE
chunkPrefix: frd
---
# Fraud Escalation Evidence Checklist

## 2.3 Independent indicators
Treat device novelty, recent-location anomaly, failed authentication and merchant blocklist matches as independent indicators only when they come from separate sources. Two or more independent high-severity indicators justify escalation for fraud investigation.

## 2.4 Minimum evidence
Record the transaction state, authentication outcome, device novelty, recent-location comparison and customer statement. Missing items must be visible to the reviewer.

## 2.5 Contradictory signals
When device and location signals contradict each other, for example a known trusted device used from an anomalous location, show the contradiction to the reviewer, reduce confidence and route for manual review rather than presenting a definitive fraud finding.

## 2.6 Unavailable evidence
If a required evidence source times out, is not found or is unavailable (for example the device service timed out or the location record was not found), record a structured unavailable result and a missing-information entry. Never substitute assumed values for unavailable evidence. Cases without the transaction record or both device and location evidence are insufficient for a routing recommendation.
