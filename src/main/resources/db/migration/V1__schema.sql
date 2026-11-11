-- Synthetic training schema. No raw card, account or customer identifiers are stored.
CREATE SEQUENCE case_number_seq START WITH 42 INCREMENT BY 1;

CREATE TABLE fraud_case (
    case_id            VARCHAR(32)  PRIMARY KEY,
    masked_customer_ref VARCHAR(32) NOT NULL,
    home_region        VARCHAR(80),
    customer_location  VARCHAR(80),
    reported_channel   VARCHAR(20)  NOT NULL,
    status             VARCHAR(24)  NOT NULL,
    reported_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by         VARCHAR(64)  NOT NULL,
    last_investigated_by VARCHAR(64),
    run_count          INT          NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE transaction_claim (
    claim_id           VARCHAR(40)  PRIMARY KEY,
    case_id            VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    transaction_ref    VARCHAR(32)  NOT NULL,
    amount             DECIMAL(15,2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_status VARCHAR(16)  NOT NULL,
    payment_channel    VARCHAR(24)  NOT NULL,
    masked_merchant    VARCHAR(64)  NOT NULL,
    issue_type         VARCHAR(32)  NOT NULL,
    statement          VARCHAR(1000) NOT NULL,
    original_statement VARCHAR(1000) NOT NULL,
    card_possession    VARCHAR(16)  NOT NULL,
    merchant_recognised BOOLEAN     NOT NULL,
    injection_suspected BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_claim_case ON transaction_claim(case_id);

CREATE TABLE evidence_item (
    evidence_id        VARCHAR(40)  PRIMARY KEY,
    case_id            VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    run_number         INT          NOT NULL,
    evidence_type      VARCHAR(16)  NOT NULL,
    severity           VARCHAR(16)  NOT NULL,
    title              VARCHAR(120) NOT NULL,
    value_summary      VARCHAR(500) NOT NULL,
    provenance         VARCHAR(16)  NOT NULL,
    source             VARCHAR(64)  NOT NULL,
    observed_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    tool_trace_id      VARCHAR(40)
);
CREATE INDEX ix_evidence_case ON evidence_item(case_id, run_number);

CREATE TABLE tool_trace (
    trace_id           VARCHAR(40)  PRIMARY KEY,
    case_id            VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    run_number         INT          NOT NULL,
    seq                INT          NOT NULL,
    tool_name          VARCHAR(64)  NOT NULL,
    request_hash       VARCHAR(64)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    duration_ms        BIGINT       NOT NULL,
    result_summary     VARCHAR(500) NOT NULL,
    started_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id     VARCHAR(64)  NOT NULL,
    redacted_result    CLOB
);
CREATE INDEX ix_trace_case ON tool_trace(case_id, run_number, seq);

CREATE TABLE recommendation (
    recommendation_id  VARCHAR(40)  PRIMARY KEY,
    case_id            VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    run_number         INT          NOT NULL,
    mode               VARCHAR(24)  NOT NULL,
    classification     VARCHAR(32)  NOT NULL,
    route              VARCHAR(24)  NOT NULL,
    risk               VARCHAR(8)   NOT NULL,
    priority           VARCHAR(4)   NOT NULL,
    confidence         DOUBLE PRECISION NOT NULL,
    human_review_required BOOLEAN   NOT NULL,
    rationale          VARCHAR(1500) NOT NULL,
    evidence_ids       CLOB NOT NULL,
    missing_information CLOB NOT NULL,
    contradictions     CLOB NOT NULL,
    evidence_requests  CLOB NOT NULL,
    rule_ids           CLOB NOT NULL,
    rule_facts         CLOB NOT NULL,
    tool_trace_ids     CLOB NOT NULL,
    plan               CLOB NOT NULL,
    policy_query       VARCHAR(400),
    retrieval_top_k    INT          NOT NULL,
    retrieval_threshold DOUBLE PRECISION NOT NULL,
    abstained          BOOLEAN      NOT NULL,
    fallback_reason    VARCHAR(500),
    correlation_id     VARCHAR(64)  NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_rec_case ON recommendation(case_id, run_number);

CREATE TABLE policy_citation (
    citation_id        VARCHAR(48)  PRIMARY KEY,
    recommendation_id  VARCHAR(40)  NOT NULL REFERENCES recommendation(recommendation_id),
    document_id        VARCHAR(32)  NOT NULL,
    title              VARCHAR(120) NOT NULL,
    version            VARCHAR(16)  NOT NULL,
    effective_date     DATE         NOT NULL,
    section            VARCHAR(16)  NOT NULL,
    section_title      VARCHAR(120) NOT NULL,
    chunk_id           VARCHAR(48)  NOT NULL,
    score              DOUBLE PRECISION NOT NULL,
    passage            VARCHAR(2000) NOT NULL,
    cited              BOOLEAN      NOT NULL
);

CREATE TABLE review_disposition (
    review_id          VARCHAR(40)  PRIMARY KEY,
    case_id            VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    recommendation_id  VARCHAR(40)  NOT NULL REFERENCES recommendation(recommendation_id),
    reviewer_id        VARCHAR(64)  NOT NULL,
    disposition        VARCHAR(40)  NOT NULL,
    final_route        VARCHAR(24),
    reason             VARCHAR(1000) NOT NULL,
    checklist          CLOB NOT NULL,
    recorded_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_event (
    seq                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id           VARCHAR(40)  NOT NULL UNIQUE,
    case_id            VARCHAR(32),
    event_type         VARCHAR(40)  NOT NULL,
    actor_type         VARCHAR(8)   NOT NULL,
    actor_id           VARCHAR(64)  NOT NULL,
    recorded_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id     VARCHAR(64),
    summary            VARCHAR(500) NOT NULL,
    payload_hash       VARCHAR(64)  NOT NULL,
    prev_hash          VARCHAR(64)  NOT NULL,
    entry_hash         VARCHAR(64)  NOT NULL
);
CREATE INDEX ix_audit_case ON audit_event(case_id, seq);

CREATE TABLE chat_memory (
    memory_id          VARCHAR(32)  PRIMARY KEY,
    messages_json      CLOB         NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
