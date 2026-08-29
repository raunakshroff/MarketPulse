// Mirrors stock-discovery's model/ records.

export interface EquitySearchResult {
  symbol: string;
  companyName: string | null;
  sector: string | null;
}

export interface EquityRecord {
  symbol: string;
  series: string;
  date: string; // dd-MMM-yyyy
  prevClose: number | null;
  openPrice: number | null;
  highPrice: number | null;
  lowPrice: number | null;
  lastPrice: number | null;
  closePrice: number | null;
  avgPrice: number | null;
  ttlTradedQty: number;
  turnoverLacs: number | null;
  noOfTrades: number;
  delivQty: number;
  delivPer: number | null;
}

export interface FundamentalsView {
  symbol: string;
  companyName: string | null;
  sector: string | null;
  industry: string | null;
  description: string | null;
  marketCap: number | null;
  trailingPe: number | null;
  forwardPe: number | null;
  fiftyTwoWeekLow: number | null;
  fiftyTwoWeekHigh: number | null;
  dividendYield: number | null;
  updatedAt: string;
}
