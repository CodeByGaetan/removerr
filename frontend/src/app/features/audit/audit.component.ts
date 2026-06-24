import { Component, computed, inject, signal } from '@angular/core';
import { AuditEntry, AuditService } from '../../core/api/audit.service';
import { ActionChipComponent } from '../../shared/components/action-chip/action-chip.component';

const FILTERS = ['Tout', 'TRASH', 'RESTORE', 'PURGE', 'LOGIN'] as const;
type Filter = (typeof FILTERS)[number];

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [ActionChipComponent],
  templateUrl: './audit.component.html',
})
export class AuditComponent {
  private auditService = inject(AuditService);

  readonly filters = FILTERS;
  activeFilter = signal<Filter>('Tout');

  readonly loading = this.auditService.loading;

  filtered = computed(() => {
    const f = this.activeFilter();
    const entries = this.auditService.entries();
    if (f === 'Tout') return entries;
    if (f === 'PURGE') return entries.filter((e) => e.action.startsWith('PURGE'));
    return entries.filter((e) => e.action === f);
  });

  totalCount = computed(() => this.auditService.entries().length);

  constructor() {
    this.auditService.load();
  }

  refresh() {
    this.auditService.load();
  }

  formatTimestamp(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit' })
      + ' ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  parseMetadata(json: string | null): Record<string, unknown> | null {
    if (!json) return null;
    try { return JSON.parse(json); } catch { return null; }
  }

  metaEntries(json: string | null): { key: string; value: string }[] {
    const obj = this.parseMetadata(json);
    if (!obj) return [];
    return Object.entries(obj).map(([key, value]) => ({
      key,
      value: typeof value === 'number' && key === 'size'
        ? formatBytes(value as number)
        : String(value),
    }));
  }

  targetLabel(entry: AuditEntry): string {
    if (entry.targetType && entry.targetId) return `${entry.targetType} #${entry.targetId}`;
    return entry.targetId ?? '';
  }
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i];
}
