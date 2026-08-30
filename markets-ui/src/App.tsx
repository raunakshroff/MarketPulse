import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import DashboardPage from './pages/DashboardPage';
import WatchlistPage from './pages/WatchlistPage';
import MarketsPage from './pages/MarketsPage';
import PortfolioPage from './pages/PortfolioPage';
import NewsPage from './pages/NewsPage';
import AlertsPage from './pages/AlertsPage';
import StockDetailPage from './pages/StockDetailPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="watchlist" element={<WatchlistPage />} />
          <Route path="markets" element={<MarketsPage />} />
          <Route path="portfolio" element={<PortfolioPage />} />
          <Route path="news" element={<NewsPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="stock/:symbol" element={<StockDetailPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
