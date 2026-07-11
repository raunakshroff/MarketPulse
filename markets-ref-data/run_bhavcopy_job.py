import sys
import os

# Ensure the project root is in the Python path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from src.jobs.bhavcopy_downloader import execute
from src.utils.logger import setup_logger

logger = setup_logger("main")

if __name__ == "__main__":
    logger.info("Triggering Bhavcopy download job...")
    execute()
    logger.info("Job execution finished.")
