import { tileToSvg } from 'tilekit'

export function tileSvg(code, width = 42) {
  const height = Math.round(width * 84 / 64)
  return tileToSvg(code, {
    width,
    height,
    radius: Math.max(2, Math.round(width * 4 / 64))
  })
}
