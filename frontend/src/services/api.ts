export interface AccountantUserSession {
  id: number;
  email: string;
  fullName: string;
  professionalCardNumber: string;
  firmName: string;
  role: string;
  authToken: string;
}

const AUTH_TOKEN_KEY = 'alcontable_auth_token';
const AUTH_USER_KEY = 'alcontable_accountant_user';

export function getAuthToken(): string | null {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getStoredAccountantSession(): AccountantUserSession | null {
  try {
    const raw = localStorage.getItem(AUTH_USER_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as AccountantUserSession;
  } catch {
    return null;
  }
}

export function setStoredAccountantSession(session: AccountantUserSession): void {
  try {
    localStorage.setItem(AUTH_TOKEN_KEY, session.authToken);
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(session));
  } catch {
    // ignore storage errors
  }
}

export function clearStoredAccountantSession(): void {
  try {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
  } catch {
    // ignore storage errors
  }
}

export async function apiRequest<T = any>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAuthToken();
  const res = await fetch(path, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...((options.headers as Record<string, string>) || {}),
    },
  });

  let data: any = null;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      data = await res.json();
    } catch {
      data = null;
    }
  } else {
    try {
      const text = await res.text();
      data = { error: text };
    } catch {
      data = null;
    }
  }

  if (!res.ok) {
    throw new Error(data?.error || data?.message || `Error HTTP ${res.status}`);
  }
  return data as T;
}

export function formatCOP(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '$0';
  const num = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(num)) return String(value);
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(num);
}

/**
 * Parsea celdas monetarias copiadas desde Excel en formato colombiano ("$ 3.200.000,00")
 * o internacional ("3,200,000.00") sin multiplicar los decimales por 100.
 */
export function parseColombianCurrency(val: string | undefined | null, fallback = 0): number {
  if (!val) return fallback;
  let str = val.trim().replace(/\$/g, '').replace(/"/g, '').trim();
  if (!str || str === '-' || str.toLowerCase() === 'n/a') return fallback;

  if (str.includes(',') && str.includes('.')) {
    if (str.lastIndexOf(',') > str.lastIndexOf('.')) {
      str = str.replace(/\./g, '').replace(',', '.');
    } else {
      str = str.replace(/,/g, '');
    }
  } else if (str.includes(',') && !str.includes('.')) {
    const parts = str.split(',');
    if (parts.length === 2 && parts[1].length <= 2) {
      str = parts[0].replace(/[^0-9-]/g, '') + '.' + parts[1];
    } else {
      str = str.replace(/[^0-9-]/g, '');
    }
  } else if (str.includes('.') && !str.includes(',')) {
    const dotCount = (str.match(/\./g) || []).length;
    if (dotCount > 1 || str.length - str.lastIndexOf('.') > 3) {
      str = str.replace(/\./g, '');
    }
  } else {
    str = str.replace(/[^0-9.-]/g, '');
  }

  const num = Math.round(Number(str));
  return Number.isNaN(num) ? fallback : Math.max(0, num);
}

export function exportRowsToCsv(filename: string, headers: string[], rows: (string | number)[][]) {
  const escapeCell = (cell: string | number) => {
    const str = String(cell ?? '');
    if (str.includes(';') || str.includes('"') || str.includes('\n')) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };
  const csvContent =
    '\uFEFF' +
    [headers.map(escapeCell).join(';'), ...rows.map((r) => r.map(escapeCell).join(';'))].join('\r\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export async function copyRowsAsTsvForExcel(headers: string[], rows: (string | number)[][]): Promise<void> {
  const cleanCell = (c: any) => String(c ?? '').replace(/\t/g, ' ').replace(/\r?\n/g, ' ');
  const tsv = [
    headers.map(cleanCell).join('\t'),
    ...rows.map((r) => r.map(cleanCell).join('\t')),
  ].join('\n');

  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(tsv);
      return;
    }
  } catch {
    // Fallback to textarea copy
  }

  const textArea = document.createElement('textarea');
  textArea.value = tsv;
  textArea.style.position = 'fixed';
  textArea.style.left = '-9999px';
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();
  document.execCommand('copy');
  document.body.removeChild(textArea);
}
