import type { EquityRecord, EquitySearchResult, FundamentalsView } from '../types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8082';

async function getJson<T>(path: string): Promise<T | null> {
  const response = await fetch(`${BASE_URL}${path}`);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Request to ${path} failed: HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function searchStocks(query: string, limit = 8): Promise<EquitySearchResult[]> {
  if (!query.trim()) {
    return [];
  }
  const result = await getJson<EquitySearchResult[]>(
    `/api/v1/stocks/search?q=${encodeURIComponent(query)}&limit=${limit}`,
  );
  return result ?? [];
}

export async function getHistory(symbol: string): Promise<EquityRecord[] | null> {
  return getJson<EquityRecord[]>(`/api/v1/stocks/${encodeURIComponent(symbol)}/history`);
}

export async function getFundamentals(symbol: string): Promise<FundamentalsView | null> {
  return getJson<FundamentalsView>(`/api/v1/stocks/${encodeURIComponent(symbol)}/fundamentals`);
}
