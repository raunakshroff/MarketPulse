/**
 * Marks a screen as still running on Strata's original placeholder data generator.
 * Search and Stock Detail are the only screens wired to real stock-discovery data so far -
 * this one needs a backing service (watchlist/portfolio/alerts/news/market-data) that doesn't
 * exist in this system yet.
 */
export default function MockDataNote({ text = 'demo data' }: { text?: string }) {
  return <span className="pending-note">{text}</span>;
}
