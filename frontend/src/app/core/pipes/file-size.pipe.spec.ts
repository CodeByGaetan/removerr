import { FileSizePipe } from './file-size.pipe';

describe('FileSizePipe', () => {
  const pipe = new FileSizePipe();

  it('renders "0 B" for nullish or zero input', () => {
    expect(pipe.transform(null)).toBe('0 B');
    expect(pipe.transform(undefined)).toBe('0 B');
    expect(pipe.transform(0)).toBe('0 B');
  });

  it('keeps bytes below 1 KB', () => {
    expect(pipe.transform(512)).toBe('512.0 B');
  });

  it('picks the right unit and rounds to one decimal', () => {
    expect(pipe.transform(1536)).toBe('1.5 KB');
    expect(pipe.transform(1024 * 1024)).toBe('1.0 MB');
    expect(pipe.transform(3 * 1024 ** 3)).toBe('3.0 GB');
  });

  it('caps at TB for very large values', () => {
    expect(pipe.transform(1024 ** 5)).toBe('1024.0 TB');
  });
});
