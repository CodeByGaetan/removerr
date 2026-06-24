import { Pipe, PipeTransform } from '@angular/core';
import { relativeDate } from '../util/date.util';

@Pipe({ name: 'relativeDate', standalone: true })
export class RelativeDatePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return iso ? relativeDate(iso) : '';
  }
}
