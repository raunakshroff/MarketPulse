import os
import datetime
import requests
from src.config import DATA_DIR, NSE_ARCHIVE_URL
from src.utils.logger import setup_logger
from src.utils.http_client import NSEHttpClient

logger = setup_logger(__name__)

def execute():
    """Executes the daily Bhavcopy download job."""
    client = NSEHttpClient()
    
    # Get today's date in DDMMYYYY format
    today = datetime.datetime.now
    date_str = today.strftime("%d%m%Y")
    
    file_name = f"sec_bhavdata_full_{date_str}.csv"
    download_url = f"{NSE_ARCHIVE_URL}{file_name}"
    
    os.makedirs(DATA_DIR, exist_ok=True)
    file_path = os.path.join(DATA_DIR, file_name)
    
    logger.info(f"Starting download for: {today.strftime('%Y-%m-%d')}")
    
    try:
        content = client.download_file(download_url)
        with open(file_path, "wb") as f:
            f.write(content)
        logger.info(f"Success! Saved Bhavcopy to {file_path}")
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 404:
            logger.warning("File not found (404). Today might be a market holiday or the file isn't published yet.")
        else:
            logger.error(f"HTTP Error occurred: {e}")
    except Exception as e:
        logger.error(f"An unexpected error occurred: {e}")
