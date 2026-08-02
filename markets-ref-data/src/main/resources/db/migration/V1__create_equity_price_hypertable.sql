CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS equity_symbol (
    symbol            VARCHAR(50)     NOT NULL,
    first_seen_date   DATE            NOT NULL,
    last_seen_date    DATE            NOT NULL,
    CONSTRAINT pk_equity_symbol PRIMARY KEY (symbol)
);

CREATE TABLE IF NOT EXISTS equity_price (
    trade_date        DATE            NOT NULL,
    symbol            VARCHAR(50)     NOT NULL REFERENCES equity_symbol (symbol),
    series            VARCHAR(10)     NOT NULL,
    prev_close        NUMERIC(18,4),
    open_price        NUMERIC(18,4),
    high_price        NUMERIC(18,4),
    low_price         NUMERIC(18,4),
    last_price        NUMERIC(18,4),
    close_price       NUMERIC(18,4),
    avg_price         NUMERIC(18,4),
    ttl_trd_qty       BIGINT          NOT NULL,
    turnover_lacs     NUMERIC(18,4),
    no_of_trades      BIGINT          NOT NULL,
    deliv_qty         BIGINT          NOT NULL,
    deliv_per         NUMERIC(9,4),
    CONSTRAINT pk_equity_price PRIMARY KEY (trade_date, symbol)
);

SELECT create_hypertable('equity_price', 'trade_date', if_not_exists => TRUE);
