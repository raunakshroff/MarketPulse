CREATE TABLE IF NOT EXISTS equity_fundamentals (
    symbol              VARCHAR(50)     NOT NULL REFERENCES equity_symbol (symbol),
    company_name        VARCHAR(255),
    sector              VARCHAR(255),
    industry            VARCHAR(255),
    description         TEXT,
    market_cap          NUMERIC(24,2),
    trailing_pe         NUMERIC(18,4),
    forward_pe          NUMERIC(18,4),
    fifty_two_week_low  NUMERIC(18,4),
    fifty_two_week_high NUMERIC(18,4),
    dividend_yield      NUMERIC(9,4),
    updated_at          TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_equity_fundamentals PRIMARY KEY (symbol)
);
