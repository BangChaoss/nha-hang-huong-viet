export function orderCode(id: string) {
  return `#${id.slice(0, 8).toUpperCase()}`;
}
