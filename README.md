# MarketPulse
Enterprise & Professional Level Markets Data Tracking and Analysis

## Project Structure
MarketPulse is a multi-module project. 
Currently, it hosts the `markets-ref-data` module which handles external data extraction and reference data management.

### Module: `markets-ref-data`
This module is responsible for systematically fetching market reference data, such as the daily NSE Bhavcopy files.

**Key Features:**
- **Automated Bhavcopy Downloads:** Programmatically fetches end-of-day (EOD) Bhavcopy CSVs from the National Stock Exchange (NSE).
- **Session & Header Management:** Employs a custom HTTP client (`NSEHttpClient`) to seamlessly manage cookies and bypass basic scraping restrictions.
- **Structured Logging:** Dedicated logger setup for tracking download jobs and troubleshooting errors.
- **Unit Testing:** Fully mocked unit tests utilizing `pytest` to validate core components without live server hits.

#### Directory Layout
```text
markets-ref-data/
├── src/
│   ├── config.py                 # Configuration variables, URLs, and Request Headers
│   ├── utils/
│   │   ├── http_client.py        # Wrapper around requests.Session
│   │   └── logger.py             # Configured logger setup
│   └── jobs/
│       └── bhavcopy_downloader.py # The main business logic to fetch EOD prices
├── tests/                        # Comprehensive pytest test suite for utilities and jobs
├── data/                         # Target storage for downloaded datasets
├── requirements.txt              # Project Python dependencies
└── run_bhavcopy_job.py           # Entry point for the scheduled cron/Task Scheduler job
```

#### Setup & Execution
1. **Install requirements:**
   ```powershell
   cd markets-ref-data
   pip install -r requirements.txt
   ```
2. **Run the Bhavcopy downloader manually:**
   ```powershell
   python run_bhavcopy_job.py
   ```
3. **Run Unit Tests:**
   ```powershell
   pytest tests/
   ```

## Research & Learnings
* **NSE Scraping Restrictions:** The NSE India servers have aggressive anti-scraping mechanisms. Direct requests without proper headers return `401 Unauthorized` or `403 Forbidden`.
* **Cookie Sessions:** It is required to first visit the base domain (`nseindia.com`) to establish a session and grab valid cookies before attempting to download archive files directly. 
* **Header Configurations:** Supplying realistic browser `User-Agent`, `Accept`, and `Accept-Language` headers bypasses these restrictions effectively.
