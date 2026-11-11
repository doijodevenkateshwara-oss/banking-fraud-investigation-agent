---
documentId: POL-CNP-017
title: Card-Not-Present Dispute Handling Procedure
version: 3.2
effectiveDate: 2026-10-01
owner: Payment Fraud Operations
classification: SYNTHETIC_TRAINING
status: ACTIVE
chunkPrefix: cnp
---
# Card-Not-Present Dispute Handling Procedure

## 3.1 Scope
This procedure applies to disputed card-not-present payments such as e-commerce, in-app and mail or telephone order transactions. It does not apply to cash withdrawals or card-present terminal payments.

## 3.2 Initial fraud routing
When the customer denies a card-not-present transaction and retains the card, collect device, authentication and location evidence before routing for investigator review. This applies when the cardholder says they did not authorise an online card payment but still holds the card. A newly observed device or a location inconsistent with recent trusted activity supports routing to the fraud investigation queue. The routing is advisory until confirmed by an authorised reviewer.

## 3.3 Authentication outcome
Record whether a one-time password or other authentication challenge was presented and passed. If the authentication outcome is unavailable from the transaction record, list it as missing information and request it before final disposition. Do not infer the outcome.

## 3.4 Customer-authorised activity
When authentication succeeded on a known trusted device at a location consistent with recent activity, the evidence may indicate customer-authorised use. Route such cases to manual review for reviewer confirmation; do not close them automatically.
