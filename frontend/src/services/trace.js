function randomHex(bytes) {
  const values = new Uint8Array(bytes)
  crypto.getRandomValues(values)
  return Array.from(values, value => value.toString(16).padStart(2, '0')).join('')
}

export function createTraceContext() {
  const traceId = randomHex(16)
  const spanId = randomHex(8)
  return {
    traceId,
    requestId: traceId,
    traceparent: `00-${traceId}-${spanId}-01`
  }
}

export function traceHeaders(context = createTraceContext()) {
  return {
    traceparent: context.traceparent,
    'X-Request-Id': context.requestId
  }
}

export function withTracePayload(data = {}) {
  const context = createTraceContext()
  return {
    ...data,
    traceparent: context.traceparent,
    requestId: context.requestId
  }
}
