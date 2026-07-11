import requests
from src.config import NSE_BASE_URL, DEFAULT_HEADERS
from src.utils.logger import setup_logger

logger = setup_logger(__name__)

class NSEHttpClient:
    """HTTP Client tailored for NSE requests to handle cookies and headers."""
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update(DEFAULT_HEADERS)
        self._initialize_session()

    def _initialize_session(self):
        """Initializes the session by visiting the home page to grab necessary cookies."""
        try:
            self.session.get(NSE_BASE_URL, timeout=10)
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to initialize NSE session: {e}")

    def download_file(self, url: str) -> bytes:
        """Downloads a file from the given URL and returns its content."""
        response = self.session.get(url, timeout=10)
        response.raise_for_status()
        return response.content
