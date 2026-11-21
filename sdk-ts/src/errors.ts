export class OctaPointApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
    message: string,
    public readonly body?: unknown,
  ) {
    super(`HTTP ${status} (${errorCode}): ${message}`);
    this.name = 'OctaPointApiError';
  }
}
