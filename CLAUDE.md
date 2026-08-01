# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarketPulse is a multi-module project for enterprise/professional-level markets data tracking and analysis. It currently hosts one module, `markets-ref-data`, which handles external data extraction and reference data management (fetching daily NSE Bhavcopy EOD price files). Future modules are expected to live as sibling directories alongside `markets-ref-data`.

## Commands

All commands below are run from the `markets-ref-data/` directory.

```powershell
cd markets-ref-data

# Install dependencies
pip install -r requirements.txt

# Run the downloader job manually
python run_bhavcopy_job.py

# Run the full test suite
pytest tests/

# Run a single test file
pytest tests/test_http_client.py

# Run a single test
pytest tests/test_http_client.py::test_download_file_success
```

Note: `pytest` is not currently installed in the active Python environment (only `requests` is present) — run `pip install -r requirements.txt` before testing.

There is no linter, formatter, or CI config in this repo yet.

## Architecture

### Import root and path setup

Code under `src/` uses absolute imports rooted at `markets-ref-data/` (e.g. `from src.config import ...`, `from src.utils.logger import setup_logger`). Both the entry point and the test suite manually add `markets-ref-data/` to `sys.path` rather than relying on a package install:
- [run_bhavcopy_job.py](markets-ref-data/run_bhavcopy_job.py) appends its own directory to `sys.path` before importing `src.*`.
- [tests/conftest.py](markets-ref-data/tests/conftest.py) does the same (appends the parent of `tests/`, i.e. `markets-ref-data/`).

There is no `setup.py`/`pyproject.toml` — the module is not pip-installed, so always run scripts/tests from within `markets-ref-data/` so this path logic resolves correctly.

### Data flow (`markets-ref-data`)

1. **[src/config.py](markets-ref-data/src/config.py)** — central config: `BASE_DIR`/`DATA_DIR` paths, NSE base/archive URLs, and the browser-like `DEFAULT_HEADERS` (User-Agent/Accept/Accept-Language) required to avoid NSE's anti-scraping blocks.
2. **[src/utils/http_client.py](markets-ref-data/src/utils/http_client.py)** — `NSEHttpClient` wraps a `requests.Session`. On construction it first `GET`s `NSE_BASE_URL` to establish cookies/session state (NSE requires this before archive downloads succeed), then exposes `download_file(url)` which does a `GET` + `raise_for_status()` and returns raw bytes.
3. **[src/jobs/bhavcopy_downloader.py](markets-ref-data/src/jobs/bhavcopy_downloader.py)** — `execute()` is the job entry point: builds today's Bhavcopy filename (`sec_bhavdata_full_DDMMYYYY.csv`), downloads it via `NSEHttpClient`, writes it to `DATA_DIR`, and logs outcomes. A `404` is treated as an expected "holiday or not yet published" case (logged as a warning, not an error) rather than a failure.
4. **[src/utils/logger.py](markets-ref-data/src/utils/logger.py)** — `setup_logger(name)` returns a standard `logging.Logger` with a single `StreamHandler`; it guards against adding duplicate handlers if called more than once with the same name.
5. **[run_bhavcopy_job.py](markets-ref-data/run_bhavcopy_job.py)** — the script invoked by cron/Windows Task Scheduler; just calls `execute()` with logging around it.

### Testing conventions

Tests fully mock network and filesystem calls (no live HTTP requests to NSE, no real file I/O) using `unittest.mock.patch`:
- `requests.Session.get` is patched directly to control both the session-init call and subsequent download calls (see [tests/test_http_client.py](markets-ref-data/tests/test_http_client.py) — note the session-init `GET` happens in `NSEHttpClient.__init__`, so tests that check `download_file` in isolation call `mock_get.reset_mock()` after construction).
- `datetime`, `open`, and `os.makedirs` are patched in [tests/test_bhavcopy_downloader.py](markets-ref-data/tests/test_bhavcopy_downloader.py) to assert the exact filename/path derived from a fixed date, without touching disk.

### Known operational constraints (from README research notes)

- NSE aggressively blocks scraping; requests without proper headers return `401`/`403`.
- A session must be established by visiting `nseindia.com` first to obtain valid cookies before hitting archive file URLs directly — this is why `NSEHttpClient` performs a warm-up `GET` in its constructor.
- Realistic browser `User-Agent`/`Accept`/`Accept-Language` headers (see `DEFAULT_HEADERS` in `config.py`) are required to bypass these restrictions.
