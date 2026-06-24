import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface AuditEntry {
  id: number;
  userId: number | null;
  username: string;
  action: string;
  targetType: string;
  targetId: string;
  metadataJson: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  readonly entries = signal<AuditEntry[]>([]);
  readonly loading = signal(false);

  constructor(private http: HttpClient) {}

  load(limit = 200): void {
    this.loading.set(true);
    this.http.get<AuditEntry[]>(`/api/audit?limit=${limit}`).subscribe({
      next: (data) => { this.entries.set(data); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
