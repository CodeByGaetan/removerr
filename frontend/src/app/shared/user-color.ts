const COLORS = [
  '#fa8072', '#4a9eff', '#52d173', '#f0c040', '#c472f2',
  '#f07050', '#40d0c8', '#f06090', '#80d0ff', '#d0a060',
];

function hashName(name: string): number {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return h;
}

export function userColor(name: string): string {
  return COLORS[hashName(name || '?') % COLORS.length];
}

export function userGradient(name: string): string {
  return `linear-gradient(135deg, ${userColor(name)}, #2a78ff)`;
}
